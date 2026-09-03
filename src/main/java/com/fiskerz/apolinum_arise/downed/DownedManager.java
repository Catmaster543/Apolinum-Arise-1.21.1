package com.fiskerz.apolinum_arise.downed;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.sleep.SleepAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative core of the downed/revive system (Phase 7). A player who would die instead enters
 * a timed "downed" state: pinned above death, input-locked (client-side), untargetable, until either a
 * revive or the timeout, at which point the real vanilla death is allowed through.
 */
public final class DownedManager {
    private DownedManager() {}

    public static final int POSE_VARIANTS = 3;

    // Active hold-to-revive channels, keyed by reviver UUID (server-authoritative).
    private record ReviveChannel(int targetId, int progressTicks, long lastTick) {}
    private static final Map<UUID, ReviveChannel> CHANNELS = new ConcurrentHashMap<>();

    // Players whose real (timeout) death is being processed right now: their kill() must NOT be
    // re-intercepted back into downed, or the timeout would loop forever.
    private static final java.util.Set<UUID> ALLOWING_REAL_DEATH = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Saved [mayFly, flying] to restore on exit. While downed we force flight so the player freezes in
    // place (they "never moved while downed") AND the server's floating-too-long kick is not triggered
    // when a downed player is airborne (e.g. launched by an explosion).
    private static final Map<UUID, boolean[]> SAVED_ABILITIES = new ConcurrentHashMap<>();

    public static boolean isDowned(Player player) {
        return player.getData(DownedAttachments.DOWNED).downed();
    }

    /**
     * True when the player is incapacitated by EITHER the real downed state or the Phase 10a exhaustion
     * pass-out. This is the predicate the shared PRESENTATION uses - camera lock, HUD hiding, input lock,
     * pose rendering, hostile de-aggro - so pass-out reuses all of it without duplicating any of it.
     *
     * <p>Deliberately NOT used by the downed MECHANICS: death interception, the health floor, the revive
     * timer and revive/bite targeting all stay on {@link #isDowned} alone, because a passed-out player is
     * fully vulnerable, has no timer, and cannot be interacted with by other players in either direction.
     * Reads the attachments directly so the two systems stay decoupled.
     */
    public static boolean isIncapacitated(Player player) {
        return isDowned(player) || player.getData(SleepAttachments.SLEEP).passedOut();
    }

    /** Revive eligibility (both directions): healthy or incubating - i.e. NOT fully infected. */
    public static boolean isEligible(Player player) {
        return !InfectionLogic.isInfected(player);
    }

    /**
     * Death-interception entry point. Returns true if the death was consumed (player entered downed),
     * false if it should proceed (system disabled, or the player is already downed).
     */
    public static boolean tryEnterDowned(Player player) {
        if (!Config.DOWNED_ENABLED.get() || isDowned(player) || ALLOWING_REAL_DEATH.contains(player.getUUID())) {
            return false;
        }
        enterDowned(player);
        return true;
    }

    /** True while this player's guarded timeout death is being processed (so it must not be re-intercepted). */
    public static boolean isAllowingRealDeath(Player player) {
        return ALLOWING_REAL_DEATH.contains(player.getUUID());
    }

    private static void enterDowned(Player player) {
        float floor = healthFloor();
        player.setHealth(floor);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        freezeInPlace(player);

        FoodData food = player.getFoodData();
        int savedFood = food.getFoodLevel();
        float savedSaturation = food.getSaturationLevel();
        int variant = player.getRandom().nextInt(POSE_VARIANTS);
        long gameTime = player.level().getGameTime();
        int durationTicks = Config.DOWNED_DURATION_SECONDS.get() * 20;
        // Freeze the body facing the instant they go down so the corpse never tracks the look direction.
        float bodyYaw = player.yBodyRot;
        // revivable = not fully infected (revive eligibility); healthy = clean (Phase 8 bite eligibility).
        // Both captured now and broadcast, since a reviver/biter's client can't see the target's infection.
        boolean healthy = InfectionLogic.isHealthy(player);

        player.setData(DownedAttachments.DOWNED,
                new DownedData(true, isEligible(player), healthy, variant, durationTicks, gameTime, bodyYaw, savedFood, savedSaturation));

        // Same requirement as the infection transition: drop any current lock right now, not just going forward.
        clearCurrentAttackers(player);
        Apolinumarise.LOGGER.debug("Downed: {} entered downed state (pose variant {}).",
                player.getGameProfile().getName(), variant);
    }

    public static void onServerTick(MinecraftServer server) {
        tickReviveChannels(server.overworld().getGameTime());
        float floor = healthFloor();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DownedData data = player.getData(DownedAttachments.DOWNED);
            if (!data.downed()) {
                continue;
            }
            // Absolute protection: pin health at exactly the floor every tick (clamps damage back up and
            // stops natural regen from a full-hunger state drifting it, keeping the downed state stable).
            if (player.getHealth() != floor) {
                player.setHealth(floor);
            }
            // Freeze hunger exactly where it was on going down (not drained, not refilled).
            FoodData food = player.getFoodData();
            if (food.getFoodLevel() != data.savedFood()) {
                food.setFoodLevel(data.savedFood());
            }
            food.setSaturation(data.savedSaturation());
            food.setExhaustion(0.0F);

            // Stay frozen in place (they never move while downed) and keep flight asserted so the
            // floating-too-long kick can't fire if they were knocked airborne.
            player.getAbilities().flying = true;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            // Freeze the authoritative body facing so it never tracks the look direction (Patch B6).
            player.yBodyRot = data.bodyYaw();
            player.yBodyRotO = data.bodyYaw();

            if (player.level().getGameTime() - data.enteredGameTime() >= data.durationTicks()) {
                realDeath(player);
            }
        }
    }

    /** Immediate clamp after any damage event, so health never even momentarily rests at a lethal value. */
    public static void clampAfterDamage(Player player) {
        float floor = healthFloor();
        if (player.getHealth() < floor) {
            player.setHealth(floor);
        }
    }

    /** Successful revive: stays in place, one heart, hunger preserved exactly, downed state cleared. */
    public static void revive(Player target) {
        DownedData data = target.getData(DownedAttachments.DOWNED);
        if (!data.downed()) {
            return;
        }
        target.setData(DownedAttachments.DOWNED, DownedData.NONE);
        restoreAbilities(target);
        target.setHealth((float) (double) Config.REVIVE_HEALTH_ON_REVIVE.get());
        FoodData food = target.getFoodData();
        food.setFoodLevel(data.savedFood());
        food.setSaturation(data.savedSaturation());
        Apolinumarise.LOGGER.debug("Downed: {} revived.", target.getGameProfile().getName());
    }

    // Timeout: leave downed FIRST, then let the real vanilla death sequence run - You Died screen, normal
    // drops, normal respawn. The kill() is guarded so onLivingDeath does not re-intercept it into downed.
    private static void realDeath(ServerPlayer player) {
        player.setData(DownedAttachments.DOWNED, DownedData.NONE);
        restoreAbilities(player);
        ALLOWING_REAL_DEATH.add(player.getUUID());
        try {
            player.kill();
        } finally {
            ALLOWING_REAL_DEATH.remove(player.getUUID());
        }
        Apolinumarise.LOGGER.debug("Downed: {} timed out -> real death.", player.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ hold-to-revive channel

    /**
     * One tick of a reviver's hold, driven by a client packet sent every tick while it is looking at a
     * downed target and holding the key. A gap (release/line-of-sight loss/range break -> the client
     * stops sending, or sends targetId -1) resets progress to zero: no partial credit.
     */
    public static void handleReviveInput(ServerPlayer reviver, int targetId, long gameTime) {
        if (targetId < 0 || !Config.DOWNED_ENABLED.get() || !isEligible(reviver)) {
            CHANNELS.remove(reviver.getUUID());
            return;
        }
        if (!(reviver.level().getEntity(targetId) instanceof ServerPlayer target)
                || target == reviver || !isDowned(target) || !isEligible(target)
                || !withinReviveReach(reviver, target)) {
            CHANNELS.remove(reviver.getUUID());
            return;
        }
        ReviveChannel channel = CHANNELS.get(reviver.getUUID());
        int progress = (channel != null && channel.targetId() == targetId && gameTime - channel.lastTick() <= 3)
                ? channel.progressTicks() + 1 : 1;
        if (progress == 1) {
            Apolinumarise.LOGGER.debug("Revive: server accepted hold from {} on target id {} (channel started).",
                    reviver.getGameProfile().getName(), targetId);
        }
        int holdTicks = Math.max(1, (int) Math.ceil(Config.REVIVE_HOLD_SECONDS.get() * 20.0D));
        if (progress >= holdTicks) {
            CHANNELS.remove(reviver.getUUID());
            revive(target);
        } else {
            CHANNELS.put(reviver.getUUID(), new ReviveChannel(targetId, progress, gameTime));
        }
    }

    // Drop channels that stopped receiving ticks (client released/disconnected without a final -1).
    private static void tickReviveChannels(long gameTime) {
        CHANNELS.entrySet().removeIf(entry -> gameTime - entry.getValue().lastTick() > 3);
    }

    /** Server-side sanity check mirroring the client's crosshair pick: within range and roughly faced. */
    public static boolean withinReviveReach(ServerPlayer reviver, ServerPlayer target) {
        double range = Config.REVIVE_RANGE.get();
        Vec3 eye = reviver.getEyePosition();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
        if (toTarget.length() > range) {
            return false;
        }
        return reviver.getLookAngle().dot(toTarget.normalize()) >= 0.8D;
    }

    private static void freezeInPlace(Player player) {
        SAVED_ABILITIES.put(player.getUUID(), new boolean[]{player.getAbilities().mayfly, player.getAbilities().flying});
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private static void restoreAbilities(Player player) {
        boolean[] saved = SAVED_ABILITIES.remove(player.getUUID());
        player.getAbilities().mayfly = saved != null && saved[0];
        player.getAbilities().flying = saved != null && saved[1];
        player.onUpdateAbilities();
    }

    static void clearCurrentAttackers(Player player) {
        List<Mob> attackers = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0D),
                mob -> mob instanceof Enemy && mob.getTarget() == player);
        for (Mob mob : attackers) {
            mob.setTarget(null);
        }
    }

    private static float healthFloor() {
        return (float) (double) Config.DOWNED_HEALTH_FLOOR.get();
    }
}
