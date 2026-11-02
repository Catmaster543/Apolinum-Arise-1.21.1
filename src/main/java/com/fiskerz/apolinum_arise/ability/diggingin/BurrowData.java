package com.fiskerz.apolinum_arise.ability.diggingin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player Digging In state (Phase 14): are they burrowed, when may they next transition, and when did
 * the current burrow begin.
 *
 * <p>All three are server-authoritative. The client receives them only so it can drive movement and
 * visuals; it never decides any of them.
 *
 * <p>The state is stored as a {@code boolean} rather than the {@link BurrowState} enum because there are
 * exactly two states, which makes a boolean the whole of the information and spares a codec for a
 * two-valued enum. {@link #state()} is the API everything else uses, so the enum remains the vocabulary.
 *
 * @param burrowed              whether the player is currently underground
 * @param nextAllowedGameTime   game time before which no transition is permitted, in ticks
 * @param burrowedSinceGameTime game time the current burrow started, for the max-duration auto-eject
 */
public record BurrowData(boolean burrowed, long nextAllowedGameTime, long burrowedSinceGameTime) {

    public static final BurrowData NORMAL = new BurrowData(false, 0L, 0L);

    public static final Codec<BurrowData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("burrowed", false).forGetter(BurrowData::burrowed),
            Codec.LONG.optionalFieldOf("nextAllowedGameTime", 0L).forGetter(BurrowData::nextAllowedGameTime),
            Codec.LONG.optionalFieldOf("burrowedSinceGameTime", 0L).forGetter(BurrowData::burrowedSinceGameTime)
    ).apply(instance, BurrowData::new));

    public static final StreamCodec<ByteBuf, BurrowData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BurrowData::burrowed,
            ByteBufCodecs.VAR_LONG, BurrowData::nextAllowedGameTime,
            ByteBufCodecs.VAR_LONG, BurrowData::burrowedSinceGameTime,
            BurrowData::new);

    public BurrowState state() {
        return burrowed ? BurrowState.BURROWED : BurrowState.NORMAL;
    }

    /** Whether a transition is permitted at {@code now}. Forced exits ignore this entirely. */
    public boolean mayTransitionAt(long now) {
        return now >= nextAllowedGameTime;
    }

    /** Whole seconds still to wait at {@code now}, rounded up, for the refusal message. */
    public int secondsRemainingAt(long now) {
        long ticks = Math.max(0L, nextAllowedGameTime - now);
        return (int) ((ticks + 19L) / 20L);
    }

    /** How long the current burrow has lasted, in ticks. Meaningless when not burrowed. */
    public long burrowedTicksAt(long now) {
        return burrowed ? Math.max(0L, now - burrowedSinceGameTime) : 0L;
    }

    public BurrowData burrowedAt(long now, long nextAllowed) {
        return new BurrowData(true, nextAllowed, now);
    }

    public BurrowData surfaced(long nextAllowed) {
        return new BurrowData(false, nextAllowed, 0L);
    }
}
