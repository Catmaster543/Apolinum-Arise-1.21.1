package com.fiskerz.apolinum_arise.sleep;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonState;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.downed.DownedAttachments;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Server-authoritative core of the Phase 10a sleep bar and pass-out state. Runs for every player who is
 * NOT fully infected: the bar drains continuously, refills while lying in a bed, and drives an escalating
 * exhaustion penalty that ends in the pass-out incapacitation.
 *
 * <p>The live bar/clock live in a transient {@link Runtime} seeded from the persisted attachment, and are
 * flushed back to it about once a second (and immediately on a pass-out transition). That keeps the
 * per-tick math exact without emitting a sync packet every tick for a bar that moves 0.0014% per tick.
 */
public final class SleepManager {
    private SleepManager() {}

    /**
     * The vanilla sleepable window: a bed can be entered from dayTime 12542 and the skip lands at 23460,
     * so "one full night" of lying is 10918 ticks. The refill rate is calibrated against this.
     */
    public static final long NIGHT_DURATION_TICKS = 10918L;
    /**
     * Percent restored by one full night of continuous lying, BEFORE the config multipliers. At the default
     * 1.5 night multiplier this becomes 50% per night, so two nights in three cover the three-day drain.
     */
    public static final double NIGHT_REFILL_PERCENT = 100.0D / 3.0D;

    private static final int FLUSH_INTERVAL_TICKS = 20;
    // Long enough that the refresh cadence never leaves a gap; the effects are also removed explicitly
    // the moment the bar rises above zero, so the duration itself is not what ends them.
    private static final int EFFECT_DURATION_TICKS = 100;

    private static final Map<UUID, Runtime> RUNTIME = new ConcurrentHashMap<>();

    // Transient live state. The attachment is the persisted mirror, written ~1/s.
    private static final class Runtime {
        float bar;
        int ticksAtZero;
        boolean effectsApplied;
        boolean wasLying;
        boolean registeredForNight;
        float refillThisLie; // for the sleep-skip top-up
    }

    // ------------------------------------------------------------------ tick

    public static void onServerTick(MinecraftServer server) {
        if (!Config.ENABLE_MOD.get()) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, gameTime);
        }
    }

    private static void tickPlayer(ServerPlayer player, long gameTime) {
        SleepData data = player.getData(SleepAttachments.SLEEP);

        // Fully infected players have no sleep bar at all: nothing ticks, nothing drains, no effects. They
        // can still physically lie in a bed (the interaction is never blocked), it just does nothing.
        if (InfectionLogic.isInfected(player)) {
            if (data.passedOut()) {
                // Never leave someone stranded in an un-ticked pass-out after they turn.
                player.setData(SleepAttachments.SLEEP, data.with(data.sleepBar(), 0, false, 0, 0.0F, false));
            }
            clearEffectsIfApplied(player, runtime(player, data));
            return;
        }

        Runtime rt = runtime(player, data);
        boolean lying = player.isSleeping();
        if (lying && !rt.wasLying) {
            rt.refillThisLie = 0.0F; // new lie-down: start the sleep-skip top-up accounting fresh
        }
        rt.wasLying = lying;
        // A nap started during the day is deliberately kept out of the level's sleep-status bookkeeping
        // (see SleepEvents.onCanPlayerSleep). If it runs on into the night, register it now so the player
        // still counts toward the ordinary vanilla night skip instead of silently blocking it.
        if (!lying) {
            rt.registeredForNight = false;
        } else if (!rt.registeredForNight && !player.serverLevel().isDay()) {
            player.serverLevel().updateSleepingPlayerList();
            rt.registeredForNight = true;
        }

        float refill = refillPerTick(player, lying, data.passedOut());
        rt.refillThisLie += refill;
        rt.bar = clamp(rt.bar + refill - drainPerTick());

        // Any refill above zero resets the escalation clock AND clears this system's effects immediately.
        if (rt.bar > 0.0F) {
            rt.ticksAtZero = 0;
            clearEffectsIfApplied(player, rt);
        } else {
            rt.ticksAtZero++;
            applyZeroEffects(player, rt, gameTime);
        }

        boolean passedOut = updatePassOut(player, data, rt);
        flush(player, data, rt, passedOut, gameTime);
    }

    // Enter at the pass-out threshold, leave strictly on the bar reaching the exit threshold (never on time).
    private static boolean updatePassOut(ServerPlayer player, SleepData data, Runtime rt) {
        if (data.passedOut()) {
            if (rt.bar >= (float) (double) Config.SLEEP_PASS_OUT_EXIT_THRESHOLD.get()) {
                Apolinumarise.LOGGER.debug("[Sleep] {} came round (bar {}%).",
                        player.getGameProfile().getName(), String.format("%.1f", rt.bar));
                return false;
            }
            // Freeze the facing every tick, exactly like the downed system, so the body does not spin.
            player.yBodyRot = data.bodyYaw();
            player.yBodyRotO = data.bodyYaw();
            return true;
        }
        // The real downed state owns the player's presentation while it is active; don't stack on it.
        if (rt.ticksAtZero >= passOutTicks() && !player.getData(DownedAttachments.DOWNED).downed()) {
            Apolinumarise.LOGGER.debug("[Sleep] {} passed out from exhaustion.", player.getGameProfile().getName());
            return true;
        }
        return false;
    }

    // Write the live values back to the persisted+synced attachment on the flush interval, or at once when
    // the pass-out flag flips / the pose fields need seeding (so clients react to that on the same tick).
    private static void flush(ServerPlayer player, SleepData data, Runtime rt, boolean passedOut, long gameTime) {
        boolean transition = passedOut != data.passedOut();
        if (!transition && gameTime % FLUSH_INTERVAL_TICKS != 0L) {
            return;
        }
        int variant = data.poseVariant();
        float bodyYaw = data.bodyYaw();
        if (transition && passedOut) {
            // Seed the pose the same way DownedManager does when a player goes down.
            variant = player.getRandom().nextInt(DownedManager.POSE_VARIANTS);
            bodyYaw = player.yBodyRot;
        }
        player.setData(SleepAttachments.SLEEP,
                data.with(rt.bar, rt.ticksAtZero, passedOut, variant, bodyYaw, InfectionLogic.isHealthy(player)));
    }

    // ------------------------------------------------------------------ rates

    /** Percent drained per tick. Never pauses - not while lying, not while passed out. */
    public static float drainPerTick() {
        double days = Math.max(0.0001D, Config.SLEEP_BAR_DRAIN_DAYS.get());
        return (float) (100.0D / (days * Level.TICKS_PER_DAY));
    }

    /** Percent restored per tick by one full night of lying (before the day/Blood-Moon multiplier). */
    public static float nightRefillPerTick() {
        return (float) (NIGHT_REFILL_PERCENT / NIGHT_DURATION_TICKS
                * Config.SLEEP_BAR_NIGHT_REFILL_MULTIPLIER.get());
    }

    private static float refillPerTick(ServerPlayer player, boolean lying, boolean passedOut) {
        // A passed-out player is unconscious on the ground, not choosing to nap: they refill at the base
        // night rate regardless of time of day, which is also what makes the bar-based exit reachable.
        if (passedOut) {
            return nightRefillPerTick();
        }
        if (!lying) {
            return 0.0F;
        }
        boolean halved = !player.level().isNight() || BloodMoonState.isActive(player.server.overworld());
        return halved
                ? nightRefillPerTick() * (float) (double) Config.SLEEP_BAR_DAY_OR_BLOOD_MOON_REFILL_MULTIPLIER.get()
                : nightRefillPerTick();
    }

    /** Continuous ticks at 0% before the pass-out triggers. */
    public static int passOutTicks() {
        return (int) Math.round(Config.SLEEP_ZERO_PASS_OUT_DAYS.get() * Level.TICKS_PER_DAY);
    }

    /** Continuous ticks at 0% before Slowness I is added and Weakness escalates to II. */
    public static int slownessTicks() {
        return (int) Math.round(Config.SLEEP_ZERO_SLOWNESS_DAYS.get() * Level.TICKS_PER_DAY);
    }

    // ------------------------------------------------------------------ sleep-skip credit

    /**
     * Vanilla's sleep-skip jumped the clock to morning, so the ticks the player would have accumulated by
     * lying through the rest of the night never happen. Top every sleeper in that level up to the FULL
     * night-equivalent for this lie-down instead of letting the jump short them.
     *
     * <p>Fired before {@code wakeUpAllPlayers}, so the sleepers are still {@code isSleeping()} here.
     */
    public static void onSleepFinished(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSleeping() || InfectionLogic.isInfected(player)) {
                continue;
            }
            SleepData data = player.getData(SleepAttachments.SLEEP);
            Runtime rt = runtime(player, data);
            float nightEquivalent = nightRefillPerTick() * NIGHT_DURATION_TICKS;
            float credit = nightEquivalent - rt.refillThisLie;
            if (credit <= 0.0F) {
                continue; // already lay long enough to have earned the whole night naturally
            }
            rt.bar = clamp(rt.bar + credit);
            rt.refillThisLie += credit;
            if (rt.bar > 0.0F) {
                rt.ticksAtZero = 0;
                clearEffectsIfApplied(player, rt);
            }
            player.setData(SleepAttachments.SLEEP,
                    data.with(rt.bar, rt.ticksAtZero, data.passedOut(), data.poseVariant(), data.bodyYaw(), InfectionLogic.isHealthy(player)));
            Apolinumarise.LOGGER.debug("[Sleep] Sleep-skip credited {}% to {} (bar now {}%).",
                    String.format("%.2f", credit), player.getGameProfile().getName(), String.format("%.2f", rt.bar));
        }
    }

    // ------------------------------------------------------------------ zero-bar effects

    private static void applyZeroEffects(ServerPlayer player, Runtime rt, long gameTime) {
        boolean escalated = rt.ticksAtZero >= slownessTicks();
        // Apply the instant the bar empties, then refresh on the same cadence the other continuous
        // symptom effects use.
        boolean due = !rt.effectsApplied || gameTime % Config.SYMPTOM_REFRESH_INTERVAL_TICKS.get() == 0L;
        if (!due) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION_TICKS, escalated ? 1 : 0));
        if (escalated) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION_TICKS, 0));
        }
        rt.effectsApplied = true;
    }

    // Only touches the effects on the applied -> not-applied edge, so it never repeatedly strips a Weakness
    // that the incubation symptom timeline applied for its own reasons.
    private static void clearEffectsIfApplied(ServerPlayer player, Runtime rt) {
        if (!rt.effectsApplied) {
            return;
        }
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        rt.effectsApplied = false;
    }

    // ------------------------------------------------------------------ queries / lifecycle

    /** True while this player is passed out from exhaustion (read on both sides - the flag is synced). */
    public static boolean isPassedOut(Player player) {
        return player.getData(SleepAttachments.SLEEP).passedOut();
    }

    /** Persisted bar value; the HUD reads this. */
    public static float sleepBar(Player player) {
        return player.getData(SleepAttachments.SLEEP).sleepBar();
    }

    public static void onLogin(ServerPlayer player) {
        RUNTIME.remove(player.getUUID()); // re-seed from the persisted attachment
    }

    /** Flush the live values so at most the last partial second is lost on the way out. */
    public static void onLogout(ServerPlayer player) {
        Runtime rt = RUNTIME.remove(player.getUUID());
        if (rt != null) {
            SleepData data = player.getData(SleepAttachments.SLEEP);
            player.setData(SleepAttachments.SLEEP,
                    data.with(rt.bar, rt.ticksAtZero, data.passedOut(), data.poseVariant(), data.bodyYaw(), InfectionLogic.isHealthy(player)));
        }
    }

    /** Admin/test entry point: set the bar directly and reconcile the escalation clock. */
    public static void setBar(ServerPlayer player, float percent) {
        SleepData data = player.getData(SleepAttachments.SLEEP);
        Runtime rt = runtime(player, data);
        rt.bar = clamp(percent);
        if (rt.bar > 0.0F) {
            rt.ticksAtZero = 0;
            clearEffectsIfApplied(player, rt);
        }
        player.setData(SleepAttachments.SLEEP,
                data.with(rt.bar, rt.ticksAtZero, data.passedOut(), data.poseVariant(), data.bodyYaw(), InfectionLogic.isHealthy(player)));
    }

    private static Runtime runtime(ServerPlayer player, SleepData data) {
        return RUNTIME.computeIfAbsent(player.getUUID(), uuid -> {
            Runtime rt = new Runtime();
            rt.bar = data.sleepBar();
            rt.ticksAtZero = data.ticksAtZero();
            return rt;
        });
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(100.0F, value));
    }
}
