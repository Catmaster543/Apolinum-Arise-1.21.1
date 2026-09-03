package com.fiskerz.apolinum_arise.downed;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side NeoForge glue for the downed/revive system. Registered from the main mod class.
 */
public final class DownedEvents {
    private DownedEvents() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        DownedManager.onServerTick(event.getServer());
    }

    // Cancelable death hook (LivingDeathEvent). A player's death is cancelled into (or kept in) the downed
    // state for absolute protection; the ONLY death that passes through is the guarded timeout death.
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!com.fiskerz.apolinum_arise.config.Config.DOWNED_ENABLED.get()
                || DownedManager.isAllowingRealDeath(player)) {
            return; // system off, or this is the real timeout death: let it proceed
        }
        event.setCanceled(true);
        if (!DownedManager.tryEnterDowned(player)) {
            // Already downed: stay downed and re-pin health (absolute protection, even from /kill).
            DownedManager.clampAfterDamage(player);
        }
    }

    // After any damage lands, immediately pin a downed player back at the health floor so it never even
    // momentarily rests at a lethal value (covers /kill, explosions, void, everything).
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && DownedManager.isDowned(player)) {
            DownedManager.clampAfterDamage(player);
        }
    }

    // Hostile mobs neither start nor continue targeting an incapacitated player - downed, or passed out
    // from exhaustion (Phase 10a): can't fight back, so shouldn't be freely farmable either.
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target instanceof Player player && DownedManager.isIncapacitated(player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    // ------- Server-side input lock: an incapacitated player cannot attack, break, use, or interact -------
    // Shared by the downed state and the Phase 10a pass-out, which reuses this lock rather than adding one.

    public static void onAttackEntity(AttackEntityEvent event) {
        cancelIfIncapacitated(event.getEntity(), event);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfIncapacitated(event.getEntity(), event);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfIncapacitated(event.getEntity(), event);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfIncapacitated(event.getEntity(), event);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfIncapacitated(event.getEntity(), event);
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (DownedManager.isIncapacitated(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    private static <E extends ICancellableEvent> void cancelIfIncapacitated(Player player, E event) {
        if (DownedManager.isIncapacitated(player)) {
            event.setCanceled(true);
        }
    }
}
