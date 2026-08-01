package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.downed.DownedManager;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DownedReviveInputHandler {
    private DownedReviveInputHandler() {}

    public static void handle(DownedReviveInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer reviver) {
                DownedManager.handleReviveInput(reviver, payload.targetEntityId(), reviver.level().getGameTime());
            }
        });
    }
}
