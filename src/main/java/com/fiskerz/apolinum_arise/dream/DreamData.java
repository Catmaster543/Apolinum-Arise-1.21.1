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
public record DreamData(List<String> queue, Set<String> receivedBroadcasts, Set<String> watched) {
    public static final DreamData EMPTY = new DreamData(List.of(), Set.of(), Set.of());

    public static final Codec<DreamData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("queue", List.of()).forGetter(DreamData::queue),
            Codec.STRING.listOf().optionalFieldOf("receivedBroadcasts", List.of())
                    .forGetter(data -> List.copyOf(data.receivedBroadcasts())),
            Codec.STRING.listOf().optionalFieldOf("watched", List.of())
                    .forGetter(data -> List.copyOf(data.watched()))
    ).apply(instance, (queue, received, watched) -> new DreamData(
            List.copyOf(queue), new LinkedHashSet<>(received), new LinkedHashSet<>(watched))));

    public boolean hasQueued() {
        return !queue.isEmpty();
    }

    /** Append a dream id. Duplicates are allowed: queueing the same dream twice plays it twice. */
    public DreamData withQueued(String dreamId) {
        List<String> next = new ArrayList<>(queue);
        next.add(dreamId);
        return new DreamData(List.copyOf(next), receivedBroadcasts, watched);
    }

    /** Remove and return nothing - the caller reads {@link #queue()} first. Pops the head. */
    public DreamData withHeadPopped() {
        if (queue.isEmpty()) {
            return this;
        }
        return new DreamData(List.copyOf(queue.subList(1, queue.size())), receivedBroadcasts, watched);
    }

    public boolean hasReceived(String broadcastKey) {
        return receivedBroadcasts.contains(broadcastKey);
    }

    public DreamData withReceived(String broadcastKey) {
        Set<String> next = new LinkedHashSet<>(receivedBroadcasts);
        next.add(broadcastKey);
        return new DreamData(queue, Set.copyOf(next), watched);
    }

    /**
     * Whether this dream has ever been watched through to its end (Phase 12).
     *
     * <p>Distinct from having been queued or started: an ability prerequisite is "you saw this", so only a
     * playback that reached its final tick counts. Interrupting a dream - getting out of bed, being bitten
     * - leaves the dream in neither the queue nor this set, which is deliberate: the dream was consumed but
     * not seen, and the player would need it queued again to earn the prerequisite.
     */
    public boolean hasWatched(String dreamId) {
        return watched.contains(dreamId);
    }

    /** Returns this instance unchanged when already recorded, so callers can skip a pointless setData. */
    public DreamData withWatched(String dreamId) {
        if (watched.contains(dreamId)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(watched);
        next.add(dreamId);
        return new DreamData(queue, receivedBroadcasts, Set.copyOf(next));
    }
}
