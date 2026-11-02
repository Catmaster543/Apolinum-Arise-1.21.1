package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.ability.Abilities;
import com.fiskerz.apolinum_arise.ability.Ability;
import com.fiskerz.apolinum_arise.ability.AbilityManager;
import com.fiskerz.apolinum_arise.ability.AbilitySide;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.quests.QuestGates;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Op-only readout for the Phase 11 assignments, so "did the variant/stats/branch actually fire, and did the
 * gate open?" is answerable in-game rather than only in the log.
 *
 * <pre>
 *   /apolinumskills profile [player]  - access, variant, stats, branch, and each configured gate's state
 *   /apolinumskills reset [player]    - wipe the profile so the one-shot assignments can be re-observed
 *   /apolinumskills setvariant <0-2> [player]
 *                                     - switch an infected player's variant, with its dreams and gate
 * </pre>
 *
 * <p>{@code reset} exists purely so a test can be repeated: every assignment in this phase is deliberately
 * one-shot, which otherwise makes "force-infect, trigger a Blood Moon, watch the variant land" a
 * once-per-player experiment. It clears the permanent choices, not the access flags.
 */
public final class SkillCommands {
    private SkillCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("apolinumskills")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("profile")
                        .executes(ctx -> profile(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> profile(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("setvariant")
                        .then(Commands.argument("variant", IntegerArgumentType.integer(0, InfectedVariants.COUNT - 1))
                                .executes(ctx -> setVariant(ctx, ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "variant")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setVariant(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "variant")))))));
    }

    private static int profile(CommandContext<CommandSourceStack> context, ServerPlayer player)
            throws CommandSyntaxException {
        SkillProfileData profile = SkillLogic.profile(player);
        StringBuilder text = new StringBuilder(player.getGameProfile().getName()).append(":\n");
        text.append("  access: healthy=").append(SkillLogic.hasHealthyAccess(player))
                .append(" infected=").append(SkillLogic.hasInfectedAccess(player)).append('\n');

        text.append("  infected variant: ");
        if (profile.hasInfectedVariant()) {
            int variant = profile.infectedVariant();
            text.append(variant)
                    .append("\n    reveal dream: ")
                    .append(describe(Config.getIndexed(Config.INFECTED_VARIANT_DREAM_IDS, variant)))
                    .append("\n    gate: ")
                    .append(QuestGates.describeGate(player,
                            Config.getIndexed(Config.INFECTED_VARIANT_GATE_QUEST_IDS, variant)));
        } else {
            text.append("unassigned (assigned at the first Blood Moon after infection completes)");
        }

        text.append("\n  stats: ");
        if (profile.statsAssigned()) {
            text.append("INT=").append(profile.intelligence())
                    .append(" STR=").append(profile.strength())
                    .append(" CRE=").append(profile.creativity());
        } else {
            text.append("unrolled (rolled when the book grants healthy access)");
        }

        text.append("\n  healthy branch: ");
        if (profile.hasHealthyBranch()) {
            int branch = profile.healthyBranch();
            text.append(branch).append("\n    gate: ").append(QuestGates.describeGate(player,
                    Config.getIndexed(Config.HEALTHY_BRANCH_GATE_QUEST_IDS, branch)));
        } else {
            text.append(SkillLogic.needsBranchChoice(player)
                    ? "not chosen - the choice screen is due on their next skill-screen open"
                    : "not chosen (and they have no healthy access, so nothing is due)");
        }

        String message = text.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE);
        context.getSource().sendSuccess(() -> Component.literal(
                "Cleared " + player.getGameProfile().getName() + "'s variant, stats and branch. Access flags "
                        + "are untouched - the infected assignment re-runs at the next Blood Moon while "
                        + "infected, and the branch choice on their next skill-screen open.\n"
                        + "NOTE: this does NOT un-complete their quest gates, so previously revealed chapters "
                        + "stay visible. Use /apolinumquests resetgates for a fully ungated state."), true);
        return 1;
    }

    /**
     * Put an infected player on a chosen variant, with everything the real roll would have done for it -
     * see {@link InfectedVariants#switchTo}. Refused for a player who is not infected, because no infected
     * ability applies to them and the variant would just be a number nobody reads.
     */
    private static int setVariant(CommandContext<CommandSourceStack> context, ServerPlayer player, int variant) {
        String name = player.getGameProfile().getName();
        if (!InfectionLogic.isInfected(player)) {
            context.getSource().sendFailure(Component.literal(name + " is not infected, so no infected variant "
                    + "can apply to them. Run /infection set " + name + " infected first."));
            return 0;
        }

        InfectedVariants.Switch result = InfectedVariants.switchTo(player, variant);
        if (!result.changed()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    name + " already holds infected variant " + variant + " - nothing changed."), false);
            return 0;
        }

        StringBuilder text = new StringBuilder(name).append(": infected variant ")
                .append(InfectedVariants.isValidIndex(result.previous()) ? String.valueOf(result.previous()) : "unassigned")
                .append(" -> ").append(variant);
        if (InfectedVariants.isValidIndex(result.previous())) {
            text.append("\n  old gate: ").append(result.gateObjectsClosed() > 0
                    ? "closed (" + result.gateObjectsClosed() + " quest object(s) reset)"
                    : "nothing to close (no usable gate id configured for variant " + result.previous() + ")");
            if (!result.unqueuedDreams().isEmpty()) {
                text.append("\n  took back unplayed dream(s): ").append(String.join(", ", result.unqueuedDreams()));
            }
        }
        text.append("\n  new gate: ").append(result.gate() == null
                ? "not attempted" : result.gate().explain("infected variant " + variant));
        text.append("\n  queued: ").append(result.queuedDreams().isEmpty()
                ? "nothing" : String.join(", ", result.queuedDreams()) + " (plays on their next nap)");

        text.append("\n  abilities:");
        for (Ability ability : Abilities.all()) {
            if (ability.side() != AbilitySide.INFECTED) {
                continue;
            }
            String state = AbilityManager.canUse(player, ability.id()) ? "ACTIVE"
                    : AbilityManager.hasAbility(player, ability.id()) ? "dormant (earned, other variant)"
                    : "not earned";
            text.append("\n    ").append(ability.id()).append(" [").append(ability.describeRestriction())
                    .append("]: ").append(state);
        }
        if (!SkillLogic.hasInfectedAccess(player)) {
            text.append("\n  NOTE: no infected-side access yet - the skill screen opens at the next Blood Moon, "
                    + "which will keep this variant rather than rolling. Abilities apply regardless.");
        }

        String message = text.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static String describe(String value) {
        return value.isBlank() ? "none configured" : value;
    }
}
