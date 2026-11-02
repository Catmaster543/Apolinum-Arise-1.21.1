package com.fiskerz.apolinum_arise.ability.diggingin;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Digging In state machine. Server-authoritative in full: the client reports a key press and renders
 * the synced result, and decides nothing.
 *
 * <h2>Transition locking</h2>
 * Every transition, in either direction, sets {@code nextAllowedGameTime = now + cooldown}. Because an exit
 * is also a transition, that single rule produces the intended shape: a burrow cannot be undone for the
 * cooldown, so {@code diggingInTransitionCooldownSeconds} IS the minimum time underground, and a surfacing
 * cannot be immediately re-burrowed either.
 *
 * <h2>Forced exits</h2>
 * Death, entering the downed state, logout, login re-validation, dimension change and teleports all ignore
 * the lock completely and eject via {@link SafePositionFinder}. The invariant they protect is simple and
 * absolute: <b>a player is never left BURROWED without a live session driving them</b>, because collision
 * is restored on load and they would be standing inside rock.
 */
public final class DiggingInManager {
    private DiggingInManager() {}

    private static final int TICKS_PER_SECOND = 20;

    private static final String REFUSED_COOLDOWN_KEY = "message.apolinumarise.diggingin.cooldown";
    private static final String REFUSED_BLOCKED_KEY = "message.apolinumarise.diggingin.blocked";
    private static final String BURROWED_KEY = "message.apolinumarise.diggingin.burrowed";
    private static final String SURFACED_KEY = "message.apolinumarise.diggingin.surfaced";
    private static final String EJECTED_KEY = "message.apolinumarise.diggingin.ejected";

    /** What a key press should do. Pure, so the rules can be tested without a networked player. */
    public enum Decision {
        /** Go underground. */
        BURROW,
        /** Come up here. */
        SURFACE,
        /** Too soon since the last transition in either direction. */
        REFUSED_COOLDOWN,
        /** Burrowed, allowed to leave, but there is solid rock where they are standing. */
        REFUSED_BLOCKED
    }

    /**
     * The whole transition rule set, as a function of state rather than of a player.
     *
     * <p>Order matters and is deliberate: the cooldown is checked FIRST, so a player who is both locked
     * and buried is told about the lock rather than being sent to dig for open space they could not use
     * yet anyway.
     */
    public static Decision decide(BurrowData data, long now, boolean standingSpaceClear) {
        if (!data.mayTransitionAt(now)) {
            return Decision.REFUSED_COOLDOWN;
        }
        if (!data.burrowed()) {
            return Decision.BURROW;
        }
        return standingSpaceClear ? Decision.SURFACE : Decision.REFUSED_BLOCKED;
    }

    /** The keybind hook registered with the ability. One press toggles, subject to {@link #decide}. */
    public static void onKeyPress(ServerPlayer player) {
        BurrowData data = player.getData(BurrowAttachments.BURROW);
        long now = player.level().getGameTime();

        switch (decide(data, now, isStandingSpaceClear(player))) {
            case REFUSED_COOLDOWN -> player.displayClientMessage(
                    Component.translatable(REFUSED_COOLDOWN_KEY, data.secondsRemainingAt(now)), true);
            case REFUSED_BLOCKED -> player.displayClientMessage(
                    Component.translatable(REFUSED_BLOCKED_KEY), true);
            case BURROW -> burrow(player, now);
            case SURFACE -> surface(player, data, now);
        }
    }

    // ---------------------------------------------------------------- transitions

    private static void burrow(ServerPlayer player, long now) {
        player.setData(BurrowAttachments.BURROW,
                player.getData(BurrowAttachments.BURROW).burrowedAt(now, now + cooldownTicks()));
        applyBurrowedState(player);
        player.displayClientMessage(Component.translatable(BURROWED_KEY), true);
        Apolinumarise.LOGGER.debug("[DiggingIn] {} burrowed at {}.", player.getGameProfile().getName(), now);
    }

    /**
     * Manual exit, already cleared by {@link #decide} - which refuses while the player's own bounding box
     * overlaps blocks, because surfacing there would restore collision inside rock. That refusal is what
     * makes a player swim to open space to get out rather than popping up anywhere.
     */
    private static void surface(ServerPlayer player, BurrowData data, long now) {
        clearBurrowedState(player);
        player.setData(BurrowAttachments.BURROW, data.surfaced(now + cooldownTicks()));
        player.displayClientMessage(Component.translatable(SURFACED_KEY), true);
        Apolinumarise.LOGGER.debug("[DiggingIn] {} surfaced at {}.", player.getGameProfile().getName(), now);
    }

    /**
     * Exit regardless of the lock, the cooldown and where they are, relocating to safe ground first.
     * Every forced-exit path funnels through here, which is what makes the "never stranded" invariant one
     * piece of code rather than six.
     */
    public static void forceSurface(ServerPlayer player, String reason) {
        BurrowData data = player.getData(BurrowAttachments.BURROW);
        if (!data.burrowed()) {
            return;
        }
        Vec3 safe = SafePositionFinder.findEjectPosition(player);
        clearBurrowedState(player);
        player.setData(BurrowAttachments.BURROW,
                data.surfaced(player.level().getGameTime() + cooldownTicks()));
        player.teleportTo(safe.x, safe.y, safe.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.displayClientMessage(Component.translatable(EJECTED_KEY), true);
        Apolinumarise.LOGGER.info("[DiggingIn] Force-surfaced {} ({}) to {}.",
                player.getGameProfile().getName(), reason, safe);
    }

    // ---------------------------------------------------------------- per-tick upkeep

    /**
     * Re-apply the burrowed flags and enforce the limits, once per player per tick.
     *
     * <p><b>Must run AFTER {@code Player.tick()}</b>, which opens with
     * {@code this.noPhysics = this.isSpectator();} and would otherwise wipe the flag every tick. NeoForge's
     * {@code PlayerTickEvent.Post} is exactly that point.
     */
    public static void onPlayerTickPost(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BurrowData data = serverPlayer.getData(BurrowAttachments.BURROW);
        if (!data.burrowed()) {
            return;
        }
        // The ability stopped applying mid-burrow - a debug revoke, a cure, or an admin variant switch away
        // from variant 2. Whatever the cause, do not strand them underground.
        if (!DiggingIn.isActiveFor(serverPlayer)) {
            forceSurface(serverPlayer, "ability no longer active (revoked, cured or variant switched)");
            return;
        }

        applyBurrowedState(serverPlayer);
        clampInsideWorld(serverPlayer);

        long now = serverPlayer.level().getGameTime();
        if (data.burrowedTicksAt(now) >= maxDurationTicks()) {
            forceSurface(serverPlayer, "max burrow duration reached");
        }
    }

    /**
     * Keep a burrowed player inside the dimension's build limits, so they can never swim out of the world
     * or down into the void. Enforced server-side as the authority; the client clamps too, so in practice
     * this never has to correct anything.
     */
    private static void clampInsideWorld(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double min = level.getMinBuildHeight() + 1.0D;
        double max = level.getMaxBuildHeight() - 2.0D;
        double clamped = Mth.clamp(player.getY(), min, max);
        if (clamped != player.getY()) {
            player.teleportTo(player.getX(), clamped, player.getZ());
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            player.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- the burrowed flags

    /**
     * The three things being burrowed actually consists of, applied on the server.
     *
     * <ul>
     *   <li>{@code noPhysics = true} - the entity collision-disable flag. It is also what makes the server
     *       accept the client's position without correction: {@code ServerGamePacketListenerImpl}'s
     *       moved-wrongly branch short-circuits on {@code player.noPhysics} before it can rubber-band.</li>
     *   <li>{@code setNoGravity(true)} - and this is what defeats the floating kick, not a config toggle:
     *       {@code getMaximumFlyingTicks} returns {@code Integer.MAX_VALUE} when
     *       {@code entity.getGravity() < 1.0E-5}, and {@code getGravity()} returns 0 for a no-gravity
     *       entity. The 80-tick counter therefore has nothing to trip.</li>
     *   <li>{@code setForcedPose(SWIMMING)} - NeoForge's hook inside {@code Player.updatePlayerPose},
     *       which skips vanilla's pose choice and its clearance check entirely. Its javadoc says to set it
     *       once rather than every tick, so it is only assigned when not already the swimming pose.</li>
     * </ul>
     */
    public static void applyBurrowedState(Player player) {
        player.noPhysics = true;
        if (!player.isNoGravity()) {
            player.setNoGravity(true);
        }
        if (player.getForcedPose() != Pose.SWIMMING) {
            player.setForcedPose(Pose.SWIMMING);
        }
    }

    /** Hand every one of those back to vanilla. */
    public static void clearBurrowedState(Player player) {
        player.noPhysics = player.isSpectator();
        player.setNoGravity(false);
        player.setForcedPose(null);
    }

    /**
     * One tick of upkeep for a side's OWN copy of the burrowed flags, given whether this side had applied
     * them as of last tick. Returns whether they are applied after this call; the caller keeps that for the
     * next tick.
     *
     * <p>This exists for the client. {@code Player.forcedPose} is a plain field with no entity-data sync, so
     * the server clearing its own copy never reaches the client - the client put the pose on its player
     * itself and has to take it off itself. The hand-back keys on the TRANSITION (applied last tick, not
     * burrowed now) and deliberately not on any of the flags: {@code Player.tick()} opens with
     * {@code noPhysics = isSpectator()}, so a check like "not burrowed but still noPhysics" reads false
     * every tick and never fires. That was the bug that left surfaced players stuck in the swimming pose.
     *
     * <p>Only ever clears what it applied. A player this side never burrowed is left alone, so another
     * mod's forced pose is not stomped on.
     */
    public static boolean upkeepOwnFlags(Player player, boolean appliedLastTick) {
        if (DiggingIn.isBurrowed(player)) {
            applyBurrowedState(player);
            return true;
        }
        if (appliedLastTick) {
            clearBurrowedState(player);
        }
        return false;
    }

    // ---------------------------------------------------------------- queries

    /** Whether the player's full standing box is clear of blocks where they are - the manual-exit gate. */
    public static boolean isStandingSpaceClear(Player player) {
        return player.level().noCollision(null,
                player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7));
    }

    public static int cooldownTicks() {
        return Math.max(0, (int) Math.round(Config.DIGGING_IN_TRANSITION_COOLDOWN_SECONDS.get() * TICKS_PER_SECOND));
    }

    /**
     * Max burrow length, clamped to at least the transition cooldown.
     *
     * <p>A max duration shorter than the cooldown would auto-eject a player who is not yet allowed to
     * surface, every single tick after the limit - so the clamp is a correctness fix, not politeness, and
     * it says so in the log rather than silently papering over the config.
     */
    public static int maxDurationTicks() {
        int configured = (int) Math.round(Config.DIGGING_IN_MAX_DURATION_SECONDS.get() * TICKS_PER_SECOND);
        int cooldown = cooldownTicks();
        if (configured < cooldown) {
            Apolinumarise.LOGGER.warn("[DiggingIn] diggingInMaxDurationSeconds ({}s) is shorter than "
                            + "diggingInTransitionCooldownSeconds ({}s), which would auto-eject a player who "
                            + "is not yet allowed to surface. Clamping the maximum to the cooldown.",
                    Config.DIGGING_IN_MAX_DURATION_SECONDS.get(),
                    Config.DIGGING_IN_TRANSITION_COOLDOWN_SECONDS.get());
            return cooldown;
        }
        return configured;
    }

    /** Blocks per tick at the configured speed. */
    public static double speedPerTick() {
        return Config.DIGGING_IN_SPEED_BLOCKS_PER_SECOND.get() / TICKS_PER_SECOND;
    }
}
