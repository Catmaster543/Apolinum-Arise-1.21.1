package com.fiskerz.apolinum_arise.infection.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot kept in the same position for layout parity but fully non-interactive. Rejecting both place
 * and pickup is enforced by the menu (server-side), so a disabled slot can't be bypassed by any client.
 * The screen draws a dark overlay over these.
 */
public class InertSlot extends Slot {
    public InertSlot(Container container, int slotIndex, int x, int y) {
        super(container, slotIndex, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
