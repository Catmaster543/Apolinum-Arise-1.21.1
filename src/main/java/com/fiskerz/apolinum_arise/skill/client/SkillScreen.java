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
    protected static final int PANEL_WIDTH = 176;
    protected static final int PANEL_HEIGHT = 166;

    protected int leftPos;
    protected int topPos;

    public SkillScreen() {
        this(Component.translatable("screen.apolinumarise.skills"));
    }

    /** For subclasses that reuse this shell with their own title - see the branch-choice screen. */
    protected SkillScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderPanel(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** The shell itself, so subclasses get the identical backdrop without repeating the blit. */
    protected void renderPanel(GuiGraphics guiGraphics) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
