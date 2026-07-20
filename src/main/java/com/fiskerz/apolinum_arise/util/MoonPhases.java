package com.fiskerz.apolinum_arise.util;

/**
 * The mod-wide moon-phase fullness mapping (Full=100 / Gibbous=75 / Quarter=50 / Crescent=25 / New=0),
 * shared by the Blood Moon player debuffs (Phase 3) and the mosquito damage/spawn scaling (Phase 4).
 *
 * Vanilla phase indices run full -> new -> full: DimensionType.MOON_BRIGHTNESS_PER_PHASE is
 * {1.0, 0.75, 0.5, 0.25, 0.0, 0.25, 0.5, 0.75}, so phase 0 is the FULL moon and phase 4 the new moon.
 */
public final class MoonPhases {
    private MoonPhases() {}

    /** Fullness percent for a vanilla {@code level.getMoonPhase()} index. */
    public static int fullnessPercent(int moonPhase) {
        return switch (moonPhase) {
            case 0 -> 100;
            case 1, 7 -> 75;
            case 2, 6 -> 50;
            case 3, 5 -> 25;
            default -> 0;
        };
    }

    /** Scaling multiplier {@code 1 + fullness/100}: 1x at New Moon, 1.5x at half-full, 2x at Full Moon. */
    public static double fullnessMultiplier(int moonPhase) {
        return 1.0D + fullnessPercent(moonPhase) / 100.0D;
    }
}
