package com.fiskerz.apolinum_arise.config;

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

    // Future systems (blood moon cycle, infection, mosquito, ...) define their fields here.
    // All fields must be defined before SPEC is built below.

    public static final ModConfigSpec SPEC = BUILDER.build();
}
