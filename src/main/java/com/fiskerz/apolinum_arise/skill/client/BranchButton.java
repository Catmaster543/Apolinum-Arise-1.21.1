package com.fiskerz.apolinum_arise.skill.client;

import java.util.List;

import com.fiskerz.apolinum_arise.skill.HealthyBranches;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * One branch option on the first-open choice screen: the branch's (placeholder) name on the first line and
 * the two stats it favours on a second, dimmer line.
 *
 * <p>Deliberately plain - a vanilla button sprite plus two lines of text. Branch art and theming wait for
 * the real branch content; what has to work today is that the player can read the trade-off and click.
 *
 * <p>The two lines are drawn by overriding {@code renderString}, the hook {@code AbstractButton} calls
 * after blitting its sprite. Overriding {@code renderWidget} instead would have meant re-implementing the
 * sprite blit, since its {@code SPRITES} table is not accessible from here.
 */
public class BranchButton extends Button {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 22;

    /** Dim grey for the advisory line, so it reads as a subtitle rather than a second label. */
    private static final int PREFERENCE_COLOR = 0xA0A0A0;

    private final int branch;
    private final Component preferenceLine;

    public BranchButton(int x, int y, int branch, OnPress onPress) {
        super(x, y, WIDTH, HEIGHT, HealthyBranches.displayName(branch), onPress, DEFAULT_NARRATION);
        this.branch = branch;
        this.preferenceLine = describePreferences(branch);
    }

    public int branch() {
        return branch;
    }

    /** "Favours high Intelligence, Favours low Strength" - or a hint when the config entry is unusable. */
    private static Component describePreferences(int branch) {
        List<HealthyBranches.StatPreference> preferences = HealthyBranches.preferences(branch);
        if (preferences.isEmpty()) {
            return Component.translatable("gui.apolinumarise.branch.pref.none");
        }
        MutableComponent line = Component.empty();
        for (int i = 0; i < preferences.size(); i++) {
            if (i > 0) {
                line.append(Component.literal(", "));
            }
            line.append(preferences.get(i).describe());
        }
        return line;
    }

    @Override
    public void renderString(GuiGraphics guiGraphics, Font font, int color) {
        int centerX = getX() + width / 2;
        // Keep the alpha the superclass computed (it fades widgets), swap only the RGB for line two.
        int preferenceColor = (color & 0xFF000000) | PREFERENCE_COLOR;
        drawCentered(guiGraphics, font, getMessage(), centerX, getY() + 3, color);
        drawCentered(guiGraphics, font, preferenceLine, centerX, getY() + 12, preferenceColor);
    }

    private void drawCentered(GuiGraphics guiGraphics, Font font, Component text, int centerX, int y, int color) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
