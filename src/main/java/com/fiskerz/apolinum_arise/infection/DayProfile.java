package com.fiskerz.apolinum_arise.infection;

import javax.annotation.Nullable;

/**
 * Data-driven description of one incubation day (1-10). The whole symptom progression lives in
 * {@link #TABLE} as ten entries rather than scattered {@code if (day == X)} branches, so the shape of
 * the disease is legible and reorderable in one place. Concrete magnitudes (chances, durations,
 * amplifiers, weights, mole chances) are NOT here - they are read live from
 * {@link com.fiskerz.apolinum_arise.config.Config} when applied, so everything stays server-config
 * tunable without recompiling. This record only captures WHICH systems are active on a given day.
 *
 * <p>Day numbering matches the spec: day N = incubation-day-delta N-1 (day 1 = the bite day), and the
 * day-10 moonrise is the existing isIncubating -> isInfected transition, after which none of this applies.
 */
public record DayProfile(
        int day,
        boolean earlyRoller,        // days 1-2: shared Nausea/Weakness/Mining-Fatigue roller (fixed 5s)
        boolean mainPool,           // days 3+: the weighted random-effect pool
        boolean poolIncludesHunger, // days 4+: Hunger joins the pool (with its duration multiplier)
        boolean poolChanceDoubled,  // days 7+: pool per-attempt chance is doubled
        boolean constantHunger,     // days 7+: unconditional always-on Hunger I
        boolean sunWeakness,        // days 5+: sunlight -> Weakness, amplifier escalating by day
        boolean sunMiningFatigue,   // days 8+: sunlight -> flat Mining Fatigue
        SunThreshold sunThreshold,  // days 8-9 Blindness / day 10 Blindness+Wither past the exposure threshold
        boolean rollsMole,          // days 6+: roll that day's mole chance at day-start
        @Nullable String actionbarKey) {

    /** What sustained sun exposure past the configured threshold does on a given day. */
    public enum SunThreshold { NONE, BLINDNESS, BLINDNESS_AND_WITHER }

    private static final String MSG = "message.apolinumarise.symptom.";

    // The full 10-day table. Index 0 = day 1.
    private static final DayProfile[] TABLE = {
            //          day early  pool  poolHun poolx2 constHun sunWk  sunMF  sunThreshold                       mole   actionbar
            new DayProfile(1,  true,  false, false, false, false,  false, false, SunThreshold.NONE,               false, null),
            new DayProfile(2,  true,  false, false, false, false,  false, false, SunThreshold.NONE,               false, null),
            new DayProfile(3,  false, true,  false, false, false,  false, false, SunThreshold.NONE,               false, MSG + "day3"),
            new DayProfile(4,  false, true,  true,  false, false,  false, false, SunThreshold.NONE,               false, null),
            new DayProfile(5,  false, true,  true,  false, false,  true,  false, SunThreshold.NONE,               false, MSG + "day5"),
            new DayProfile(6,  false, true,  true,  false, false,  true,  false, SunThreshold.NONE,               true,  null),
            new DayProfile(7,  false, true,  true,  true,  true,   true,  false, SunThreshold.NONE,               true,  MSG + "day7"),
            new DayProfile(8,  false, true,  true,  true,  true,   true,  true,  SunThreshold.BLINDNESS,           true,  null),
            new DayProfile(9,  false, true,  true,  true,  true,   true,  true,  SunThreshold.BLINDNESS,           true,  null),
            // Day 10's message is the Wither warning; it is fired on each Wither (re-)application in the
            // symptom engine (Phase 6 A1), NOT once at day-start, so its actionbarKey here is null.
            new DayProfile(10, false, true,  true,  true,  true,   true,  true,  SunThreshold.BLINDNESS_AND_WITHER, true,  null),
    };

    /** Profile for a symptom day number, clamped to the valid 1-10 range. */
    public static DayProfile forDay(int dayNumber) {
        int clamped = Math.max(1, Math.min(TABLE.length, dayNumber));
        return TABLE[clamped - 1];
    }

    public static int maxDay() {
        return TABLE.length;
    }
}
