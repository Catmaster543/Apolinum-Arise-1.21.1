package com.fiskerz.apolinum_arise.infection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /infection status <player>} and {@code /infection set <player> <healthy|infected|incubating <1-10>>}
 * - both op-level (permission 2). The set subcommands apply real mechanical state but suppress the normal
 * flavor/actionbar messages, since it's an admin tool.
 */
public final class InfectionCommands {
    private InfectionCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("infection")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    Component status = InfectionSymptoms.statusFor(target);
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "message.apolinumarise.infection.status.report",
                                            target.getDisplayName(), status), false);
                                    return 1;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("healthy").executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    InfectionSymptoms.setHealthy(target);
                                    return report(context, target);
                                }))
                                .then(Commands.literal("infected").executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    InfectionSymptoms.setInfected(target);
                                    return report(context, target);
                                }))
                                .then(Commands.literal("incubating")
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, DayProfile.maxDay()))
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    int day = IntegerArgumentType.getInteger(context, "day");
                                                    InfectionSymptoms.setIncubating(target, day);
                                                    return report(context, target);
                                                }))))));
    }

    private static int report(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
        Component status = InfectionSymptoms.statusFor(target);
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.apolinumarise.infection.set", target.getDisplayName(), status), true);
        return 1;
    }
}
