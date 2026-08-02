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
        registrar.playToClient(MoleSyncPayload.TYPE, MoleSyncPayload.STREAM_CODEC,
            (payload, context) -> MoleClientHandler.handle(payload, context));
        // Client -> server: infected player requesting their (server-enforced) restricted inventory.
        registrar.playToServer(OpenRestrictedInventoryPayload.TYPE, OpenRestrictedInventoryPayload.STREAM_CODEC,
            OpenRestrictedInventoryHandler::handle);
        // Client -> server: hold-to-revive channel input (target entity id, or -1 to break the channel).
        registrar.playToServer(DownedReviveInputPayload.TYPE, DownedReviveInputPayload.STREAM_CODEC,
            DownedReviveInputHandler::handle);
        // Client -> server: single-press bite attempt on a downed healthy target (Phase 8).
        registrar.playToServer(BiteAttemptPayload.TYPE, BiteAttemptPayload.STREAM_CODEC,
            BiteAttemptHandler::handle);
    }
}
