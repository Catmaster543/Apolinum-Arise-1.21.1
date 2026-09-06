package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.skill.SkillLogic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ChooseBranchHandler {
    private ChooseBranchHandler() {}

    public static void handle(ChooseBranchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && SkillLogic.chooseHealthyBranch(player, payload.branch())) {
                // Confirm on the actionbar: the choice is one-time and the screen closes immediately after,
                // so without this there is nothing telling the player it actually took.
                player.displayClientMessage(Component.translatable("message.apolinumarise.skill.branch_chosen",
                        com.fiskerz.apolinum_arise.skill.HealthyBranches.displayName(payload.branch())), true);
            }
        });
    }
}
