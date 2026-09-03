package com.fiskerz.apolinum_arise.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared draw routine for this mod's bottom-HUD status bars (Phase 8 bite bar, Phase 10a sleep bar). Both
 * use the same 81x27 sheet of three stacked 81x9 strips - frame, fill, extra state - so the geometry lives
 * here once instead of being duplicated (and drifting) per bar.
 *
 * <p>The bar takes a RAW 0-100 percentage and crops the fill to it. Nothing here assumes the value is
 * rising or falling, so a bar that counts down (sleep) renders exactly like one that counts up (bite); the
 * only per-bar difference is the caller's own condition for showing the extra strip.
 *
 * <h2>Fill inset</h2>
 * The fill artwork does not span the full 81px strip - it sits INSIDE the frame's border, occupying
 * x=[1..78]. Cropping the percentage against the full texture width made the fill edge track a 81px span
 * the art never occupies, letting the drawn region reach the frame's outer pixels. The fill is therefore
 * cropped against {@link #FILL_WIDTH} and drawn from {@link #FILL_INSET_LEFT}, which both maps the
 * percentage onto the pixels that actually exist and hard-bounds the fill inside the frame.
 */
public final class StatusBarRenderer {
    private StatusBarRenderer() {}

    public static final int BAR_WIDTH = 81;
    public static final int STRIP_HEIGHT = 9;
    private static final int TEXTURE_WIDTH = 81;
    private static final int TEXTURE_HEIGHT = 27;

    private static final int V_FRAME = 0;
    private static final int V_FILL = 9;
    private static final int V_EXTRA = 18;

    /** Left edge of the fill artwork within the strip (measured from the shipped sheet). */
    public static final int FILL_INSET_LEFT = 1;
    /** Right-hand margin of the fill artwork within the strip. */
    public static final int FILL_INSET_RIGHT = 2;
    /** Width the fill artwork actually occupies - what a 0-100% crop is measured against. */
    public static final int FILL_WIDTH = BAR_WIDTH - FILL_INSET_LEFT - FILL_INSET_RIGHT;

    // Diagnostics: the computed rectangles, logged only when they change (never once per frame).
    private static final Map<ResourceLocation, String> LAST_LOGGED = new ConcurrentHashMap<>();

    /**
     * Draws one status bar. Fill first, then the optional extra strip, then the frame on top - the frame's
     * interior is transparent, so the fill shows through while its border still renders above.
     *
     * @param percent   raw 0-100 bar value; direction-agnostic
     * @param showExtra whether to draw the third strip (each bar decides its own trigger)
     */
    public static void render(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y,
                              float percent, boolean showExtra) {
        float clamped = Mth.clamp(percent, 0.0F, 100.0F);
        int filledWidth = Math.round(FILL_WIDTH * clamped / 100.0F);

        if (filledWidth > 0) {
            // Source x starts at the fill artwork's own left edge, NOT at 0, so the crop measures the
            // region the art occupies rather than the whole strip.
            guiGraphics.blit(texture, x + FILL_INSET_LEFT, y, (float) FILL_INSET_LEFT, (float) V_FILL,
                    filledWidth, STRIP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        if (showExtra) {
            guiGraphics.blit(texture, x, y, 0.0F, (float) V_EXTRA,
                    BAR_WIDTH, STRIP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        guiGraphics.blit(texture, x, y, 0.0F, (float) V_FRAME,
                BAR_WIDTH, STRIP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        logRects(texture, x, y, clamped, filledWidth, showExtra);
    }

    // The blit overload used above is 1:1 - dest (x, y, width, height) and source (u, v, width, height)
    // share the same width/height, so nothing is scaled. Logged on change so an overflow can be read off
    // the actual numbers instead of guessed at.
    private static void logRects(ResourceLocation texture, int x, int y, float percent, int filledWidth, boolean showExtra) {
        String signature = filledWidth + "|" + showExtra + "|" + x + "," + y;
        if (signature.equals(LAST_LOGGED.get(texture))) {
            return;
        }
        LAST_LOGGED.put(texture, signature);
        Apolinumarise.LOGGER.debug("[StatusBar] {} at {}%: fill src=[u={},v={},w={},h={}] dst=[x={},y={},w={},h={}] "
                        + "| frame src=[u=0,v={},w={},h={}] dst=[x={},y={},w={},h={}] | extra={}",
                texture, String.format("%.1f", percent),
                FILL_INSET_LEFT, V_FILL, filledWidth, STRIP_HEIGHT,
                x + FILL_INSET_LEFT, y, filledWidth, STRIP_HEIGHT,
                V_FRAME, BAR_WIDTH, STRIP_HEIGHT, x, y, BAR_WIDTH, STRIP_HEIGHT,
                showExtra);
    }
}
