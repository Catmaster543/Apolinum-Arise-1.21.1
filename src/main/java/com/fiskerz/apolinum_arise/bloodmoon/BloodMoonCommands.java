package com.fiskerz.apolinum_arise.bloodmoon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BloodMoonCommands {
    private BloodMoonCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("bloodmoon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("force")
                        .then(Commands.literal("start").executes(context -> {
                            BloodMoonEvents.forceStart(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.translatable("message.apolinumarise.bloodmoon.forced_start"), false);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(context -> {
                            BloodMoonEvents.forceStop(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.translatable("message.apolinumarise.bloodmoon.forced_stop"), false);
                            return 1;
                        })))
                .then(Commands.literal("chance")
                        .executes(context -> {
                            double chance = BloodMoonEvents.getCurrentChance(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.translatable("message.apolinumarise.bloodmoon.chance", chance), false);
                            return 1;
                        })
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D))
                                        .executes(context -> {
                                            double value = DoubleArgumentType.getDouble(context, "value");
                                            BloodMoonEvents.setCurrentChance(context.getSource().getServer(), value);
                                            context.getSource().sendSuccess(() -> Component.translatable("message.apolinumarise.bloodmoon.chance_set", BloodMoonEvents.getCurrentChance(context.getSource().getServer())), false);
                                            return 1;
                                        })))));
    }
}