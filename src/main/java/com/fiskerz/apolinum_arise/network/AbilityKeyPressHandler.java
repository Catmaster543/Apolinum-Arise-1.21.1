package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.ability.AbilityManager;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AbilityKeyPressHandler {
    private AbilityKeyPressHandler() {}

    public static void handle(AbilityKeyPressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AbilityManager.onKeybindSlotPressed(player, payload.slot());
            }
        });
    }
}
