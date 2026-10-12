package com.fiskerz.apolinum_arise.ability;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.Utf8String;
import net.minecraft.network.codec.StreamCodec;

/**
 * The set of ability ids a player has unlocked, persisted as a NeoForge attachment.
 *
 * <p>Stored as plain ids rather than as {@link Ability} references so the save survives an ability being
 * renamed out of the registry: an unknown id simply never matches anything and is ignored, instead of
 * breaking the load. Order is kept (LinkedHashSet) purely so debug readouts are stable.
 */
public record AbilityData(Set<String> unlocked) {
    public static final AbilityData NONE = new AbilityData(Set.of());

    public static final Codec<AbilityData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of())
                    .forGetter(data -> List.copyOf(data.unlocked()))
    ).apply(instance, unlocked -> new AbilityData(new LinkedHashSet<>(unlocked))));

    /** Self-only sync (Phase 13), so the client knows whether to report its attack key for Strong Punch. */
    public static final StreamCodec<ByteBuf, AbilityData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityData decode(ByteBuf buffer) {
            int count = VarInt.read(buffer);
            Set<String> unlocked = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) {
                unlocked.add(Utf8String.read(buffer, 256));
            }
            return new AbilityData(Set.copyOf(unlocked));
        }

        @Override
        public void encode(ByteBuf buffer, AbilityData value) {
            VarInt.write(buffer, value.unlocked().size());
            for (String abilityId : value.unlocked()) {
                Utf8String.write(buffer, abilityId, 256);
            }
        }
    };

    public boolean has(String abilityId) {
        return unlocked.contains(abilityId);
    }

    /** Returns this instance unchanged when the ability was already unlocked, so callers can detect a no-op. */
    public AbilityData with(String abilityId) {
        if (unlocked.contains(abilityId)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(unlocked);
        next.add(abilityId);
        return new AbilityData(Set.copyOf(next));
    }

    /** Debug only - abilities are permanent in normal play. */
    public AbilityData without(String abilityId) {
        if (!unlocked.contains(abilityId)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(unlocked);
        next.remove(abilityId);
        return new AbilityData(Set.copyOf(next));
    }
}
