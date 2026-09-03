package com.fiskerz.apolinum_arise.skill.client;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The inventory's skill-tree button. Its icon depends on which side the player has unlocked, so the button
 * itself communicates healthy vs infected without a label.
 *
 * <p>Texture {@code assets/apolinumarise/textures/gui/skill_tree_button.png} is 32x32, four 16x16 quadrants:
 * <pre>
 *   (0,0)  healthy,  not hovered      (16,0)  infected, not hovered
 *   (0,16) healthy,  hovered          (16,16) infected, hovered
 * </pre>
 * So the column is chosen by side and the row by hover. The two sides are mutually exclusive, and the
 * button is only ever constructed when the player holds one of them (see SkillClientEvents) - with neither,
 * no button is added to the screen at all, so nothing about the skill system is revealed.
 */
public class SkillTreeButton extends Button {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/gui/skill_tree_button.png");

    public static final int SIZE = 16;
    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 32;

    private final boolean healthySide;

    public SkillTreeButton(int x, int y, boolean healthySide, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.translatable("gui.apolinumarise.skills"), onPress, DEFAULT_NARRATION);
        this.healthySide = healthySide;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int u = healthySide ? 0 : SIZE;          // left column = healthy, right column = infected
        int v = isHovered() ? SIZE : 0;          // bottom row = hovered, top row = idle
        guiGraphics.blit(TEXTURE, getX(), getY(), (float) u, (float) v, SIZE, SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
