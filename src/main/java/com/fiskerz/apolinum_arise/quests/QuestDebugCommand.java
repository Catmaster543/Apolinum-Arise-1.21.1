package com.fiskerz.apolinum_arise.quests;

import java.util.List;

import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.skill.SkillLogic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.util.ProgressChange;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Op-only proof for Phase 11a item 3: programmatic, per-player control of what quest content a player can
 * see, driven from our own server code.
 *
 * <p><b>Important finding:</b> FTB Quests has no "set visible for player" call. {@code isVisible(TeamData)}
 * is a READ-ONLY derived query - a chapter is visible if it is not {@code alwaysInvisible} and any of its
 * quests are visible, and a quest's visibility falls out of its dependencies and hide flags. The only
 * server-side lever is therefore that player's own progress: complete or reset a "gate" quest in their
 * {@link TeamData} and everything that depends on it appears or disappears for them alone.
 *
 * <pre>
 *   /apolinumquests status &lt;hexId&gt;              - visible/started/completed for the sender
 *   /apolinumquests visibility &lt;hexId&gt; show    - force-complete the gate for the sender only
 *   /apolinumquests visibility &lt;hexId&gt; hide    - reset it again for the sender only
 *   /apolinumquests testchain create             - build the Phase 11 placeholder gated chapters
 *   /apolinumquests testchain list               - re-print the ids of the chain in the world
 *   /apolinumquests testchain remove             - delete them again
 *   /apolinumquests resetgates [player]          - un-complete every gate + clear their variant/branch
 *   /apolinumquests verifygates [player]         - check every configured gate id resolves (changes nothing)
 * </pre>
 *
 * <p>The {@code testchain} subcommands exist so Phase 11 can be tested end-to-end before any real chapter
 * content is authored: they create seven gate quests and seven gated chapters, WRITE their ids into
 * {@code infectedVariantGateQuestIds} / {@code healthyBranchGateQuestIds}, and clean up after themselves.
 * Writing the ids is what stops the config pointing at a previous, deleted generation of the chain - the
 * failure that made branch selection look like it did nothing at all.
 */
public final class QuestDebugCommand {
    private QuestDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("apolinumquests")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(QuestDebugCommand::status)))
                .then(Commands.literal("visibility")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.literal("show").executes(ctx -> setVisible(ctx, true)))
                                .then(Commands.literal("hide").executes(ctx -> setVisible(ctx, false)))))
                .then(Commands.literal("testchain")
                        .then(Commands.literal("create").executes(QuestDebugCommand::testChainCreate))
                        .then(Commands.literal("list").executes(QuestDebugCommand::testChainList))
                        .then(Commands.literal("remove").executes(QuestDebugCommand::testChainRemove)))
                .then(Commands.literal("resetgates")
                        .executes(ctx -> resetGates(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetGates(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("verifygates")
                        .executes(ctx -> verifyGates(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> verifyGates(ctx, EntityArgument.getPlayer(ctx, "player"))))));
    }

    /**
     * Check every configured gate id WITHOUT changing anything - the fast answer to "I picked a branch and
     * nothing happened". A gate whose id no longer resolves is by far the most common cause, because quest
     * ids are assigned at creation and change whenever content is deleted and re-made.
     */
    private static int verifyGates(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        List<String> variants = List.copyOf(Config.INFECTED_VARIANT_GATE_QUEST_IDS.get());
        List<String> branches = List.copyOf(Config.HEALTHY_BRANCH_GATE_QUEST_IDS.get());
        StringBuilder text = new StringBuilder("Configured gates for ")
                .append(player.getGameProfile().getName()).append(":");
        int broken = 0;
        broken += appendGateLines(text, player, variants, "infected variant");
        broken += appendGateLines(text, player, branches, "healthy branch");

        if (broken == 0) {
            text.append("\nAll ").append(variants.size() + branches.size())
                    .append(" gate(s) resolve. If a choice still reveals nothing, check that editing mode is "
                            + "off and that the content quests really depend on these gates.");
        } else {
            text.append("\n").append(broken).append(" gate(s) are unusable - assignments will succeed but "
                    + "reveal nothing. Run /apolinumquests testchain create to rebuild the placeholder chain "
                    + "and rewrite these ids, or paste the correct ids from the editor.");
        }
        String message = text.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return broken == 0 ? 1 : 0;
    }

    private static int appendGateLines(StringBuilder text, ServerPlayer player, List<String> ids, String what) {
        int broken = 0;
        for (int i = 0; i < ids.size(); i++) {
            QuestGates.GateResult result = QuestGates.checkGate(player, ids.get(i));
            if (!result.opened()) {
                broken++;
            }
            text.append("\n  ").append(what).append(' ').append(i).append(": ")
                    .append(result.opened() ? "OK - " + QuestGates.describeGate(player, ids.get(i))
                            : result.name() + " (" + (ids.get(i).isBlank() ? "<empty>" : ids.get(i)) + ")");
        }
        return broken;
    }

    // ---------------------------------------------------------------- reset

    /**
     * Put one player back to a genuinely fresh, ungated state: un-complete every variant and branch gate
     * (and whatever those gates were revealing) in their team data, and clear the variant/branch fields
     * this mod persists. Both halves are needed - the gates alone would leave them assigned but locked out,
     * and the fields alone would leave content visible for an assignment they no longer have.
     */
    private static int resetGates(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        int before = QuestGates.completedGateCount(player);
        int reset = QuestGates.resetAllGates(player);
        SkillLogic.resetAssignments(player);
        int after = QuestGates.completedGateCount(player);

        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "Reset %s: %d quest object(s) across their gates, and cleared their variant and branch.%n"
                        + "  gates completed before: %d, after: %d%s%n"
                        + "  They will be re-assigned a variant at the next Blood Moon while infected, and "
                        + "asked to pick a branch the next time they open their skills.",
                player.getGameProfile().getName(), reset, before, after,
                after == 0 ? "  (clean)" : "  (NOT CLEAN - see the log)")), true);
        return after == 0 ? 1 : 0;
    }

    // ---------------------------------------------------------------- Phase 11 placeholder content

    private static int testChainCreate(CommandContext<CommandSourceStack> context) {
        ServerQuestFile file = QuestIds.file().orElse(null);
        if (file == null) {
            return fail(context, "No server quest file loaded.");
        }
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        if (gates.isEmpty()) {
            return fail(context, "A test chain already exists. Use 'testchain list' for its ids, "
                    + "or 'testchain remove' first.");
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Created " + gates.size() + " gated placeholder chapters, saved the quest file, and wrote "
                        + "their ids into the config - no pasting needed.\n"
                        + configSnippet(gates)
                        + "\nNow RECONNECT: the quest file is only synced to clients on join, so the new "
                        + "chapters will not appear until you do."), true);
        return 1;
    }

    private static int testChainList(CommandContext<CommandSourceStack> context) {
        ServerQuestFile file = QuestIds.file().orElse(null);
        if (file == null) {
            return fail(context, "No server quest file loaded.");
        }
        List<QuestTestChain.Gate> gates = QuestTestChain.list(file);
        if (gates.isEmpty()) {
            return fail(context, "No test chain exists. Create one with 'testchain create'.");
        }
        context.getSource().sendSuccess(() -> Component.literal(configSnippet(gates)), false);
        return 1;
    }

    private static int testChainRemove(CommandContext<CommandSourceStack> context) {
        ServerQuestFile file = QuestIds.file().orElse(null);
        if (file == null) {
            return fail(context, "No server quest file loaded.");
        }
        int removed = QuestTestChain.remove(file);
        context.getSource().sendSuccess(() -> Component.literal(
                "Removed " + removed + " placeholder chapter(s). Reconnect to see the change."), true);
        return removed > 0 ? 1 : 0;
    }

    // The gates come back in config order: the three variants first, then the four branches.
    private static String configSnippet(List<QuestTestChain.Gate> gates) {
        StringBuilder text = new StringBuilder();
        for (QuestTestChain.Gate gate : gates) {
            text.append(gate.label()).append(" -> ").append(gate.codeString()).append('\n');
        }
        text.append("\ninfectedVariantGateQuestIds = [")
                .append(quoted(gates, 0, 3)).append("]\nhealthyBranchGateQuestIds = [")
                .append(quoted(gates, 3, 7)).append(']');
        return text.toString();
    }

    private static String quoted(List<QuestTestChain.Gate> gates, int from, int toExclusive) {
        StringBuilder text = new StringBuilder();
        for (int i = from; i < Math.min(toExclusive, gates.size()); i++) {
            if (i > from) {
                text.append(", ");
            }
            text.append('"').append(gates.get(i).codeString()).append('"');
        }
        return text.toString();
    }

    private static int status(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long id = QuestIds.parse(StringArgumentType.getString(context, "id"));
        if (id == QuestIds.INVALID) {
            return fail(context, "Not a valid quest id (expected the hex code string from the editor).");
        }
        TeamData data = QuestIds.teamData(player).orElse(null);
        if (data == null) {
            return fail(context, "No server quest file loaded.");
        }
        QuestObject object = QuestGatesInternal.resolve(id);
        if (object == null) {
            return fail(context, "No quest or chapter with id " + QuestIds.toCodeString(id) + ".");
        }
        String kind = object instanceof Chapter ? "chapter" : "quest";
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "[%s %s] team=%s visible=%s started=%s completed=%s",
                kind, QuestIds.toCodeString(id), data.getName(),
                object.isVisible(data), data.isStarted(object), data.isCompleted(object))), false);
        return 1;
    }

    // Drives visibility the only way FTB supports it: this player's own progress on the object.
    private static int setVisible(CommandContext<CommandSourceStack> context, boolean show) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long id = QuestIds.parse(StringArgumentType.getString(context, "id"));
        if (id == QuestIds.INVALID) {
            return fail(context, "Not a valid quest id (expected the hex code string from the editor).");
        }
        TeamData data = QuestIds.teamData(player).orElse(null);
        QuestObject object = QuestGatesInternal.resolve(id);
        if (data == null || object == null) {
            return fail(context, "No quest/chapter with id " + QuestIds.toCodeString(id) + ", or no quest file loaded.");
        }
        // reset = hide (drop the progress that was revealing dependents), complete = show.
        ProgressChange change = new ProgressChange(object, player.getUUID());
        change.setReset(!show);
        object.forceProgress(data, change);
        data.saveIfChanged();

        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s %s for %s only (team %s). Now visible=%s completed=%s",
                show ? "Completed" : "Reset", QuestIds.toCodeString(id), player.getGameProfile().getName(),
                data.getName(), object.isVisible(data), data.isCompleted(object))), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
