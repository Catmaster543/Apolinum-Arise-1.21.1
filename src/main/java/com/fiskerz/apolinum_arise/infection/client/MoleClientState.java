package com.fiskerz.apolinum_arise.infection.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.infection.MoleData;

/**
 * Client-side cache of every tracked player's moles, keyed by entity id and fed by
 * {@code MoleSyncPayload}. The mole render layer reads it for whichever player it is drawing, so a
 * player sees other players' moles, not only their own.
 */
public final class MoleClientState {
    private MoleClientState() {}

    private static final Map<Integer, List<MoleData>> MOLES = new ConcurrentHashMap<>();

    public static void set(int entityId, List<MoleData> moles) {
        if (moles == null || moles.isEmpty()) {
            MOLES.remove(entityId);
        } else {
            MOLES.put(entityId, List.copyOf(moles));
        }
    }

    public static List<MoleData> get(int entityId) {
        return MOLES.getOrDefault(entityId, List.of());
    }

    public static void remove(int entityId) {
        MOLES.remove(entityId);
    }

    public static void clear() {
        MOLES.clear();
    }
}
