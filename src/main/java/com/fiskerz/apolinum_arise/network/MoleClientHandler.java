package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.infection.client.MoleClientState;

import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client-only handling of MoleSyncPayload. Referenced only via a lambda in ModNetworking so it (and
// the client state it touches) never classloads on a dedicated server.
public final class MoleClientHandler {
    private MoleClientHandler() {}

    public static void handle(MoleSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MoleClientState.set(payload.entityId(), payload.moles()));
    }
}
