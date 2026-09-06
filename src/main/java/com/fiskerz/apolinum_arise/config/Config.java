package com.fiskerz.apolinum_arise.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

// The mod's single SERVER-type config: stored per world, controlled by whoever runs the server,
// and synced to clients on join. Registered from the Apolinumarise constructor.
// NOTE: SERVER config values are only readable after the config loads (i.e. once a world is up),
// never during registration or common setup.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_MOD = BUILDER
            .comment("Master kill-switch: set to false to disable all of this mod's systems in this world.")
            .define("enableMod", true);

    // --- Awakening shrine placement (Patch 1) ---

    public static final ModConfigSpec.BooleanValue SHRINE_SPAWN_EXCLUSION_ENABLED = BUILDER
            .comment("When true, awakening shrines are forbidden from generating too close to the world origin (0,0).")
            .define("shrineSpawnExclusionEnabled", true);

    public static final ModConfigSpec.IntValue SHRINE_SPAWN_EXCLUSION_DISTANCE = BUILDER
            .comment("Half-width of the square, in blocks, centered on world origin where shrines cannot generate.",
                    "A candidate is rejected when abs(x) < distance AND abs(z) < distance. Read live during worldgen.")
            .defineInRange("shrineSpawnExclusionDistance", 3000, 0, 30_000_000);

    public static final ModConfigSpec.BooleanValue SHRINE_REQUIRE_ACTIVATION_COMMAND = BUILDER
            .comment("When true, shrines generate NOWHERE until '/shrine activate' flips the persisted world flag.",
                    "Once activated, the origin-distance exclusion above is also bypassed. Affects only newly generated chunks.")
            .define("shrineRequireActivationCommand", false);

    // --- Blood Moon cycle (Phase 3) ---

    public static final ModConfigSpec.DoubleValue BLOOD_MOON_BASE_CHANCE = BUILDER
            .comment("Starting nightly Blood Moon chance after unlock, and the reset value after a successful Blood Moon.")
            .defineInRange("bloodMoonBaseChance", 0.01D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue BLOOD_MOON_CHANCE_GROWTH = BUILDER
            .comment("Multiplier applied to the nightly Blood Moon chance after a failed roll.")
            .defineInRange("bloodMoonChanceGrowth", 1.5D, 1.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue BLOOD_MOON_CHANCE_CAP = BUILDER
            .comment("Maximum Blood Moon chance after growth is applied.")
            .defineInRange("bloodMoonChanceCap", 1.0D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue MOB_DAMAGE_MULTIPLIER = BUILDER
            .comment("Hostile-mob damage multiplier AT FULL MOON. Scales linearly with moon fullness down to 1.0",
                    "(no buff) at New Moon: multiplier(phase) = 1 + (this - 1) * fullness(phase)/100.")
            .defineInRange("mobDamageMultiplier", 1.2D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue MOB_HEALTH_MULTIPLIER = BUILDER
            .comment("Hostile-mob max-health multiplier AT FULL MOON. Scales linearly with moon fullness down to 1.0",
                    "(no buff) at New Moon: multiplier(phase) = 1 + (this - 1) * fullness(phase)/100.")
            .defineInRange("mobHealthMultiplier", 1.2D, 0.0D, 100.0D);

    public static final ModConfigSpec.IntValue EFFECT_REFRESH_INTERVAL_TICKS = BUILDER
            .comment("How often, in ticks, Blood Moon player debuffs are refreshed while active.")
            .defineInRange("effectRefreshIntervalTicks", 100, 1, 1_000_000);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> WEAKNESS_AMPLIFIER_BY_PHASE = BUILDER
            .comment("Eight Weakness amplifiers, one for each vanilla moon phase index (0-7).",
                    "Vanilla phase 0 is the full moon and phase 4 the new moon, so the default peaks at index 0.")
            .defineList("weaknessAmplifierByPhase", () -> List.of(2, 1, 1, 0, 0, 0, 1, 1), value -> value instanceof Integer integer && integer >= 0 && integer <= 255);

    public static final ModConfigSpec.IntValue MINING_FATIGUE_THRESHOLD_1 = BUILDER
            .comment("Moon fullness percent at which Mining Fatigue I begins.")
            .defineInRange("miningFatigueThreshold1", 50, 0, 100);

    public static final ModConfigSpec.IntValue MINING_FATIGUE_THRESHOLD_2 = BUILDER
            .comment("Moon fullness percent at which Mining Fatigue II begins.")
            .defineInRange("miningFatigueThreshold2", 75, 0, 100);

    public static final ModConfigSpec.IntValue MINING_FATIGUE_THRESHOLD_3 = BUILDER
            .comment("Moon fullness percent at which Mining Fatigue III begins.")
            .defineInRange("miningFatigueThreshold3", 90, 0, 100);

    public static final ModConfigSpec.IntValue MINING_FATIGUE_AMPLIFIER_1 = BUILDER
            .comment("Mining Fatigue I amplifier (0 = level I).")
            .defineInRange("miningFatigueAmplifier1", 0, 0, 255);

    public static final ModConfigSpec.IntValue MINING_FATIGUE_AMPLIFIER_2 = BUILDER
            .comment("Mining Fatigue II amplifier (0 = level I).")
            .defineInRange("miningFatigueAmplifier2", 1, 0, 255);

    public static final ModConfigSpec.IntValue MINING_FATIGUE_AMPLIFIER_3 = BUILDER
            .comment("Mining Fatigue III amplifier (0 = level I).")
            .defineInRange("miningFatigueAmplifier3", 2, 0, 255);

    // --- Mosquito (Phase 4) ---

    public static final ModConfigSpec.IntValue MOSQUITO_WANDER_RADIUS = BUILDER
            .comment("Horizontal radius, in blocks, within which an idle mosquito picks its next hover point.")
            .defineInRange("mosquitoWanderRadius", 16, 1, 64);

    public static final ModConfigSpec.IntValue MOSQUITO_WANDER_INTERVAL_TICKS = BUILDER
            .comment("Ticks a mosquito hovers in place before picking its next wander point.")
            .defineInRange("mosquitoWanderIntervalTicks", 80, 1, 1_000_000);

    public static final ModConfigSpec.IntValue MOSQUITO_HOVER_MIN_HEIGHT = BUILDER
            .comment("Minimum hover/spawn height, in blocks, above the solid floor of the column.")
            .defineInRange("mosquitoHoverMinHeight", 2, 1, 64);

    public static final ModConfigSpec.IntValue MOSQUITO_HOVER_MAX_HEIGHT = BUILDER
            .comment("Maximum hover/spawn height, in blocks, above the solid floor of the column.")
            .defineInRange("mosquitoHoverMaxHeight", 10, 1, 64);

    public static final ModConfigSpec.DoubleValue MOSQUITO_DETECTION_RANGE = BUILDER
            .comment("Player detection/aggro range in blocks. Default 70 = 2x the vanilla Zombie FOLLOW_RANGE of 35",
                    "(verified against the decompiled 1.21.1 Zombie.createAttributes and hardcoded here).")
            .defineInRange("mosquitoDetectionRange", 70.0D, 1.0D, 256.0D);

    public static final ModConfigSpec.DoubleValue MOSQUITO_BASE_DAMAGE = BUILDER
            .comment("Base bite damage before moon-phase scaling. Default 3.0 = the vanilla Zombie ATTACK_DAMAGE",
                    "(verified against the decompiled 1.21.1 Zombie.createAttributes).")
            .defineInRange("mosquitoBaseDamage", 3.0D, 0.0D, 1024.0D);

    public static final ModConfigSpec.IntValue MOSQUITO_SPAWN_INTERVAL_TICKS = BUILDER
            .comment("Ticks between mosquito spawn attempts while a Blood Moon is active.")
            .defineInRange("mosquitoSpawnIntervalTicks", 200, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue MOSQUITO_SPAWN_BASE_CHANCE = BUILDER
            .comment("Base success chance per spawn attempt, before moon-phase scaling.")
            .defineInRange("mosquitoSpawnBaseChance", 0.02D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue MOSQUITO_MAX_CONCURRENT = BUILDER
            .comment("Maximum mosquitoes alive in the Overworld at once; spawn attempts are skipped at the cap.")
            .defineInRange("mosquitoMaxConcurrent", 6, 0, 256);

    public static final ModConfigSpec.IntValue MOSQUITO_UNDERGROUND_SPAWN_WEIGHT_PERCENT = BUILDER
            .comment("Percent of spawn attempts that target underground (cave) locations instead of the surface.")
            .defineInRange("mosquitoUndergroundSpawnWeightPercent", 15, 0, 100);

    // --- Infection (Patch 2) ---

    public static final ModConfigSpec.DoubleValue INFECTION_CHANCE_PER_BITE = BUILDER
            .comment("Chance, per successful mosquito bite, that a not-yet-incubating/infected player begins incubating.")
            .defineInRange("infectionChancePerBite", 0.10D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue INFECTION_INCUBATION_DAYS = BUILDER
            .comment("Number of incubation days; the player becomes fully infected at the moonrise of this day",
                    "(day N = N-1 day-counts after the bite, so N=10 completes at day-10 moonrise).")
            .defineInRange("infectionIncubationDays", 10, 1, 1_000_000);

    // --- Skill system (Phase 9) ---

    public static final ModConfigSpec.IntValue HEALTHY_UNLOCK_INFECTED_THRESHOLD = BUILDER
            .comment("How many players must EVER complete incubation (cumulative, world-wide) before the healthy-side",
                    "skill system unlocks permanently. One-way: it never re-locks even if the live infected count drops.")
            .defineInRange("healthyUnlockInfectedThreshold", 3, 1, 1_000_000);

    // --- Blood Moon awakening event (Phase 2) ---

    public static final ModConfigSpec.DoubleValue AWAKENING_HEALTH_LEFT = BUILDER
            .comment("Health the triggering player is left with after touching the Awakening Block (2.0 = one heart).")
            .defineInRange("awakeningHealthLeft", 1.0, 0.5, 1024.0);

    public static final ModConfigSpec.IntValue AWAKENING_BLINDNESS_DURATION_TICKS = BUILDER
            .comment("Blindness duration, in ticks, applied to the triggering player.")
            .defineInRange("awakeningBlindnessDurationTicks", 200, 0, 1_000_000);

    public static final ModConfigSpec.IntValue AWAKENING_BLINDNESS_AMPLIFIER = BUILDER
            .comment("Blindness amplifier (0 = level I).")
            .defineInRange("awakeningBlindnessAmplifier", 1, 0, 255);

    public static final ModConfigSpec.IntValue AWAKENING_NAUSEA_DURATION_TICKS = BUILDER
            .comment("Nausea duration, in ticks, applied to the triggering player.")
            .defineInRange("awakeningNauseaDurationTicks", 600, 0, 1_000_000);

    public static final ModConfigSpec.IntValue AWAKENING_NAUSEA_AMPLIFIER = BUILDER
            .comment("Nausea amplifier (0 = level I).")
            .defineInRange("awakeningNauseaAmplifier", 2, 0, 255);

    public static final ModConfigSpec.IntValue AWAKENING_WEAKNESS_DURATION_TICKS = BUILDER
            .comment("Weakness duration, in ticks, applied to the triggering player.")
            .defineInRange("awakeningWeaknessDurationTicks", 1200, 0, 1_000_000);

    public static final ModConfigSpec.IntValue AWAKENING_WEAKNESS_AMPLIFIER = BUILDER
            .comment("Weakness amplifier (0 = level I).")
            .defineInRange("awakeningWeaknessAmplifier", 0, 0, 255);

    public static final ModConfigSpec.DoubleValue SOUND_FALLOFF_DISTANCE = BUILDER
            .comment("Distance (blocks) over which the awakening broadcast sound fades from full volume down to soundMinVolume.")
            .defineInRange("soundFalloffDistance", 100.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue SOUND_MIN_VOLUME = BUILDER
            .comment("Floor volume of the awakening broadcast sound; heard at any distance and from other dimensions.")
            .defineInRange("soundMinVolume", 0.15, 0.0, 1.0);

    // --- Infection symptom timeline (Phase 5): everything below applies ONLY while incubating ---

    // Days 1-2 shared early-symptom roller. interval x chance over the ~2-day window averages ~2 hits
    // (48000-tick window / 1200 = 40 attempts x 0.05 = 2.0 expected; natural variance is intended).
    public static final ModConfigSpec.IntValue SYMPTOM_EARLY_ROLL_INTERVAL_TICKS = BUILDER
            .comment("Days 1-2: ticks between early-symptom roll attempts.")
            .defineInRange("symptomEarlyRollIntervalTicks", 1200, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue SYMPTOM_EARLY_ROLL_CHANCE = BUILDER
            .comment("Days 1-2: success chance per roll attempt. Tuned with the interval so the full window averages ~2 hits.")
            .defineInRange("symptomEarlyRollChance", 0.05D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue SYMPTOM_EARLY_DURATION_TICKS = BUILDER
            .comment("Days 1-2: fixed duration (ticks) of each early symptom (Nausea/Weakness/Mining Fatigue, equal weight). Default 100 = 5s.")
            .defineInRange("symptomEarlyDurationTicks", 100, 1, 1_000_000);

    // Days 3-10 main random-effect pool.
    public static final ModConfigSpec.IntValue SYMPTOM_POOL_INTERVAL_TICKS = BUILDER
            .comment("Days 3+: ticks between main-pool roll attempts.")
            .defineInRange("symptomPoolIntervalTicks", 600, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue SYMPTOM_POOL_BASE_CHANCE = BUILDER
            .comment("Days 3+: base success chance per main-pool roll attempt (doubled from day 7 by symptomPoolDay7ChanceMultiplier).")
            .defineInRange("symptomPoolBaseChance", 0.35D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue SYMPTOM_POOL_BASE_WEIGHT = BUILDER
            .comment("Days 3+: selection weight of each common pool effect (Nausea, Weakness, Mining Fatigue, Blindness).")
            .defineInRange("symptomPoolBaseWeight", 1.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SYMPTOM_POOL_POISON_WEIGHT = BUILDER
            .comment("Days 3+: selection weight of Poison in the pool (lower than the common effects).")
            .defineInRange("symptomPoolPoisonWeight", 0.3D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SYMPTOM_POOL_HUNGER_WEIGHT = BUILDER
            .comment("Days 4+: selection weight of Hunger in the pool (similar to Poison's).")
            .defineInRange("symptomPoolHungerWeight", 0.3D, 0.0D, 1000.0D);

    public static final ModConfigSpec.IntValue SYMPTOM_POOL_MIN_DURATION_TICKS = BUILDER
            .comment("Days 3+: minimum rolled effect duration (ticks). Default 60 = 3s.")
            .defineInRange("symptomPoolMinDurationTicks", 60, 1, 1_000_000);

    public static final ModConfigSpec.IntValue SYMPTOM_POOL_MAX_DURATION_TICKS = BUILDER
            .comment("Days 3+: maximum rolled effect duration (ticks). Default 600 = 30s.")
            .defineInRange("symptomPoolMaxDurationTicks", 600, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue SYMPTOM_POOL_HUNGER_DURATION_MULTIPLIER = BUILDER
            .comment("Days 4+: when Hunger is the rolled pool effect, its applied duration is the rolled value times this (only Hunger).")
            .defineInRange("symptomPoolHungerDurationMultiplier", 3.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SYMPTOM_POOL_DAY7_CHANCE_MULTIPLIER = BUILDER
            .comment("Days 7+: multiplier applied to the main-pool per-attempt chance.")
            .defineInRange("symptomPoolDay7ChanceMultiplier", 2.0D, 0.0D, 1000.0D);

    // Sun-exposure effects (days 5+). Exposure reuses vanilla's undead sun-burn conditions
    // (isDay + high sky light + can-see-sky + not in water/rain/powder-snow), minus the burn-chance roll.
    public static final ModConfigSpec.IntValue SYMPTOM_REFRESH_INTERVAL_TICKS = BUILDER
            .comment("How often (ticks) continuous symptom effects (sun exposure, constant Hunger) are refreshed.")
            .defineInRange("symptomRefreshIntervalTicks", 40, 1, 1_000_000);

    public static final ModConfigSpec.IntValue SYMPTOM_SUN_WEAKNESS_BASE_AMPLIFIER = BUILDER
            .comment("Day 5+: base Weakness amplifier applied while in sunlight (day 5 uses this).")
            .defineInRange("symptomSunWeaknessBaseAmplifier", 0, 0, 255);

    public static final ModConfigSpec.IntValue SYMPTOM_SUN_WEAKNESS_PER_DAY_INCREMENT = BUILDER
            .comment("Day 5+: added to the sun Weakness amplifier per day past day 5. So day5=I, day6=II, ... day10=VI by default.")
            .defineInRange("symptomSunWeaknessPerDayIncrement", 1, 0, 255);

    public static final ModConfigSpec.IntValue SYMPTOM_SUN_MINING_FATIGUE_AMPLIFIER = BUILDER
            .comment("Day 8+: flat Mining Fatigue amplifier applied while in sunlight, alongside the escalating Weakness.")
            .defineInRange("symptomSunMiningFatigueAmplifier", 0, 0, 255);

    public static final ModConfigSpec.IntValue SYMPTOM_SUN_BLINDNESS_THRESHOLD_SECONDS = BUILDER
            .comment("Days 8-9: seconds of unbroken sun exposure after which Blindness is also applied while exposure continues.")
            .defineInRange("symptomSunBlindnessThresholdSeconds", 60, 1, 1_000_000);

    public static final ModConfigSpec.IntValue SYMPTOM_SUN_BLINDNESS_THRESHOLD_SECONDS_DAY10 = BUILDER
            .comment("Day 10: replaces the day-8 threshold; at this many seconds of unbroken exposure BOTH Blindness and Wither apply.")
            .defineInRange("symptomSunBlindnessThresholdSecondsDay10", 30, 1, 1_000_000);

    public static final ModConfigSpec.IntValue SYMPTOM_SUN_WITHER_AMPLIFIER = BUILDER
            .comment("Day 10: Wither amplifier applied at the exposure threshold (0 = Wither I).")
            .defineInRange("symptomSunWitherAmplifier", 0, 0, 255);

    // Moles.
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MOLE_CHANCE_PER_DAY = BUILDER
            .comment("Per-day chance to grow a new mole, indexed day 6..day 10 (5 entries). Days 1-5 never grow moles.")
            .defineList("moleChancePerDay", () -> List.of(0.75D, 0.5D, 0.5D, 0.5D, 0.7D),
                    value -> value instanceof Double chance && chance >= 0.0D && chance <= 1.0D);

    public static final ModConfigSpec.DoubleValue MOLE_BASE_SIZE_MIN = BUILDER
            .comment("Minimum random original/base size of a new mole, in player-model pixels.")
            .defineInRange("moleBaseSizeMin", 0.6D, 0.01D, 16.0D);

    public static final ModConfigSpec.DoubleValue MOLE_BASE_SIZE_MAX = BUILDER
            .comment("Maximum random original/base size of a new mole, in player-model pixels.")
            .defineInRange("moleBaseSizeMax", 1.0D, 0.01D, 16.0D);

    public static final ModConfigSpec.DoubleValue MOLE_DAILY_GROWTH_INCREMENT = BUILDER
            .comment("Base size a mole gains each day since creation, before the daily random multiplier.")
            .defineInRange("moleDailyGrowthIncrement", 0.15D, 0.0D, 16.0D);

    public static final ModConfigSpec.DoubleValue MOLE_GROWTH_MULTIPLIER_MIN = BUILDER
            .comment("Low end of the per-day random growth multiplier rolled fresh for each mole each day.")
            .defineInRange("moleGrowthMultiplierMin", 1.0D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue MOLE_GROWTH_MULTIPLIER_MAX = BUILDER
            .comment("High end of the per-day random growth multiplier rolled fresh for each mole each day.")
            .defineInRange("moleGrowthMultiplierMax", 2.0D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue MOLE_CAP_MULTIPLIER_MIN = BUILDER
            .comment("Low end of a mole's cap multiplier, rolled once at creation. Cap = base size x this..max (default 3.0 +/- 0.7).")
            .defineInRange("moleCapMultiplierMin", 2.3D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue MOLE_CAP_MULTIPLIER_MAX = BUILDER
            .comment("High end of a mole's cap multiplier, rolled once at creation.")
            .defineInRange("moleCapMultiplierMax", 3.7D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue MOLE_DAY10_SIZE_MULTIPLIER = BUILDER
            .comment("Day 10 mole only: multiplies its base (starting) size, since it has no meaningful time to grow before moonrise.")
            .defineInRange("moleDay10SizeMultiplier", 2.0D, 0.0D, 100.0D);

    // --- Downed / revive system (Phase 7): replaces player death with a timed downed state ---

    public static final ModConfigSpec.BooleanValue DOWNED_ENABLED = BUILDER
            .comment("Master toggle for the downed/revive system. When false, players die normally.")
            .define("downedEnabled", true);

    public static final ModConfigSpec.IntValue DOWNED_DURATION_SECONDS = BUILDER
            .comment("How long (seconds) a downed player has before they actually die, if not revived.")
            .defineInRange("downedDurationSeconds", 60, 1, 100_000);

    public static final ModConfigSpec.DoubleValue DOWNED_HEALTH_FLOOR = BUILDER
            .comment("Health a downed player is clamped to (kept alive at) for the whole downed state.")
            .defineInRange("downedHealthFloor", 1.0D, 0.5D, 1024.0D);

    public static final ModConfigSpec.DoubleValue REVIVE_RANGE = BUILDER
            .comment("Maximum distance (blocks) at which a downed player can be revived.")
            .defineInRange("reviveRange", 3.0D, 0.5D, 64.0D);

    public static final ModConfigSpec.DoubleValue REVIVE_HOLD_SECONDS = BUILDER
            .comment("Seconds the revive key must be held continuously to complete a revive.")
            .defineInRange("reviveHoldSeconds", 5.0D, 0.1D, 3600.0D);

    public static final ModConfigSpec.DoubleValue REVIVE_HEALTH_ON_REVIVE = BUILDER
            .comment("Health a revived player is restored to (2.0 = one heart).")
            .defineInRange("reviveHealthOnRevive", 2.0D, 0.5D, 1024.0D);

    // --- Bite bar & player-to-player spread (Phase 8) ---

    public static final ModConfigSpec.DoubleValue BITE_BAR_FILL_RATE_PERCENT_PER_20SEC = BUILDER
            .comment("Percent the infected player's bite bar fills per 20 seconds while a fill contributor is active",
                    "(darkness is the first contributor). At the default 1.5 the bar goes 0->100% in ~22 minutes of darkness.")
            .defineInRange("biteBarFillRatePercentPer20Sec", 1.5D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue BITE_BAR_BLOOD_MOON_MULTIPLIER = BUILDER
            .comment("Extra multiplier applied to the total bite-bar fill rate while a Blood Moon is active,",
                    "on top of the base rate above. Default 2.0, so darkness + active Blood Moon = 1.5 x 2 = 3x the base rate.")
            .defineInRange("biteBarBloodMoonMultiplier", 2.0D, 0.0D, 100.0D);

    public static final ModConfigSpec.IntValue BITE_BAR_DARKNESS_THRESHOLD = BUILDER
            .comment("The bite bar fills while the player's effective (time-of-day-adjusted) light level is AT OR BELOW this.",
                    "Default 7 matches the darkness at which hostile mobs spawn. Uses getMaxLocalRawBrightness, not raw sky light.")
            .defineInRange("biteBarDarknessThreshold", 7, 0, 15);

    public static final ModConfigSpec.DoubleValue BITE_INFECTION_CHANCE = BUILDER
            .comment("Chance a bite (by an infected player with a full bar, on a downed healthy target) starts the target's incubation.")
            .defineInRange("biteInfectionChance", 0.20D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue BITE_RANGE = BUILDER
            .comment("Maximum distance (blocks) at which an infected player can bite a downed healthy target.")
            .defineInRange("biteRange", 3.0D, 0.5D, 64.0D);

    // --- Sleep bar & pass-out (Phase 10a): applies ONLY to players who are not fully infected ---

    public static final ModConfigSpec.DoubleValue SLEEP_BAR_DRAIN_DAYS = BUILDER
            .comment("Days of elapsed game time for the sleep bar to fall from 100% to 0%, regardless of activity.",
                    "The drain never pauses (not even while lying in a bed), so at the default 3 one full day",
                    "of drain is 100/3 = 33.33%.")
            .defineInRange("sleepBarDrainDays", 3.0D, 0.01D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SLEEP_BAR_NIGHT_REFILL_MULTIPLIER = BUILDER
            .comment("Scales the base refill rate, which is calibrated so one full night of continuous lying",
                    "(the vanilla sleepable window, 10918 ticks) restores 100/3 = 33.33% at a multiplier of 1.0.",
                    "The default 1.5 raises that to 100/2 = 50% per night, so sleeping 2 nights out of every 3",
                    "exactly sustains the 3-day drain instead of having to sleep every single night.")
            .defineInRange("sleepBarNightRefillMultiplier", 1.5D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue SLEEP_BAR_DAY_OR_BLOOD_MOON_REFILL_MULTIPLIER = BUILDER
            .comment("Extra multiplier applied to the night refill rate while lying during the DAY or during an",
                    "active Blood Moon (0.5 = half rate). Does not apply to the involuntary pass-out state.")
            .defineInRange("sleepBarDayOrBloodMoonRefillMultiplier", 0.5D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue SLEEP_ZERO_SLOWNESS_DAYS = BUILDER
            .comment("Days spent CONTINUOUSLY at 0% before Slowness I is added and Weakness escalates to II.",
                    "Any refill above 0% clears the effects and resets this clock to zero.")
            .defineInRange("sleepZeroSlownessDays", 2.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SLEEP_ZERO_PASS_OUT_DAYS = BUILDER
            .comment("Days spent CONTINUOUSLY at 0% before the player passes out (see sleepPassOutExitThreshold).")
            .defineInRange("sleepZeroPassOutDays", 3.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SLEEP_PASS_OUT_EXIT_THRESHOLD = BUILDER
            .comment("Bar percentage at which the pass-out state ends. Purely bar-based, never time-based.",
                    "A passed-out player refills at the base night rate (they are unconscious, not napping)",
                    "while the drain keeps running, so at the defaults a pass-out lasts about 78 seconds.")
            .defineInRange("sleepPassOutExitThreshold", 5.0D, 0.0D, 100.0D);

    // --- Dream system (Phase 10b) ---

    public static final ModConfigSpec.IntValue DREAM_INITIAL_DELAY_MIN_SECONDS = BUILDER
            .comment("Minimum wait, in seconds, after lying down before the first queued dream starts.",
                    "Getting out of bed during this wait cancels the attempt without consuming the queue.")
            .defineInRange("dreamInitialDelayMinSeconds", 30, 0, 100000);

    public static final ModConfigSpec.IntValue DREAM_INITIAL_DELAY_MAX_SECONDS = BUILDER
            .comment("Maximum wait, in seconds, before a queued dream starts. The actual delay is rolled",
                    "uniformly between the min and this.")
            .defineInRange("dreamInitialDelayMaxSeconds", 120, 0, 100000);

    public static final ModConfigSpec.DoubleValue DREAM_CHAIN_IMMEDIATE_CHANCE = BUILDER
            .comment("When a dream ends with more still queued and the player is still in bed, the chance the",
                    "next one chains immediately. On failure another random delay is rolled instead.")
            .defineInRange("dreamChainImmediateChance", 0.5D, 0.0D, 1.0D);

    // --- FTB Quests integration (Phase 11a) ---

    public static final ModConfigSpec.ConfigValue<String> QUEST_VISIBILITY_GATE_ID = BUILDER
            .comment("Hex id of the FTB Quests \"gate\" quest our code completes/resets per player to control what",
                    "quest content they can see. Author the quest in the in-game editor, copy its id (the 16-digit",
                    "hex code string shown in the editor, e.g. 3A7F10C2B4D5E608) and paste it here.",
                    "Empty = no gate wired. NOTE: ids are assigned at creation - deleting and re-making the quest",
                    "produces a new id and this value must be re-pasted.")
            .define("questVisibilityGateId", "");

    // --- Infected variants & healthy stats/branches (Phase 11) ---
    //
    // Quest ids are stored as the 16-digit HEX "code string" the FTB Quests editor shows and copies,
    // exactly like questVisibilityGateId above - that is the form a user actually has in hand. An empty
    // entry parses to FTB's invalid id 0L, which every consumer treats as "not wired yet", so the
    // shipped defaults are inert until real content exists and the ids are pasted in.

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> INFECTED_VARIANT_WEIGHTS = BUILDER
            .comment("Relative weights for the three infected variants (3 entries, indexed 0..2), rolled once",
                    "when a player is granted infected-side access. Equal by default. A weight of 0 disables",
                    "that variant; if every weight is 0 the roll falls back to a uniform pick.")
            .defineList("infectedVariantWeights", () -> List.of(1.0D, 1.0D, 1.0D),
                    value -> value instanceof Double weight && weight >= 0.0D);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> INFECTED_VARIANT_DREAM_IDS = BUILDER
            .comment("Dream script id queued for each infected variant (3 entries, indexed 0..2) at the moment",
                    "the variant is assigned. A dream id is the file name, minus .json, of a script under",
                    "data/<namespace>/dreams/. PLACEHOLDER DEFAULTS: these scripts do not exist yet, so the",
                    "queued dream is skipped with a warning until the real reveal dreams are authored.",
                    "An empty entry queues nothing at all.")
            .defineList("infectedVariantDreamIds",
                    () -> List.of("variant_reveal_0", "variant_reveal_1", "variant_reveal_2"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> INFECTED_VARIANT_GATE_QUEST_IDS = BUILDER
            .comment("Hex ids of the FTB Quests \"gate\" quest for each infected variant (3 entries, indexed 0..2).",
                    "When a player is assigned variant N we force-complete entry N for that player alone, which",
                    "reveals everything depending on it - their variant's chapter. The other two are never",
                    "touched, so those chapters stay permanently hidden for them with no explicit lock needed.",
                    "Author each gate quest in the in-game editor, copy its 16-digit hex id and paste it here.",
                    "DEFAULT IS EMPTY = the invalid id 0L: no gate is completed and nothing is revealed.")
            .defineList("infectedVariantGateQuestIds", () -> List.of("", "", ""),
                    value -> value instanceof String);

    public static final ModConfigSpec.IntValue HEALTHY_STAT_MIN = BUILDER
            .comment("Low end (inclusive) of the independent roll for each of the three healthy-side stats",
                    "(Intelligence, Strength, Creativity), made once when the book grants healthy-side access.")
            .defineInRange("healthyStatMin", 1, 0, 1_000_000);

    public static final ModConfigSpec.IntValue HEALTHY_STAT_MAX = BUILDER
            .comment("High end (inclusive) of each healthy-side stat roll. Values below healthyStatMin are",
                    "clamped up to it at roll time, so a misconfigured pair degrades to a fixed value.")
            .defineInRange("healthyStatMax", 10, 0, 1_000_000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HEALTHY_BRANCH_GATE_QUEST_IDS = BUILDER
            .comment("Hex ids of the FTB Quests \"gate\" quest for each healthy branch (4 entries, indexed 0..3).",
                    "Completed for that player alone when they pick the branch, exactly as the variant gates are.",
                    "DEFAULT IS EMPTY = the invalid id 0L: no gate is completed and nothing is revealed.")
            .defineList("healthyBranchGateQuestIds", () -> List.of("", "", "", ""),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HEALTHY_BRANCH_STAT_PREFERENCES = BUILDER
            .comment("The two stats each healthy branch favours (4 entries, indexed 0..3), shown on the branch",
                    "buttons so a player can weigh their rolled stats against the options. Format per entry:",
                    "\"STAT:DIRECTION,STAT:DIRECTION\" where STAT is INTELLIGENCE, STRENGTH or CREATIVITY and",
                    "DIRECTION is HIGH or LOW. These are advisory labels only - nothing enforces them.")
            .defineList("healthyBranchStatPreferences", () -> List.of(
                            "INTELLIGENCE:HIGH,CREATIVITY:HIGH",
                            "STRENGTH:HIGH,INTELLIGENCE:LOW",
                            "CREATIVITY:HIGH,STRENGTH:LOW",
                            "INTELLIGENCE:HIGH,STRENGTH:HIGH"),
                    value -> value instanceof String);

    /** One entry of a 3- or 4-wide indexed list config; empty string when the index is out of range. */
    public static String getIndexed(ModConfigSpec.ConfigValue<List<? extends String>> list, int index) {
        List<? extends String> values = list.get();
        return index >= 0 && index < values.size() ? values.get(index) : "";
    }

    /** Per-day mole chance for a symptom day number (6..10); returns 0 for days 1-5 or out-of-range. */
    public static double getMoleChanceForDay(int dayNumber) {
        List<? extends Double> table = MOLE_CHANCE_PER_DAY.get();
        int index = dayNumber - 6;
        return index >= 0 && index < table.size() ? table.get(index) : 0.0D;
    }

    public static int getWeaknessAmplifierForPhase(int moonPhase) {
        List<? extends Integer> table = WEAKNESS_AMPLIFIER_BY_PHASE.get();
        return moonPhase >= 0 && moonPhase < table.size() ? table.get(moonPhase) : 0;
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
