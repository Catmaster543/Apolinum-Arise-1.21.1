package com.fiskerz.apolinum_arise.quests;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.quests.QuestGates.GateResult;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.util.ProgressChange;

import net.minecraft.server.level.ServerPlayer;

/**
 * The FTB-touching half of {@link QuestGates}. Never call this directly - go through the facade, which is
 * what keeps FTB classes off the code path when FTB Quests is not installed.
 */
final class QuestGatesInternal {
    private QuestGatesInternal() {}

    static GateResult completeGate(ServerPlayer player, String gateCodeString) {
        GateResult check = checkGate(player, gateCodeString);
        if (!check.opened()) {
            return check;
        }
        long id = QuestIds.parse(gateCodeString);
        TeamData data = QuestIds.teamData(player).orElseThrow();
        QuestObject object = resolve(id);
        forceComplete(data, object, player.getUUID());
        Apolinumarise.LOGGER.info("[Quests] Opened gate {} for {} only (team {}); it is now visible={} "
                        + "completed={}.", QuestIds.toCodeString(id), player.getGameProfile().getName(),
                data.getName(), object.isVisible(data), data.isCompleted(object));
        return GateResult.OPENED;
    }

    /** Resolve a configured id without changing anything. OPENED here means "this gate is usable". */
    static GateResult checkGate(ServerPlayer player, String gateCodeString) {
        long id = QuestIds.parse(gateCodeString);
        if (id == QuestIds.INVALID) {
            return GateResult.UNPARSEABLE_ID;
        }
        if (QuestIds.teamData(player).isEmpty()) {
            return GateResult.NO_QUEST_FILE;
        }
        return resolve(id) == null ? GateResult.NO_SUCH_QUEST : GateResult.OPENED;
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
     * Un-complete every known gate for this player's team, plus everything those gates were revealing, so
     * they are back to seeing no gated chapters at all. Returns how many quest objects were reset.
     *
     * <p>"Known" means every id configured in {@code infectedVariantGateQuestIds} /
     * {@code healthyBranchGateQuestIds} plus every gate in the placeholder test chain, so this works
     * whether the world is running authored content, the scaffolding, or both.
     *
     * <p>Resetting a gate alone is not enough for a clean state: the quests that depend on it may already
     * be complete, and so may their chapters. Those are reset too - but a chapter ONLY when every one of
     * its quests is in the reset set, because {@code forceProgress} on a chapter recurses into all of its
     * children and would otherwise wipe unrelated progress a player legitimately earned.
     */
    static int resetGates(ServerPlayer player, Collection<String> configuredCodeStrings) {
        ServerQuestFile file = QuestIds.file().orElse(null);
        TeamData data = QuestIds.teamData(player).orElse(null);
        if (file == null || data == null) {
            Apolinumarise.LOGGER.warn("[Quests] No server quest file loaded - cannot reset gates for {}.",
                    player.getGameProfile().getName());
            return 0;
        }
        return resetGates(player.getUUID(), data, file, configuredCodeStrings);
    }

    /** The reset itself, addressed by team data rather than by player, so a gametest can drive it. */
    static int resetGates(UUID playerId, TeamData data, ServerQuestFile file,
                          Collection<String> configuredCodeStrings) {
        Set<Long> gateIds = new HashSet<>();
        for (String codeString : configuredCodeStrings) {
            long id = QuestIds.parse(codeString);
            if (id != QuestIds.INVALID && QuestIds.quest(id).isPresent()) {
                gateIds.add(id);
            }
        }
        for (QuestTestChain.Gate gate : QuestTestChain.list(file)) {
            long id = QuestIds.parse(gate.codeString());
            if (id != QuestIds.INVALID) {
                gateIds.add(id);
            }
        }
        if (gateIds.isEmpty()) {
            return 0;
        }

        Set<QuestObject> toReset = new LinkedHashSet<>();
        for (long id : gateIds) {
            QuestIds.quest(id).ifPresent(toReset::add);
        }
        Set<Quest> dependents = new LinkedHashSet<>();
        file.forAllQuests(quest -> {
            if (quest.streamDependencies().anyMatch(dependency -> gateIds.contains(dependency.id))) {
                dependents.add(quest);
            }
        });
        toReset.addAll(dependents);
        for (Quest dependent : dependents) {
            Chapter chapter = dependent.getQuestChapter();
            if (chapter != null && dependents.containsAll(chapter.getQuests())) {
                toReset.add(chapter);
            }
        }

        for (QuestObject object : toReset) {
            ProgressChange change = new ProgressChange(object, playerId);
            change.setReset(true);
            object.forceProgress(data, change);
        }
        data.saveIfChanged();
        Apolinumarise.LOGGER.info("[Quests] Reset {} quest object(s) across {} gate(s) for team {}.",
                toReset.size(), gateIds.size(), data.getName());
        return toReset.size();
    }

    /** How many of the given gates are still complete for this player - for verifying a reset. */
    static int countCompletedGates(ServerPlayer player, Collection<String> configuredCodeStrings) {
        TeamData data = QuestIds.teamData(player).orElse(null);
        ServerQuestFile file = QuestIds.file().orElse(null);
        if (data == null || file == null) {
            return 0;
        }
        Set<Long> gateIds = new HashSet<>();
        for (String codeString : configuredCodeStrings) {
            gateIds.add(QuestIds.parse(codeString));
        }
        for (QuestTestChain.Gate gate : QuestTestChain.list(file)) {
            gateIds.add(QuestIds.parse(gate.codeString()));
        }
        int completed = 0;
        for (long id : gateIds) {
            if (id == QuestIds.INVALID) {
                continue;
            }
            QuestObject object = resolve(id);
            if (object != null && data.isCompleted(object)) {
                completed++;
            }
        }
        return completed;
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
