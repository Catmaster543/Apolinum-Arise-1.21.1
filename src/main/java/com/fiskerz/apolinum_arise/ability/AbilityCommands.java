package com.fiskerz.apolinum_arise.ability;

import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindEvents;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Op-only tooling for the ability system.
 *
 * <pre>
 *   /apolinumability list [player]        - every registered ability, and this player's state for each
 *   /apolinumability unlock &lt;id&gt; [player] - force-unlock, bypassing the prerequisite
 *   /apolinumability revoke &lt;id&gt; [player] - take one back, so a grant can be re-tested
 *   /apolinumability grantwand [player]   - the Hive-Mind drop without farming a 0.1% chance
 * </pre>
 *
 * <p>{@code grantwand} deliberately runs the real {@code HiveMindEvents.grant}, wand and unlock together,
 * so what a test exercises is the same code path the drop uses.
 */
public final class AbilityCommands {
    private AbilityCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("apolinumability")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> list(ctx, net.minecraft.commands.arguments.EntityArgument
                                        .getPlayer(ctx, "player")))))
                .then(Commands.literal("unlock")
                        .then(abilityIdArgument(true)))
                .then(Commands.literal("revoke")
                        .then(abilityIdArgument(false)))
                .then(Commands.literal("grantwand")
                        .executes(ctx -> grantWand(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> grantWand(ctx, net.minecraft.commands.arguments.EntityArgument
                                        .getPlayer(ctx, "player"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            abilityIdArgument(boolean unlock) {
        return Commands.argument("ability", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Abilities.all().stream().map(Ability::id), builder))
                .executes(ctx -> setUnlocked(ctx, ctx.getSource().getPlayerOrException(), unlock))
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> setUnlocked(ctx, net.minecraft.commands.arguments.EntityArgument
                                .getPlayer(ctx, "player"), unlock)));
    }

    private static int list(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        StringBuilder text = new StringBuilder("Abilities for ")
                .append(player.getGameProfile().getName()).append(':');
        if (Abilities.all().isEmpty()) {
            text.append("\n  (none registered)");
        }
        for (Ability ability : Abilities.all()) {
            text.append("\n  ").append(ability.id())
                    .append("\n    for: ").append(ability.describeRestriction())
                    .append(" | applies to them: ").append(ability.appliesTo(player))
                    .append("\n    needs: ").append(ability.unlockCondition().describe())
                    .append(" | met: ").append(ability.unlockCondition().isMet(player))
                    .append("\n    UNLOCKED: ").append(AbilityManager.hasAbility(player, ability.id()));
        }
        String message = text.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setUnlocked(CommandContext<CommandSourceStack> context, ServerPlayer player,
                                   boolean unlock) {
        String abilityId = StringArgumentType.getString(context, "ability");
        boolean changed = unlock
                ? AbilityManager.unlockAbility(player, abilityId)
                : AbilityManager.revokeAbility(player, abilityId);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s '%s' for %s: %s", unlock ? "Unlock" : "Revoke", abilityId,
                player.getGameProfile().getName(),
                changed ? "done" : "no change (already in that state, or unknown ability)")), true);
        return changed ? 1 : 0;
    }

    private static int grantWand(CommandContext<CommandSourceStack> context, ServerPlayer player)
            throws CommandSyntaxException {
        HiveMindEvents.grant(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "Gave " + player.getGameProfile().getName() + " the Hive-Mind wand and unlocked the ability "
                        + "(the same call the drop makes)."), true);
        return 1;
    }
}
