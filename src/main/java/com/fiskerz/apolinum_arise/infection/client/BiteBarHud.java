package com.fiskerz.apolinum_arise.infection.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

/**
 * The Phase 8 bite bar: a bottom-HUD fill bar shown ONLY to the local player while they are fully infected.
 * It sits directly above the hunger bar, and stacks one row higher when Tough As Nails is present so it
 * never overlaps that mod's thirst bar (which renders one row above hunger).
 *
 * <p>Texture is a standard vertical two/three-strip sheet at {@link #TEXTURE}: an empty strip drawn full
 * width as the background, a filled strip cropped to {@code (percent/100 * width)} from the left, and a
 * distinct "ready" strip swapped in at 100%. The file is not shipped yet - until it lands, the missing
 * texture simply renders as the usual magenta/black placeholder (no crash), same as every other pending
 * asset in this mod.
 */
public final class BiteBarHud {
    private BiteBarHud() {}

    // assets/apolinumarise/textures/gui/bite_bar.png - 81 x 27, three stacked 81 x 9 strips:
    //   v=0  empty/background, v=9  filled, v=18  ready (100%).
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/gui/bite_bar.png");
    private static final int BAR_WIDTH = 81;
    private static final int STRIP_HEIGHT = 9;
    private static final int TEXTURE_WIDTH = 81;
    private static final int TEXTURE_HEIGHT = 27;
    private static final int V_EMPTY = 0;
    private static final int V_FILLED = 9;
    private static final int V_READY = 18;

    // Right edge aligns with the vanilla food bar's right edge (screen center + 91).
    private static final int FOOD_BAR_RIGHT_OFFSET = 91;
    // The food bar's own row; one row (10px) above it is where air/thirst live.
    private static final int FOOD_ROW_FROM_BOTTOM = 39;
    private static final int ROW_HEIGHT = 10;

    private static final String TOUGH_AS_NAILS_ID = "toughasnails";
    private static Boolean toughAsNailsLoaded; // resolved lazily; the mod list is fixed at runtime

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        // Only the local player, only while FULLY infected (not incubating, not healthy), and not while
        // downed (the downed fade owns the screen then).
        if (!InfectionLogic.isInfected(player) || DownedManager.isDowned(player)) {
            return;
        }
        float percent = Mth.clamp(player.getData(InfectionAttachments.INFECTION).biteBar(), 0.0F, 100.0F);

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        int x = screenWidth / 2 + FOOD_BAR_RIGHT_OFFSET - BAR_WIDTH;
        // Directly above the hunger bar; one extra row up when TAN's thirst bar occupies that first row.
        int rowsAboveFood = 1 + (isToughAsNailsLoaded() ? 1 : 0);
        int y = screenHeight - FOOD_ROW_FROM_BOTTOM - rowsAboveFood * ROW_HEIGHT;

        // Background (empty strip), always full width. A missing texture renders as the placeholder here.
        guiGraphics.blit(TEXTURE, x, y, 0.0F, (float) V_EMPTY, BAR_WIDTH, STRIP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        int filledWidth = Math.round(BAR_WIDTH * percent / 100.0F);
        if (filledWidth > 0) {
            // "ready" tint at full, otherwise the normal filled strip; both cropped to filledWidth from left.
            int v = percent >= 100.0F ? V_READY : V_FILLED;
            guiGraphics.blit(TEXTURE, x, y, 0.0F, (float) v, filledWidth, STRIP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    private static boolean isToughAsNailsLoaded() {
        if (toughAsNailsLoaded == null) {
            toughAsNailsLoaded = ModList.get().isLoaded(TOUGH_AS_NAILS_ID);
        }
        return toughAsNailsLoaded;
    }
}
