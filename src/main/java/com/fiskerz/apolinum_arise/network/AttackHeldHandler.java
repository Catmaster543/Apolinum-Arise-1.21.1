package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunchManager;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AttackHeldHandler {
    private AttackHeldHandler() {}

    public static void handle(AttackHeldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StrongPunchManager.onAttackHeldReported(player, payload.held());
            }
        });
    }
}
