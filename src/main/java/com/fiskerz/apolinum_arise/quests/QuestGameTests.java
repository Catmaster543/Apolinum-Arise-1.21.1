package com.fiskerz.apolinum_arise.quests;

import java.util.List;
import java.util.UUID;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.skill.HealthyBranches;
import com.fiskerz.apolinum_arise.skill.InfectedVariants;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Proof that the Phase 11 gating mechanism actually reveals content, on real FTB Quests objects.
 *
 * <p>Phase 11a established the theory - there is no per-player visibility setter, only per-player progress
 * on a gate quest that the real content depends on - but could not demonstrate it, because no quest content
 * existed. This runs against exactly the placeholder chain {@code /apolinumquests testchain create} builds
 * in-game, so what passes here is what the user will be exercising by hand.
 *
 * <p>The chapter list asserted here, {@code ChapterGroup.getVisibleChapters(TeamData)}, is the very list
 * FTB's own {@code ChapterPanel} builds its buttons from - so this is also the check behind item 5's claim
 * that our routing needs no chapter-navigation code of its own.
 */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class QuestGameTests {

    // The whole mechanism end to end: incomplete gates hide their chapters, completing ONE gate reveals
    // ONLY that chapter and only to that player, and every other gate stays shut with nothing to lock.
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void completing_one_gate_reveals_only_that_chapter_for_only_that_player(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file - cannot test gating");
        ChapterGroup group = file.getDefaultChapterGroup();

        QuestTestChain.remove(file); // in case a previous run left one behind
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        try {
            helper.assertValueEqual(gates.size(), InfectedVariants.COUNT + HealthyBranches.COUNT,
                    "One gate per variant and per branch");

            UUID revealed = UUID.randomUUID();
            UUID untouched = UUID.randomUUID();
            TeamData revealedData = file.getOrCreateTeamData(revealed);
            TeamData untouchedData = file.getOrCreateTeamData(untouched);

            // Before: every gated chapter is hidden from everyone.
            for (QuestTestChain.Gate gate : gates) {
                Chapter chapter = QuestTestChain.chapterFor(file, gate.label());
                helper.assertTrue(chapter != null, "Missing content chapter for " + gate.label());
                helper.assertFalse(chapter.isVisible(revealedData), gate.label() + " starts hidden");
                helper.assertFalse(group.getVisibleChapters(revealedData).contains(chapter),
                        gate.label() + " must not be in the list the quest book builds its buttons from");
            }

            // The config path: the user pastes the hex code string the editor (or our command) prints.
            QuestTestChain.Gate chosen = gates.get(0);
            long id = QuestIds.parse(chosen.codeString());
            helper.assertTrue(id != QuestIds.INVALID, "The printed code string must parse back to an id");
            helper.assertTrue(QuestGatesInternal.resolve(id) != null, "And must resolve to a real quest");

            // The action: exactly what QuestGates.completeGate performs for an assigned variant or branch.
            QuestGatesInternal.forceComplete(revealedData, QuestGatesInternal.resolve(id), revealed);

            // After: that one chapter is revealed to that one player.
            Chapter revealedChapter = QuestTestChain.chapterFor(file, chosen.label());
            helper.assertTrue(revealedData.isCompleted(QuestGatesInternal.resolve(id)),
                    "The gate is completed for this player");
            helper.assertTrue(revealedChapter.isVisible(revealedData), "Its chapter is now visible to them");
            helper.assertTrue(group.getVisibleChapters(revealedData).contains(revealedChapter),
                    "And the quest book would now list that chapter for them");

            // Every other gate was left alone, and that alone keeps its chapter hidden - there is no
            // explicit lock anywhere, which is exactly the design claim being checked here.
            for (int i = 1; i < gates.size(); i++) {
                Chapter other = QuestTestChain.chapterFor(file, gates.get(i).label());
                helper.assertFalse(other.isVisible(revealedData),
                        gates.get(i).label() + " must stay hidden - its gate was never touched");
            }

            // And nothing reached the other player.
            helper.assertFalse(untouchedData.isCompleted(QuestGatesInternal.resolve(id)),
                    "Another player's gate is untouched");
            helper.assertFalse(revealedChapter.isVisible(untouchedData), "The chapter stays hidden for them");
            helper.assertFalse(group.getVisibleChapters(untouchedData).contains(revealedChapter),
                    "Their quest book still would not list it");
        } finally {
            QuestTestChain.remove(file);
        }
        helper.assertValueEqual(QuestTestChain.list(file).size(), 0, "The placeholder chain cleans up fully");
        helper.succeed();
    }

    // Phase 11a's id contract, which every gate config field depends on: 0L is FTB's "none", garbage does
    // not throw, and a real id survives the round trip through the hex form a user pastes.
    @GameTest(template = "empty_3x3", batch = "quest_ids")
    public static void quest_id_parsing_is_total(GameTestHelper helper) {
        helper.assertValueEqual(QuestIds.parse(""), QuestIds.INVALID, "Empty is the invalid id");
        helper.assertValueEqual(QuestIds.parse(null), QuestIds.INVALID, "Null is the invalid id");
        helper.assertValueEqual(QuestIds.parse("not_a_quest_id"), QuestIds.INVALID, "Garbage is the invalid id");

        long id = 0x3A7F10C2B4D5E608L;
        helper.assertValueEqual(QuestIds.parse(QuestIds.toCodeString(id)), id, "Hex round trip");
        helper.assertValueEqual(QuestIds.parse("  " + QuestIds.toCodeString(id) + "  "), id,
                "A pasted id with stray whitespace still parses");
        helper.succeed();
    }
}
