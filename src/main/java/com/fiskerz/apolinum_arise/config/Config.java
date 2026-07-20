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
            .comment("Damage multiplier applied to hostile mobs while Blood Moon is active.")
            .defineInRange("mobDamageMultiplier", 1.2D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue MOB_HEALTH_MULTIPLIER = BUILDER
            .comment("Max-health multiplier applied to hostile mobs while Blood Moon is active.")
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

    public static int getWeaknessAmplifierForPhase(int moonPhase) {
        List<? extends Integer> table = WEAKNESS_AMPLIFIER_BY_PHASE.get();
        return moonPhase >= 0 && moonPhase < table.size() ? table.get(moonPhase) : 0;
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
