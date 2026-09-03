package com.fiskerz.apolinum_arise.infection.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.client.StatusBarLayout;
import com.fiskerz.apolinum_arise.client.StatusBarRenderer;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Phase 8 bite bar: a bottom-HUD fill bar shown ONLY to the local player while they are fully infected.
 * It counts UP from 0 to 100, and its extra "ready" strip shows at 100%.
 *
 * <p>Geometry and the three-strip draw order live in {@link StatusBarRenderer}, shared with the sleep bar.
 */
public final class BiteBarHud {
    private BiteBarHud() {}

    // assets/apolinumarise/textures/gui/bite_bar.png - 81 x 27, three stacked 81 x 9 strips:
    //   v=0  frame (border/decoration, transparent interior), v=9  filled, v=18  ready (100%) overlay.
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/gui/bite_bar.png");

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        // Only the local player, only while FULLY infected (not incubating, not healthy), and not while
        // incapacitated (the downed fade / pass-out owns the screen then).
        if (!InfectionLogic.isInfected(player) || DownedManager.isIncapacitated(player)) {
            return;
        }
        float percent = player.getData(InfectionAttachments.INFECTION).biteBar();
        // This bar's extra strip is the "ready to bite" state: exactly full.
        boolean ready = percent >= 100.0F;

        StatusBarRenderer.render(guiGraphics, TEXTURE,
                StatusBarLayout.left(guiGraphics, StatusBarRenderer.BAR_WIDTH), StatusBarLayout.top(guiGraphics),
                percent, ready);
    }
}
