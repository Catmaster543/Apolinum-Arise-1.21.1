package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.bloodmoon.client.BloodMoonClientState;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class BloodMoonClientHandler {
    private BloodMoonClientHandler() {}

    public static void handle(BloodMoonSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BloodMoonClientState.setActive(payload.active()));
    }
}