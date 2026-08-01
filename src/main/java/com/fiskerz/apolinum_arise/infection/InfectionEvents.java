package com.fiskerz.apolinum_arise.infection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge event glue for the Phase 5 symptom timeline. Registered from the main mod class as its own
 * listeners (separate from Phase 3's BloodMoonEvents), so those files stay untouched.
 */
public final class InfectionEvents {
    private InfectionEvents() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        InfectionSymptoms.onServerTick(event.getServer());
    }

    // A newly-tracking viewer needs the tracked player's current moles pushed to it.
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer) {
            InfectionSymptoms.onStartTracking(viewer, event.getTarget());
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InfectionSymptoms.onLogin(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InfectionSymptoms.onLogout(player);
        }
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        InfectionCommands.register(event.getDispatcher());
    }

    // Day-9 sunset onward: hostile mobs never acquire this player as a target. Redirecting the new
    // target to null (rather than cancelling) also avoids preserving a stale earlier lock on them.
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target instanceof Player player && InfectionSymptoms.enemiesIgnore(player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    // Patch A1: right-clicking a helmet/boots equips it via ArmorItem.use -> Equipable.swapWithEquipmentSlot,
    // a path entirely outside the restricted menu. Cancel the interaction that triggers it for infected
    // players (any gamemode, no screen needed). Chestplate/leggings still equip normally.
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!InfectionLogic.isInfected(player)) {
            return;
        }
        EquipmentSlot slot = player.getEquipmentSlotForItem(event.getItemStack());
        if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.FEET) {
            event.setCanceled(true);
        }
    }

    // Patch A3: automatic pickup writes straight into the inventory, bypassing the menu's slot blocking.
    // For infected players, insert only into the slots the restricted menu keeps usable (hotbar + the main
    // row closest to the hotbar) and deny the default pickup, so the two disabled main rows (inventory
    // indices 9-26) are treated as unavailable during auto-pickup too. Anything that doesn't fit stays on
    // the ground rather than being forced into a disabled slot.
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide() || !InfectionLogic.isInfected(player)) {
            return;
        }
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity.hasPickUpDelay()) {
            return; // respect vanilla pickup delay
        }
        ItemStack stack = itemEntity.getItem();
        int before = stack.getCount();
        insertIntoUsableSlots(player.getInventory(), stack);
        int taken = before - stack.getCount();
        if (taken > 0) {
            player.take(itemEntity, taken); // pickup animation / sound
            if (stack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(stack);
            }
        }
        // Prevent the default pickup regardless: it would fill the disabled rows with the remainder.
        event.setCanPickup(TriState.FALSE);
    }

    // Usable auto-pickup destinations = hotbar (0-8) + the main row closest to the hotbar (27-35); the two
    // upper main rows (9-26) are the disabled ones and are deliberately excluded.
    private static final int[] USABLE_PICKUP_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    // Mirrors vanilla merge order: top up matching stacks first, then fill empty slots.
    private static void insertIntoUsableSlots(Inventory inventory, ItemStack stack) {
        for (int index : USABLE_PICKUP_SLOTS) {
            if (stack.isEmpty()) {
                return;
            }
            ItemStack existing = inventory.getItem(index);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int room = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize()) - existing.getCount();
                if (room > 0) {
                    int moved = Math.min(room, stack.getCount());
                    existing.grow(moved);
                    stack.shrink(moved);
                    inventory.setItem(index, existing);
                }
            }
        }
        for (int index : USABLE_PICKUP_SLOTS) {
            if (stack.isEmpty()) {
                return;
            }
            if (inventory.getItem(index).isEmpty()) {
                int moved = Math.min(stack.getMaxStackSize(), stack.getCount());
                inventory.setItem(index, stack.copyWithCount(moved));
                stack.shrink(moved);
            }
        }
    }
}
