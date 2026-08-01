package com.fiskerz.apolinum_arise.infection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One mole on an infected player. {@code slot} indexes {@link MoleSlot}; {@code size} is the current
 * rendered size in player-model pixels; {@code baseSize}/{@code cap} are server-only growth bookkeeping.
 *
 * <p>{@link #CODEC} persists the full record; {@link #STREAM_CODEC} syncs only what a client needs to
 * render (slot + current size), reconstructing the server-only fields as harmless placeholders.
 */
public record MoleData(int slot, float baseSize, float size, float cap) {
    public static final Codec<MoleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(MoleData::slot),
            Codec.FLOAT.fieldOf("baseSize").forGetter(MoleData::baseSize),
            Codec.FLOAT.fieldOf("size").forGetter(MoleData::size),
            Codec.FLOAT.fieldOf("cap").forGetter(MoleData::cap)
    ).apply(instance, MoleData::new));

    // Network view: slot + current size only. baseSize/cap are irrelevant on the client.
    public static final StreamCodec<ByteBuf, MoleData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MoleData::slot,
            ByteBufCodecs.FLOAT, MoleData::size,
            (slot, size) -> new MoleData(slot, size, size, size));

    public MoleData grownTo(float newSize) {
        return new MoleData(slot, baseSize, newSize, cap);
    }
}
