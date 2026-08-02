package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.infection.BiteBar;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class BiteAttemptHandler {
    private BiteAttemptHandler() {}

    public static void handle(BiteAttemptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer biter) {
                BiteBar.attemptBite(biter, payload.targetEntityId());
            }
        });
    }
}
