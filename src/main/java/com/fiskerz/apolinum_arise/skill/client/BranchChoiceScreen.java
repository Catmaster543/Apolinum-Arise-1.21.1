package com.fiskerz.apolinum_arise.skill.client;

import com.fiskerz.apolinum_arise.network.ChooseBranchPayload;
import com.fiskerz.apolinum_arise.skill.HealthyBranches;
import com.fiskerz.apolinum_arise.skill.HealthyStat;
import com.fiskerz.apolinum_arise.skill.SkillAttachments;
import com.fiskerz.apolinum_arise.skill.SkillProfileData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The healthy side's one-time branch choice (Phase 11 item 4), shown INSTEAD of the quest book the first
 * time a healthy player with access opens their skills. It reuses the Phase 9 shell, so the backdrop is
 * literally the same panel the plain skill screen draws.
 *
 * <p>Contents: the three stats rolled when the book was used, then the four branches as buttons, each
 * annotated with the two stats it favours and which way. Clicking sends the choice to the server, which is
 * the only place it is validated and stored; this screen closes immediately and is never shown again,
 * because the routing in {@link SkillClientEvents} reads the synced branch field.
 *
 * <p>Escape closes without choosing - the choice is permanent, so it must be possible to back out and
 * think about it. Nothing is lost: the next open lands right back here.
 */
public class BranchChoiceScreen extends SkillScreen {
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int BUTTON_GAP = 2;

    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int STAT_COLOR = 0xE0E0E0;

    public BranchChoiceScreen() {
        super(Component.translatable("screen.apolinumarise.branch_choice"));
    }

    @Override
    protected void init() {
        super.init(); // centres the panel
        int buttonsHeight = HealthyBranches.COUNT * BranchButton.HEIGHT
                + (HealthyBranches.COUNT - 1) * BUTTON_GAP;
        // Buttons sit against the bottom of the panel; the text block fills the space above them. The two
        // halves are a tight fit inside 166px: 94 for the four two-line buttons, and 8..61 for the title,
        // three stat lines and the warning, leaving a 3px gap. Changing either needs both re-checked.
        int y = topPos + PANEL_HEIGHT - PADDING - buttonsHeight;
        for (int branch = 0; branch < HealthyBranches.COUNT; branch++) {
            int index = branch;
            addRenderableWidget(new BranchButton(
                    leftPos + (PANEL_WIDTH - BranchButton.WIDTH) / 2, y, branch, button -> choose(index)));
            y += BranchButton.HEIGHT + BUTTON_GAP;
        }
    }

    private void choose(int branch) {
        PacketDistributor.sendToServer(new ChooseBranchPayload(branch));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int y = topPos + PADDING;
        drawCentered(guiGraphics, Component.translatable("gui.apolinumarise.branch.title"), y, TITLE_COLOR);
        y += LINE_HEIGHT + 2;

        SkillProfileData profile = profile();
        for (HealthyStat stat : HealthyStat.values()) {
            drawCentered(guiGraphics, Component.translatable("gui.apolinumarise.stat.line",
                    stat.displayName(), profile.stat(stat)), y, STAT_COLOR);
            y += LINE_HEIGHT;
        }
        y += 2;
        drawCentered(guiGraphics, Component.translatable("gui.apolinumarise.branch.prompt"), y, STAT_COLOR);
    }

    /** Stats come straight from the self-synced attachment, so no extra request is needed to show them. */
    private SkillProfileData profile() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? SkillProfileData.NONE : player.getData(SkillAttachments.SKILL_PROFILE);
    }

    private void drawCentered(GuiGraphics guiGraphics, Component text, int y, int color) {
        guiGraphics.drawString(this.font, text,
                leftPos + (PANEL_WIDTH - this.font.width(text)) / 2, y, color, false);
    }
}
