package com.fiskerz.apolinum_arise.dream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player dream state (Phase 10b), persisted as a NeoForge attachment. Server-only: nothing about the
 * queue needs to reach the client, since playback is driven entirely server-side.
 *
 * <p>{@code queue} is the pending dream ids in order. {@code receivedBroadcasts} records which
 * category broadcasts this player has already been given, keyed by {@link DreamBroadcasts#key}, so the
 * same broadcast is never handed out twice even if the player changes category and changes back.
 */
public record DreamData(List<String> queue, Set<String> receivedBroadcasts) {
    public static final DreamData EMPTY = new DreamData(List.of(), Set.of());

    public static final Codec<DreamData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("queue", List.of()).forGetter(DreamData::queue),
            Codec.STRING.listOf().optionalFieldOf("receivedBroadcasts", List.of())
                    .forGetter(data -> List.copyOf(data.receivedBroadcasts()))
    ).apply(instance, (queue, received) -> new DreamData(List.copyOf(queue), new LinkedHashSet<>(received))));

    public boolean hasQueued() {
        return !queue.isEmpty();
    }

    /** Append a dream id. Duplicates are allowed: queueing the same dream twice plays it twice. */
    public DreamData withQueued(String dreamId) {
        List<String> next = new ArrayList<>(queue);
        next.add(dreamId);
        return new DreamData(List.copyOf(next), receivedBroadcasts);
    }

    /** Remove and return nothing - the caller reads {@link #queue()} first. Pops the head. */
    public DreamData withHeadPopped() {
        if (queue.isEmpty()) {
            return this;
        }
        return new DreamData(List.copyOf(queue.subList(1, queue.size())), receivedBroadcasts);
    }

    public boolean hasReceived(String broadcastKey) {
        return receivedBroadcasts.contains(broadcastKey);
    }

    public DreamData withReceived(String broadcastKey) {
        Set<String> next = new LinkedHashSet<>(receivedBroadcasts);
        next.add(broadcastKey);
        return new DreamData(queue, Set.copyOf(next));
    }
}
