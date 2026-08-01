package com.fiskerz.apolinum_arise.infection.client;

import com.fiskerz.apolinum_arise.infection.inventory.InertSlot;
import com.fiskerz.apolinum_arise.infection.inventory.RestrictedInventoryMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The infected player's inventory screen. Reuses the vanilla inventory texture and player preview for
 * layout parity, then paints a plain semi-transparent dark rectangle over every {@link InertSlot} using
 * the standard {@code GuiGraphics.fill} - no new texture assets. The disabled slots are inert
 * server-side too; the overlay is purely the visual cue.
 */
public class RestrictedInventoryScreen extends AbstractContainerScreen<RestrictedInventoryMenu> {
    private static final ResourceLocation INVENTORY_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int OVERLAY_COLOR = 0xC0202020; // ARGB: ~75% opaque dark grey

    public RestrictedInventoryScreen(RestrictedInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(INVENTORY_LOCATION, x, y, 0, 0, this.imageWidth, this.imageHeight);
        // Player preview, exactly like the vanilla inventory screen.
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x + 26, y + 8, x + 75, y + 78, 30, 0.0625F,
                mouseX, mouseY, this.minecraft.player);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Runs inside the container-translated pose (origin at leftPos/topPos) and after slot items are
        // drawn, so the overlays sit ON TOP of any locked item yet still under hover tooltips.
        for (Slot slot : this.menu.slots) {
            if (slot instanceof InertSlot) {
                guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, OVERLAY_COLOR);
            }
        }
    }
}
