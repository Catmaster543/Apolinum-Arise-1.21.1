package com.fiskerz.apolinum_arise.mosquito;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class MosquitoCommands {
    private MosquitoCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mosquito")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("forcespawn").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    MosquitoEntity mosquito = MosquitoSpawner.forceSpawnAt(source.getLevel(), source.getPosition());
                    if (mosquito == null) {
                        source.sendFailure(Component.translatable("message.apolinumarise.mosquito.forcespawn_failed"));
                        return 0;
                    }
                    source.sendSuccess(() -> Component.translatable("message.apolinumarise.mosquito.forcespawn",
                            mosquito.blockPosition().toShortString()), true);
                    return 1;
                })));
    }
}
