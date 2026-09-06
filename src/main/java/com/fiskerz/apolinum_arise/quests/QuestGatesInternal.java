package com.fiskerz.apolinum_arise.quests;

import java.util.UUID;

import com.fiskerz.apolinum_arise.Apolinumarise;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.util.ProgressChange;

import net.minecraft.server.level.ServerPlayer;

/**
 * The FTB-touching half of {@link QuestGates}. Never call this directly - go through the facade, which is
 * what keeps FTB classes off the code path when FTB Quests is not installed.
 */
final class QuestGatesInternal {
    private QuestGatesInternal() {}

    static boolean completeGate(ServerPlayer player, String gateCodeString, String what) {
        long id = QuestIds.parse(gateCodeString);
        if (id == QuestIds.INVALID) {
            Apolinumarise.LOGGER.warn("[Quests] The {} gate id '{}' is not a valid quest code string "
                    + "(expected the 16-digit hex the editor shows) - nothing revealed for {}.",
                    what, gateCodeString, player.getGameProfile().getName());
            return false;
        }
        TeamData data = QuestIds.teamData(player).orElse(null);
        if (data == null) {
            Apolinumarise.LOGGER.warn("[Quests] No server quest file is loaded - could not open the {} gate "
                    + "for {}.", what, player.getGameProfile().getName());
            return false;
        }
        QuestObject object = resolve(id);
        if (object == null) {
            Apolinumarise.LOGGER.warn("[Quests] No quest or chapter with id {} - the {} gate for {} points at "
                    + "content that does not exist (was it deleted and re-made? ids change).",
                    QuestIds.toCodeString(id), what, player.getGameProfile().getName());
            return false;
        }
        forceComplete(data, object, player.getUUID());
        Apolinumarise.LOGGER.info("[Quests] Opened the {} gate {} for {} only (team {}); it is now visible={} "
                        + "completed={}.", what, QuestIds.toCodeString(id), player.getGameProfile().getName(),
                data.getName(), object.isVisible(data), data.isCompleted(object));
        return true;
    }

    static String describeGate(ServerPlayer player, String gateCodeString) {
        long id = QuestIds.parse(gateCodeString);
        if (id == QuestIds.INVALID) {
            return gateCodeString + " (not a valid quest code string)";
        }
        TeamData data = QuestIds.teamData(player).orElse(null);
        QuestObject object = resolve(id);
        if (data == null) {
            return QuestIds.toCodeString(id) + " (no quest file loaded)";
        }
        if (object == null) {
            return QuestIds.toCodeString(id) + " (no quest or chapter with that id)";
        }
        return String.format("%s completed=%s visible=%s", QuestIds.toCodeString(id),
                data.isCompleted(object), object.isVisible(data));
    }

    /**
     * The whole per-player visibility mechanism in three lines: give this player progress on the gate, and
     * everything that depends on it becomes visible to their team data alone.
     */
    static void forceComplete(TeamData data, QuestObject object, UUID playerId) {
        ProgressChange change = new ProgressChange(object, playerId);
        change.setReset(false);
        object.forceProgress(data, change);
        data.saveIfChanged();
    }

    static QuestObject resolve(long id) {
        Quest quest = QuestIds.quest(id).orElse(null);
        if (quest != null) {
            return quest;
        }
        Chapter chapter = QuestIds.chapter(id).orElse(null);
        return chapter;
    }
}
