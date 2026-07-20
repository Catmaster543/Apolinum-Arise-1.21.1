package com.fiskerz.apolinum_arise.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Central payload registration; listener added to the mod event bus in Apolinumarise.
public final class ModNetworking {
    private ModNetworking() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        // Lambda (not a method reference) so the client-only handler class is never resolved on a dedicated server.
        registrar.playToClient(AwakeningSoundPayload.TYPE, AwakeningSoundPayload.STREAM_CODEC,
                (payload, context) -> AwakeningSoundClientHandler.handle(payload, context));
        registrar.playToClient(BloodMoonSyncPayload.TYPE, BloodMoonSyncPayload.STREAM_CODEC,
            (payload, context) -> BloodMoonClientHandler.handle(payload, context));
    }
}
