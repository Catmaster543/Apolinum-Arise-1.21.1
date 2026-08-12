package com.fiskerz.apolinum_arise.skill.client;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 9 GUI shell: an empty background panel, roughly the footprint of the inventory screen. It has
 * no functional content yet - nodes/buttons/points are a later content task. Not a container screen
 * (nothing server-side to enforce), so it is opened purely client-side once access is confirmed.
 *
 * <p>Panel texture pending (assets/apolinumarise/textures/gui/skill_panel.png, expected 176 x 166); a
 * missing texture renders as the usual magenta/black placeholder, no crash.
 */
public class SkillScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/gui/skill_panel.png");
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private int leftPos;
    private int topPos;

    public SkillScreen() {
        super(Component.translatable("screen.apolinumarise.skills"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
