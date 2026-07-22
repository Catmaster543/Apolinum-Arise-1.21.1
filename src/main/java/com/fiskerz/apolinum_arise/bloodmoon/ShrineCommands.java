package com.fiskerz.apolinum_arise.bloodmoon;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class ShrineCommands {
    private ShrineCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Permission 4 (owner/console tier), above the level-2 debug commands: this is a deliberately
        // obscure, one-way switch, not a testing toggle. sendSuccess(..., false) keeps feedback to the
        // sender only - it is not broadcast to other ops or console.
        dispatcher.register(Commands.literal("shrine")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("activate").executes(context -> {
                    ServerLevel overworld = context.getSource().getServer().overworld();
                    boolean changed = ShrineGenerationState.unlock(overworld);
                    context.getSource().sendSuccess(() -> Component.translatable(changed
                            ? "message.apolinumarise.shrine.activated"
                            : "message.apolinumarise.shrine.already_activated"), false);
                    return 1;
                })));
    }
}
