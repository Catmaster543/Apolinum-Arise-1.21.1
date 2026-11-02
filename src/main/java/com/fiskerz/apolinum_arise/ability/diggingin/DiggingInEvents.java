package com.fiskerz.apolinum_arise.ability.diggingin;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Everything the world does to, and is prevented from doing by, a burrowed player.
 *
 * <p>Two jobs: the interaction lockout (all server-side, so a modified client gains nothing), and the
 * forced-exit paths that keep the "never stranded underground" invariant.
 */
public final class DiggingInEvents {
    private DiggingInEvents() {}

    // ---------------------------------------------------------------- per-tick upkeep

    /**
     * Re-applies the burrowed flags and enforces the duration and height limits.
     *
     * <p>{@code PlayerTickEvent.Post} specifically, because {@code Player.tick()} BEGINS with
     * {@code this.noPhysics = this.isSpectator();} - anything running before or during the tick would be
     * overwritten the very next tick.
     */
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        DiggingInManager.onPlayerTickPost(event.getEntity());
    }

    // ---------------------------------------------------------------- interaction lockout

    public static void onAttackEntity(AttackEntityEvent event) {
        cancelIfBurrowed(event.getEntity(), event);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfBurrowed(event.getEntity(), event);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfBurrowed(event.getEntity(), event);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfBurrowed(event.getEntity(), event);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfBurrowed(event.getEntity(), event);
    }

    /**
     * Block breaking. A separate event from the left-click above, and both are needed: the left-click
     * cancels the attempt to start mining, while this one is the authoritative "the block is coming out
     * now" that a packet-level exploit would have to get past.
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (DiggingIn.isBurrowed(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * Block placement, which is NOT covered by RightClickBlock alone - an off-hand placement or a
     * dispenser-like path reaches this instead.
     */
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && DiggingIn.isBurrowed(player)) {
            event.setCanceled(true);
        }
    }

    private static <E extends ICancellableEvent> void cancelIfBurrowed(Player player, E event) {
        if (DiggingIn.isBurrowed(player)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- forced exits

    /**
     * Death. Also the hook that covers the DOWNED state, because Phase 7's downed system intercepts
     * {@code LivingDeathEvent} - this listener runs at a higher priority than
     * {@code DownedEvents.onLivingDeath}, so the eject happens BEFORE the downed state is applied and the
     * player goes down in open space, still reachable and still revivable.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiggingInManager.forceSurface(player, "death or downed entry");
        }
    }

    /** Logout: never persist a live burrow into a session that no longer exists. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiggingInManager.forceSurface(player, "logout");
        }
    }

    /**
     * Login re-validation. The logout eject above should mean this never finds anything, but a crash or a
     * hard kill can write the attachment to disk mid-burrow - and the player would then load inside rock
     * with collision restored. Belt and braces, deliberately.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getData(BurrowAttachments.BURROW).burrowed()) {
            Apolinumarise.LOGGER.warn("[DiggingIn] {} logged in still burrowed - a session must have ended "
                    + "uncleanly. Ejecting.", player.getGameProfile().getName());
            DiggingInManager.forceSurface(player, "stale burrow found at login");
        }
    }

    /** Dimension change: the destination has its own terrain, so the burrow cannot meaningfully continue. */
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiggingInManager.forceSurface(player, "dimension change");
        }
    }

    /**
     * Any other teleport - a command, an ender pearl, a mod. Checked on the tick after arrival rather than
     * on a teleport event, because NeoForge has no single hook every teleport passes through; see
     * {@link #onPlayerTickPost}, which also notices a burrowed player who ended up somewhere solid.
     *
     * <p>Position-based ejects are deliberately NOT done here: a burrowed player is legitimately inside
     * blocks, so "inside a block" is not evidence of a teleport. The duration cap and the explicit hooks
     * above are what bound the state instead.
     */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiggingInManager.forceSurface(player, "arrived in a new dimension");
        }
    }
}
