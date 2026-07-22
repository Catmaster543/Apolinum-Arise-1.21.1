package com.fiskerz.apolinum_arise.infection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player infection state machine, stored as a NeoForge data attachment.
 * States: clean -> incubating (from a bite roll) -> infected (after the incubation days elapse).
 * Purely internal in Patch 2 - {@code infected} has no visible consequence yet beyond gating
 * Blood Moon susceptibility.
 */
public record InfectionData(boolean incubating, int infectionStartDay, boolean infected) {
    public static final InfectionData NONE = new InfectionData(false, 0, false);

    public static final Codec<InfectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("incubating", false).forGetter(InfectionData::incubating),
            Codec.INT.optionalFieldOf("infectionStartDay", 0).forGetter(InfectionData::infectionStartDay),
            Codec.BOOL.optionalFieldOf("infected", false).forGetter(InfectionData::infected)
    ).apply(instance, InfectionData::new));

    public InfectionData beginIncubating(int currentDay) {
        return new InfectionData(true, currentDay, false);
    }

    public InfectionData becomeInfected() {
        return new InfectionData(false, infectionStartDay, true);
    }
}
