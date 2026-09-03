package com.fiskerz.apolinum_arise.sleep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player sleep state (Phase 10a), stored as a persisted + synced NeoForge attachment. Applies only to
 * players who are NOT fully infected; an infected player keeps whatever value is frozen here but nothing
 * ticks it and no bar is drawn.
 *
 * <p>{@code sleepBar} is 0-100 and starts full. {@code ticksAtZero} is the CONTINUOUS time spent at 0%
 * (any refill above zero resets it). {@code passedOut} is the exhaustion incapacitation - it is persisted
 * precisely so relogging is no escape - and {@code poseVariant}/{@code bodyYaw} let the passed-out body
 * reuse the Phase 7 downed pose rendering (which needs a variant to draw and a frozen facing so the
 * corpse does not spin with the camera).
 *
 * <p>Synced to ALL tracking clients, not just the owner: other players must see the passed-out pose, the
 * same way {@code DownedData} is broadcast. Writes are throttled to ~1/s by {@link SleepManager}, so the
 * continuously-changing bar does not produce a packet every tick.
 */
public record SleepData(float sleepBar, int ticksAtZero, boolean passedOut, int poseVariant, float bodyYaw,
                        boolean healthy) {
    /** A fresh player starts fully rested and clean. */
    public static final SleepData FULL = new SleepData(100.0F, 0, false, 0, 0.0F, true);

    public static final Codec<SleepData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("sleepBar", 100.0F).forGetter(SleepData::sleepBar),
            Codec.INT.optionalFieldOf("ticksAtZero", 0).forGetter(SleepData::ticksAtZero),
            Codec.BOOL.optionalFieldOf("passedOut", false).forGetter(SleepData::passedOut),
            Codec.INT.optionalFieldOf("poseVariant", 0).forGetter(SleepData::poseVariant),
            Codec.FLOAT.optionalFieldOf("bodyYaw", 0.0F).forGetter(SleepData::bodyYaw),
            Codec.BOOL.optionalFieldOf("healthy", true).forGetter(SleepData::healthy)
    ).apply(instance, SleepData::new));

    public static final StreamCodec<ByteBuf, SleepData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SleepData::sleepBar,
            ByteBufCodecs.VAR_INT, SleepData::ticksAtZero,
            ByteBufCodecs.BOOL, SleepData::passedOut,
            ByteBufCodecs.VAR_INT, SleepData::poseVariant,
            ByteBufCodecs.FLOAT, SleepData::bodyYaw,
            ByteBufCodecs.BOOL, SleepData::healthy,
            SleepData::new);

    /**
     * The live bar/clock values, keeping the pass-out presentation fields as they are. {@code healthyNow}
     * is refreshed on every flush (rather than snapshotted at pass-out) so a biter's client always sees the
     * target's current eligibility - infection itself is only synced to its owner.
     */
    public SleepData with(float bar, int zeroTicks, boolean passedOutNow, int variant, float yaw, boolean healthyNow) {
        return new SleepData(Math.max(0.0F, Math.min(100.0F, bar)), Math.max(0, zeroTicks), passedOutNow,
                variant, yaw, healthyNow);
    }
}
