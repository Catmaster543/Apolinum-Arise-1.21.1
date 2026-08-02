package com.fiskerz.apolinum_arise.infection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player infection state machine, stored as a NeoForge data attachment.
 * States: clean -> incubating (from a bite roll) -> infected (after the incubation days elapse).
 *
 * <p>{@code biteBar} (Phase 8) is the 0-100 charge an INFECTED player builds toward being able to bite a
 * downed healthy player and spread the infection. It only fills while infected, persists across relog, and
 * syncs to the owning client so the HUD bar can read it. It is meaningless (stays 0) while healthy or
 * incubating.
 */
public record InfectionData(boolean incubating, int infectionStartDay, boolean infected, float biteBar) {
    public static final InfectionData NONE = new InfectionData(false, 0, false, 0.0F);

    public static final Codec<InfectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("incubating", false).forGetter(InfectionData::incubating),
            Codec.INT.optionalFieldOf("infectionStartDay", 0).forGetter(InfectionData::infectionStartDay),
            Codec.BOOL.optionalFieldOf("infected", false).forGetter(InfectionData::infected),
            Codec.FLOAT.optionalFieldOf("biteBar", 0.0F).forGetter(InfectionData::biteBar)
    ).apply(instance, InfectionData::new));

    // Synced to the owning client only (Phase 6) so client-side screen interception can read isInfected;
    // Phase 8 adds biteBar so the owner's HUD bar reflects the server-authoritative charge.
    public static final StreamCodec<ByteBuf, InfectionData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, InfectionData::incubating,
            ByteBufCodecs.VAR_INT, InfectionData::infectionStartDay,
            ByteBufCodecs.BOOL, InfectionData::infected,
            ByteBufCodecs.FLOAT, InfectionData::biteBar,
            InfectionData::new);

    public InfectionData beginIncubating(int currentDay) {
        return new InfectionData(true, currentDay, false, 0.0F);
    }

    public InfectionData becomeInfected() {
        return new InfectionData(false, infectionStartDay, true, 0.0F);
    }

    /** Same state, with the bite bar set to a new (clamped 0-100) value. */
    public InfectionData withBiteBar(float value) {
        float clamped = Math.max(0.0F, Math.min(100.0F, value));
        return new InfectionData(incubating, infectionStartDay, infected, clamped);
    }

    /** Clean = neither incubating nor infected (the only valid bite target state). */
    public boolean healthy() {
        return !incubating && !infected;
    }
}
