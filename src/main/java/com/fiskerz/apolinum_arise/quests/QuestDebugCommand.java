package com.fiskerz.apolinum_arise.quests;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.util.ProgressChange;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
 * </pre>
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
                                .then(Commands.literal("hide").executes(ctx -> setVisible(ctx, false))))));
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
        QuestObject object = resolve(id);
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
        QuestObject object = resolve(id);
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

    private static QuestObject resolve(long id) {
        Quest quest = QuestIds.quest(id).orElse(null);
        if (quest != null) {
            return quest;
        }
        return QuestIds.chapter(id).orElse(null);
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
