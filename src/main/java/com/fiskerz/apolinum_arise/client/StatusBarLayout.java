package com.fiskerz.apolinum_arise.client;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.ModList;

/**
 * Shared placement for this mod's bottom-HUD status bars. The bite bar (Phase 8, infected players) and the
 * sleep bar (Phase 10a, everyone else) are mutually exclusive and occupy the SAME slot, so they resolve
 * their position from here rather than each carrying its own copy of the maths - if the row ever moves,
 * both move together and they can never drift apart.
 *
 * <p>The slot is directly above the vanilla hunger bar, pushed one row higher when Tough As Nails is
 * installed so it never lands on that mod's thirst bar (which itself renders one row above hunger). TAN is
 * a soft, optional detection - it is not a dependency.
 */
public final class StatusBarLayout {
    private StatusBarLayout() {}

    /** Right edge aligns with the vanilla food bar's right edge (screen centre + 91). */
    private static final int FOOD_BAR_RIGHT_OFFSET = 91;
    /** The food bar's own row measured from the bottom; one row (10px) above it is where air/thirst live. */
    private static final int FOOD_ROW_FROM_BOTTOM = 39;
    private static final int ROW_HEIGHT = 10;

    private static final String TOUGH_AS_NAILS_ID = "toughasnails";
    private static Boolean toughAsNailsLoaded; // resolved lazily; the mod list is fixed at runtime

    /** Left edge for a bar of the given width, right-aligned with the food bar. */
    public static int left(GuiGraphics guiGraphics, int barWidth) {
        return guiGraphics.guiWidth() / 2 + FOOD_BAR_RIGHT_OFFSET - barWidth;
    }

    /** Top edge of the shared status-bar row. */
    public static int top(GuiGraphics guiGraphics) {
        int rowsAboveFood = 1 + (isToughAsNailsLoaded() ? 1 : 0);
        return guiGraphics.guiHeight() - FOOD_ROW_FROM_BOTTOM - rowsAboveFood * ROW_HEIGHT;
    }

    private static boolean isToughAsNailsLoaded() {
        if (toughAsNailsLoaded == null) {
            toughAsNailsLoaded = ModList.get().isLoaded(TOUGH_AS_NAILS_ID);
        }
        return toughAsNailsLoaded;
    }
}
