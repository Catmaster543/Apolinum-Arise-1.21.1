package com.fiskerz.apolinum_arise.ability.strongpunch;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative charge tracking for {@link StrongPunch}.
 *
 * <h2>What the client is trusted with, and what it is not</h2>
 * Exactly one thing: whether the attack key is physically down, reported by
 * {@code AttackHeldPayload}, because the server genuinely cannot know it - vanilla's
 * {@code ServerboundPlayerInputPacket} carries movement only, and swings arrive as discrete events.
 * Everything else is recomputed here every tick from server state: what the player is looking at (a server
 * raycast along their own view vector, not a client-supplied entity id), whether it is in melee range,
 * whether the hand is empty, how many ticks have elapsed, and whether the cooldown has expired. There is
 * no "charge complete" message a client could send, and holding the key is something a player can do
 * legitimately anyway - so the worst a forged packet achieves is what a finger on the button achieves.
 *
 * <h2>No partial credit</h2>
 * The charge counter resets to zero the instant any condition lapses - target change, key release, range
 * loss, a non-empty hand, the target dying - matching the Phase 7 revive channel. Normal attacks landing
 * on the same target are not a condition at all and so cannot interrupt: this loop never looks at attacks,
 * only at where the player is looking and what they are holding.
 */
public final class StrongPunchManager {
    private StrongPunchManager() {}

    private static final int TICKS_PER_SECOND = 20;
    /**
     * How stale a hold report may be before the key counts as released. The client sends one every tick
     * while down, so two ticks of slack absorbs ordinary jitter while still releasing promptly if the
     * player disconnects or packets stop - the server must never be stuck believing a key is held.
     */
    private static final int HOLD_REPORT_GRACE_TICKS = 2;

    private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();

    private static final class Charge {
        /** Server tick at which the client last reported the attack key down. */
        long lastHeldTick = Long.MIN_VALUE;
        /** The entity this charge is committed to; a different one restarts from zero. */
        int targetId = -1;
        int chargeTicks;
        int cooldownTicks;
    }

    private static Charge charge(Player player) {
        return CHARGES.computeIfAbsent(player.getUUID(), id -> new Charge());
    }

    /** C2S entry point: record the raw key state. Nothing is decided here. */
    public static void onAttackHeldReported(ServerPlayer player, boolean held) {
        Charge charge = charge(player);
        if (held) {
            charge.lastHeldTick = player.level().getGameTime();
        } else {
            // An explicit release is acted on at once rather than waiting for the grace window.
            charge.lastHeldTick = Long.MIN_VALUE;
        }
    }

    public static void onLogout(ServerPlayer player) {
        CHARGES.remove(player.getUUID());
    }

    /** Transient state only - safe to drop on world unload. */
    public static void clearRuntime() {
        CHARGES.clear();
    }

    public static void onServerTick(MinecraftServer server) {
        if (!Config.ENABLE_MOD.get()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StrongPunch.isUnlockedFor(player)) {
                tickPlayer(player);
            } else if (CHARGES.containsKey(player.getUUID())) {
                CHARGES.remove(player.getUUID()); // lost the ability - forget everything
            }
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        Charge charge = charge(player);

        if (charge.cooldownTicks > 0) {
            charge.cooldownTicks--;
            charge.chargeTicks = 0;
            charge.targetId = -1;
            return;
        }

        LivingEntity target = currentTarget(player, charge);
        if (target == null) {
            charge.chargeTicks = 0;
            charge.targetId = -1;
            return;
        }

        if (target.getId() != charge.targetId) {
            // Switching victims starts over - no partial credit carried across targets.
            charge.targetId = target.getId();
            charge.chargeTicks = 0;
            return;
        }

        charge.chargeTicks++;
        if (charge.chargeTicks >= chargeTicks()) {
            StrongPunchEffect.detonate(player, target);
            charge.chargeTicks = 0;
            charge.targetId = -1;
            charge.cooldownTicks = cooldownTicks();
            Apolinumarise.LOGGER.debug("[StrongPunch] {} landed a strong punch; {} tick cooldown.",
                    player.getGameProfile().getName(), charge.cooldownTicks);
        }
    }

    /**
     * The entity this player is currently charging on, or null if any precondition fails.
     *
     * <p>Package-visible and taking the charge state explicitly so a gametest can drive the exact decision
     * without a networked player.
     */
    static LivingEntity currentTarget(ServerPlayer player, Charge charge) {
        if (!isHeld(player, charge)) {
            return null;
        }
        if (!player.getMainHandItem().isEmpty()) {
            return null; // bare fists only
        }
        return raycastLivingTarget(player);
    }

    private static boolean isHeld(Player player, Charge charge) {
        long now = player.level().getGameTime();
        return charge.lastHeldTick != Long.MIN_VALUE && now - charge.lastHeldTick <= HOLD_REPORT_GRACE_TICKS;
    }

    /**
     * Server-side raycast along the player's own view vector, limited to their melee reach
     * ({@code ENTITY_INTERACTION_RANGE}, 3.0 by default). Deliberately not a client-reported entity id:
     * this is the part a cheating client would most want to control, and the server already has
     * everything it needs to work it out.
     */
    public static LivingEntity raycastLivingTarget(Player player) {
        double reach = player.entityInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        AABB search = player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(reach)).inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
                candidate -> candidate != player && candidate.isAlive() && candidate.isPickable()
                        && candidate instanceof LivingEntity,
                reach * reach);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity living)) {
            return null;
        }
        // expandTowards/inflate makes the search box generous; this is the real range gate.
        return player.distanceToSqr(living) <= reach * reach ? living : null;
    }

    public static int chargeTicks() {
        return Math.max(1, (int) Math.ceil(Config.STRONG_PUNCH_CHARGE_SECONDS.get() * TICKS_PER_SECOND));
    }

    public static int cooldownTicks() {
        return Math.max(0, (int) Math.round(Config.STRONG_PUNCH_COOLDOWN_SECONDS.get() * TICKS_PER_SECOND));
    }

    // ---------------------------------------------------------------- test/debug accessors

    /** Ticks of charge accumulated for this player right now. */
    public static int chargeTicksOf(Player player) {
        Charge charge = CHARGES.get(player.getUUID());
        return charge == null ? 0 : charge.chargeTicks;
    }

    /** Remaining cooldown ticks, or 0. */
    public static int cooldownTicksOf(Player player) {
        Charge charge = CHARGES.get(player.getUUID());
        return charge == null ? 0 : charge.cooldownTicks;
    }

    /** The entity id this player's charge is committed to, or -1. */
    public static int targetIdOf(Player player) {
        Charge charge = CHARGES.get(player.getUUID());
        return charge == null ? -1 : charge.targetId;
    }

    /**
     * Drive one tick of the charge state machine against an explicitly supplied target, so a gametest can
     * exercise the accumulate / reset / fire / cooldown logic without a networked client or a raycast.
     * The real per-tick path resolves its own target and then runs exactly this.
     */
    public static void tickWithResolvedTarget(Player player, LivingEntity target, java.util.function.
            BiConsumer<Player, LivingEntity> onComplete) {
        Charge charge = charge(player);
        if (charge.cooldownTicks > 0) {
            charge.cooldownTicks--;
            charge.chargeTicks = 0;
            charge.targetId = -1;
            return;
        }
        if (target == null) {
            charge.chargeTicks = 0;
            charge.targetId = -1;
            return;
        }
        if (target.getId() != charge.targetId) {
            charge.targetId = target.getId();
            charge.chargeTicks = 0;
            return;
        }
        charge.chargeTicks++;
        if (charge.chargeTicks >= chargeTicks()) {
            onComplete.accept(player, target);
            charge.chargeTicks = 0;
            charge.targetId = -1;
            charge.cooldownTicks = cooldownTicks();
        }
    }

    /** Test helper: pretend the client reported a hold this tick. */
    public static void reportHeldForTest(Player player, boolean held) {
        Charge charge = charge(player);
        charge.lastHeldTick = held ? player.level().getGameTime() : Long.MIN_VALUE;
    }

    /** Test helper: is the hold currently considered live for this player? */
    public static boolean isHeldForTest(Player player) {
        return isHeld(player, charge(player));
    }

    /** Test helper: forget a player's charge entirely. */
    public static void forget(Entity player) {
        CHARGES.remove(player.getUUID());
    }
}
