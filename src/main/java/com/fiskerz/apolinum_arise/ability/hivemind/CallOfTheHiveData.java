package com.fiskerz.apolinum_arise.ability.hivemind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persisted Call of the Hive state: just the cooldown deadline, in game ticks (the same clock in every
 * dimension). Kept as a deadline rather than a countdown so it needs no ticking, and so time a player spends
 * logged out on a running server counts - the same choice Digging In's {@code nextAllowedGameTime} makes.
 */
public record CallOfTheHiveData(long nextAllowedGameTime) {
    public static final CallOfTheHiveData READY = new CallOfTheHiveData(0L);

    public static final Codec<CallOfTheHiveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("nextAllowedGameTime", 0L).forGetter(CallOfTheHiveData::nextAllowedGameTime)
    ).apply(instance, CallOfTheHiveData::new));

    public boolean readyAt(long now) {
        return now >= nextAllowedGameTime;
    }

    public long remainingTicksAt(long now) {
        return Math.max(0L, nextAllowedGameTime - now);
    }
}
