package com.fiskerz.apolinum_arise.sleep.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.client.StatusBarLayout;
import com.fiskerz.apolinum_arise.client.StatusBarRenderer;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.sleep.SleepManager;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Phase 10a sleep/energy bar. Occupies the SAME bottom-HUD slot as the Phase 8 bite bar and is mutually
 * exclusive with it: the bite bar is drawn for fully infected players, this one for everybody else
 * (healthy and incubating).
 *
 * <p>Unlike the bite bar this one starts FULL and counts DOWN, which needs no special handling: the shared
 * {@link StatusBarRenderer} crops the fill from a raw 0-100 percentage with no assumption about direction.
 * The only difference is the trigger for the third strip - this bar shows it when EMPTY (an explicit 0%
 * check, the point at which the exhaustion penalties begin), not when full.
 */
public final class SleepBarHud {
    private SleepBarHud() {}

    // assets/apolinumarise/textures/gui/sleep_bar.png - 81 x 27, three stacked 81 x 9 strips, same
    // convention as bite_bar.png: v=0 frame (transparent interior), v=9 filled, v=18 critical (0%) overlay.
    // Not shipped yet: a missing texture renders as the magenta/black placeholder, no crash.
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/gui/sleep_bar.png");

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        // Fully infected players have no sleep bar at all; and nothing is drawn while incapacitated (the
        // downed fade / pass-out owns the screen then).
        if (InfectionLogic.isInfected(player) || DownedManager.isIncapacitated(player)) {
            return;
        }
        float percent = SleepManager.sleepBar(player);
        // This bar's extra strip is the EMPTY/critical state - its own explicit 0% test, deliberately not
        // an inversion of the bite bar's "full" condition.
        boolean critical = percent <= 0.0F;

        StatusBarRenderer.render(guiGraphics, TEXTURE,
                StatusBarLayout.left(guiGraphics, StatusBarRenderer.BAR_WIDTH), StatusBarLayout.top(guiGraphics),
                percent, critical);
    }
}
