package com.fiskerz.apolinum_arise.infection.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mirrors vanilla's package-private {@code ArmorSlot} for the still-usable chestplate/leggings slots:
 * only the correct armor piece may be placed, and equipping fires the wearer's equip callback.
 */
public class UsableArmorSlot extends Slot {
    private final LivingEntity owner;
    private final EquipmentSlot equipmentSlot;

    public UsableArmorSlot(Container container, LivingEntity owner, EquipmentSlot equipmentSlot, int slotIndex, int x, int y) {
        super(container, slotIndex, x, y);
        this.owner = owner;
        this.equipmentSlot = equipmentSlot;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.canEquip(equipmentSlot, owner);
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        owner.onEquipItem(equipmentSlot, oldStack, newStack);
        super.setByPlayer(newStack, oldStack);
    }
}
