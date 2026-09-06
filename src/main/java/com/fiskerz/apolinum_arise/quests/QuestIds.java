package com.fiskerz.apolinum_arise.quests;

import java.util.Optional;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import net.minecraft.server.level.ServerPlayer;

/**
 * How our code refers to user-authored quest content (Phase 11a, item 5).
 *
 * <p>Every FTB Quests object (chapter, quest, task, reward) has a {@code long} id. The editor and the
 * quest files show it as a 16-digit HEX "code string" (e.g. {@code 3A7F10C2B4D5E608}); that string is what
 * a user copies out of the editor. {@link QuestObjectBase#parseCodeString(String)} converts it back, and
 * {@code 0L} is FTB's "invalid/none" id.
 *
 * <p>So the intended pattern is: the user authors content, copies the hex id, pastes it into one of our
 * config fields, and our code resolves it here at runtime. Ids are stable across edits (they are assigned
 * once at creation), but they are NOT stable across a re-created quest - a deleted-and-remade chapter gets
 * a new id and the config value must be re-pasted.
 */
public final class QuestIds {
    private QuestIds() {}

    /** FTB's sentinel for "no object". */
    public static final long INVALID = 0L;

    /** Parse a hex code string as copied from the quest editor. Returns {@link #INVALID} if unparseable. */
    public static long parse(String codeString) {
        if (codeString == null || codeString.isBlank()) {
            return INVALID;
        }
        try {
            return QuestObjectBase.parseCodeString(codeString.trim());
        } catch (RuntimeException exception) {
            return INVALID;
        }
    }

    /** Render an id back to the hex form the editor displays. */
    public static String toCodeString(long id) {
        return QuestObjectBase.getCodeString(id);
    }

    public static Optional<ServerQuestFile> file() {
        return ServerQuestFile.getInstance();
    }

    public static Optional<Quest> quest(long id) {
        return file().map(f -> f.getQuest(id));
    }

    public static Optional<Chapter> chapter(long id) {
        return file().map(f -> f.getChapter(id));
    }

    /** The quest-progress data for this player's team (every player has one, solo or not). */
    public static Optional<TeamData> teamData(ServerPlayer player) {
        return file().map(f -> f.getOrCreateTeamData(player));
    }

    /** Completion state of an authored quest for a specific player - the main read our systems will want. */
    public static boolean isCompleted(ServerPlayer player, long questId) {
        Optional<Quest> quest = quest(questId);
        Optional<TeamData> data = teamData(player);
        return quest.isPresent() && data.isPresent() && data.get().isCompleted((QuestObject) quest.get());
    }
}
