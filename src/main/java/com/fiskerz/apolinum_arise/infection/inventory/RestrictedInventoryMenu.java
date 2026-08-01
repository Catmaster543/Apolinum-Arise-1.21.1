package com.fiskerz.apolinum_arise.infection.inventory;

import com.fiskerz.apolinum_arise.infection.InfectionMenus;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The infected player's restricted inventory. It is backed by the player's REAL {@link Inventory} (not a
 * copy), laid out identically to the vanilla inventory so the vanilla texture lines up, but with most of
 * it inert: only the hotbar, the main-inventory row closest to the hotbar, the offhand, and the
 * chestplate/leggings armor slots are functional. The 2 upper main rows, the 2x2 crafting grid + result,
 * and the helmet/boots armor slots are {@link InertSlot}s that reject place/take at the menu level, so
 * the restriction holds server-side and can't be bypassed.
 */
public class RestrictedInventoryMenu extends AbstractContainerMenu {
    // Player Inventory indices: armor is 36(boots)..39(helmet); offhand is 40.
    private static final int ARMOR_BOOTS = 36;
    private static final int ARMOR_LEGS = 37;
    private static final int ARMOR_CHEST = 38;
    private static final int ARMOR_HELMET = 39;
    private static final int OFFHAND = 40;

    // Menu-slot ranges used by quickMoveStack (order below must match addSlot order).
    private static final int USABLE_MAIN_ROW_START = 27; // inv 27-35 (closest to hotbar)
    private static final int USABLE_MAIN_ROW_END = 36;
    private static final int HOTBAR_START = 36;          // inv 0-8
    private static final int HOTBAR_END = 45;

    // Inert crafting result + 2x2 grid never hold items, so a throwaway container backs them.
    private final SimpleContainer craftingDummy = new SimpleContainer(5);

    public RestrictedInventoryMenu(int containerId, Inventory inventory) {
        super(InfectionMenus.RESTRICTED_INVENTORY.get(), containerId);
        Player owner = inventory.player;

        // 0: crafting result, 1-4: 2x2 crafting grid - all inert.
        this.addSlot(new InertSlot(craftingDummy, 0, 154, 28));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                this.addSlot(new InertSlot(craftingDummy, 1 + col + row * 2, 98 + col * 18, 18 + row * 18));
            }
        }

        // 5-8: armor. Helmet + boots inert; chestplate + leggings usable.
        this.addSlot(new InertSlot(inventory, ARMOR_HELMET, 8, 8));
        this.addSlot(new UsableArmorSlot(inventory, owner, EquipmentSlot.CHEST, ARMOR_CHEST, 8, 26));
        this.addSlot(new UsableArmorSlot(inventory, owner, EquipmentSlot.LEGS, ARMOR_LEGS, 8, 44));
        this.addSlot(new InertSlot(inventory, ARMOR_BOOTS, 8, 62));

        // 9-35: main inventory. Top two rows (inv 9-26) inert; bottom row (inv 27-35, closest to hotbar) usable.
        for (int line = 0; line < 3; line++) {
            for (int col = 0; col < 9; col++) {
                int invIndex = col + (line + 1) * 9;
                int x = 8 + col * 18;
                int y = 84 + line * 18;
                if (line < 2) {
                    this.addSlot(new InertSlot(inventory, invIndex, x, y));
                } else {
                    this.addSlot(new Slot(inventory, invIndex, x, y));
                }
            }
        }

        // 36-44: hotbar - usable.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        // 45: offhand - usable.
        this.addSlot(new Slot(inventory, OFFHAND, 77, 62));
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the player's own inventory is always valid
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        // Never shift-move out of an inert slot (defensive: inert slots also reject pickup at click time).
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index >= HOTBAR_START && index < HOTBAR_END) {
            // hotbar -> usable main row
            if (!this.moveItemStackTo(stack, USABLE_MAIN_ROW_START, USABLE_MAIN_ROW_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= USABLE_MAIN_ROW_START && index < USABLE_MAIN_ROW_END) {
            // usable main row -> hotbar
            if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // usable armor/offhand -> hotbar, then usable main row. moveItemStackTo respects mayPlace,
            // so inert destination slots are always skipped.
            if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)
                    && !this.moveItemStackTo(stack, USABLE_MAIN_ROW_START, USABLE_MAIN_ROW_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }
}
