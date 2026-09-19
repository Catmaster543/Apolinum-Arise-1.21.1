package com.fiskerz.apolinum_arise.quests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.skill.HealthyBranches;
import com.fiskerz.apolinum_arise.skill.InfectedVariants;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
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

    /**
     * REGRESSION, the patch this file's sibling exists for. Every gate appeared unlocked the instant a
     * player joined, because the placeholder gates had no tasks:
     *
     * <ul>
     *   <li>{@code QuestObject.isCompletedRaw} gathers a quest's child tasks and returns true outright when
     *       that list is empty - a task-less quest is "raw complete" for everybody.</li>
     *   <li>{@code ServerQuestFile.checkQuestBookOnLogin} runs
     *       {@code if (!data.isCompleted(q) && q.isCompletedRaw(data)) q.onCompleted(...)} over EVERY quest
     *       on EVERY login.</li>
     * </ul>
     *
     * So this asserts the exact predicate that login pass evaluates. It cannot call the pass itself - that
     * needs a real connected ServerPlayer - but {@code isCompletedRaw} is the whole of the decision, and it
     * must be false for a fresh team on every gate. The second half re-creates the old task-less shape to
     * prove the assertion would actually have caught the bug.
     */
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void gates_are_not_auto_completed_for_a_fresh_player(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file");

        QuestTestChain.remove(file);
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        try {
            TeamData fresh = file.getOrCreateTeamData(UUID.randomUUID());
            for (QuestTestChain.Gate gate : gates) {
                Quest quest = QuestIds.quest(QuestIds.parse(gate.codeString())).orElse(null);
                helper.assertTrue(quest != null, "Missing gate quest for " + gate.label());
                helper.assertFalse(quest.getTasks().isEmpty(),
                        gate.label() + " gate has no tasks - it would auto-complete on login");
                helper.assertFalse(quest.isCompletedRaw(fresh),
                        gate.label() + " gate is raw-complete for a fresh player, so the login pass would "
                                + "hand it to them unearned");
                helper.assertFalse(fresh.isCompleted(quest), gate.label() + " gate starts incomplete");
            }

            // The shape that caused the bug, so this test fails if the checkmark task is ever dropped.
            Chapter gatesChapter = QuestIds.quest(QuestIds.parse(gates.get(0).codeString()))
                    .orElseThrow().getQuestChapter();
            Quest taskless = new Quest(file.newID(), gatesChapter);
            taskless.onCreated();
            helper.assertTrue(taskless.isCompletedRaw(fresh),
                    "A task-less quest must be raw-complete - if this ever fails, FTB Quests changed and the "
                            + "checkmark-task workaround can be revisited");
        } finally {
            QuestTestChain.remove(file);
        }
        helper.succeed();
    }

    /**
     * REGRESSION for the second gate bug: a player could TICK a gate by hand and award themselves any
     * variant or branch, all seven if they liked.
     *
     * <p>The first fix gave each gate a {@link dev.ftb.mods.ftbquests.quest.task.CheckmarkTask}, which is a
     * MANUAL task - {@code canSubmit} returns true unconditionally. And {@code SubmitTaskMessage}'s only
     * server-side check is {@code TeamData.canStartTasks(quest)}: it validates neither chapter visibility
     * nor {@code canSubmit}, so hiding the gates would not have been enough either.
     *
     * <p>So this drives the submit path itself - the exact call the packet handler makes - and asserts the
     * gate does not budge. The contrast case at the end submits to a checkmark task and watches it complete
     * immediately, which is what proves this test would have caught the bug rather than passing vacuously.
     */
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void a_player_cannot_complete_a_gate_by_submitting_its_task(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file");

        QuestTestChain.remove(file);
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        try {
            TeamData data = file.getOrCreateTeamData(UUID.randomUUID());
            for (QuestTestChain.Gate entry : gates) {
                Quest gate = QuestIds.quest(QuestIds.parse(entry.codeString())).orElseThrow();
                Chapter revealed = QuestTestChain.chapterFor(file, entry.label());

                for (Task task : gate.getTasks()) {
                    // Exactly what SubmitTaskMessage reaches after its canStartTasks check passes. A null
                    // player is safe here precisely BECAUSE the task refuses before dereferencing anything -
                    // if this ever throws, the gate became submittable and the lock is gone.
                    task.submitTask(data, null, ItemStack.EMPTY);
                    helper.assertValueEqual(data.getProgress(task), 0L,
                            entry.label() + " gate task took progress from a submit");
                }
                helper.assertFalse(data.isCompleted(gate), entry.label() + " gate was completed by submitting");
                helper.assertFalse(revealed.isVisible(data),
                        entry.label() + " was revealed by ticking its gate - a player could grant themselves "
                                + "any branch this way");
            }

            // Contrast: a checkmark task DOES complete on submit. This is the behaviour that leaked, kept
            // here so the assertions above cannot quietly become vacuous.
            Quest content = QuestTestChain.chapterFor(file, gates.get(0).label()).getQuests().get(0);
            Task checkmark = content.getTasks().iterator().next();
            checkmark.submitTask(data, null, ItemStack.EMPTY);
            helper.assertTrue(data.getProgress(checkmark) > 0L,
                    "A checkmark task must still be completable by submitting - if not, this contrast case "
                            + "no longer proves anything and the gate assertions above need re-checking");
        } finally {
            QuestTestChain.remove(file);
        }
        helper.succeed();
    }

    // The gates chapter must never reach the quest book at all - the second barrier, in front of the lock.
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void the_gates_chapter_is_never_listed_in_the_book(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file");
        ChapterGroup group = file.getDefaultChapterGroup();

        QuestTestChain.remove(file);
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        try {
            TeamData data = file.getOrCreateTeamData(UUID.randomUUID());
            Chapter gatesChapter = QuestIds.quest(QuestIds.parse(gates.get(0).codeString()))
                    .orElseThrow().getQuestChapter();

            helper.assertFalse(gatesChapter.isVisible(data), "The gates chapter must be always-invisible");
            helper.assertFalse(group.getVisibleChapters(data).contains(gatesChapter),
                    "The quest book must never list the gates chapter");

            // Hiding it must not have cost us the bookkeeping that remove() and resetgates rely on: the
            // chapter is hidden by a full writeData/readData round trip, which reads its keys unguarded.
            helper.assertValueEqual(QuestTestChain.list(file).size(), gates.size(),
                    "The hidden gates chapter must still be findable by title");
        } finally {
            QuestTestChain.remove(file);
        }
        helper.assertValueEqual(QuestTestChain.list(file).size(), 0, "The hidden chapter still removes cleanly");
        helper.succeed();
    }

    // Item 4: the reset tool really does return a player to an ungated state, and only then.
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void resetting_gates_returns_a_player_to_a_clean_state(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file");
        ChapterGroup group = file.getDefaultChapterGroup();

        QuestTestChain.remove(file);
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        try {
            UUID playerId = UUID.randomUUID();
            TeamData data = file.getOrCreateTeamData(playerId);

            // Dirty the state the way the bug did: complete a gate, its dependent quest and its chapter.
            QuestTestChain.Gate chosen = gates.get(0);
            long gateId = QuestIds.parse(chosen.codeString());
            Chapter revealed = QuestTestChain.chapterFor(file, chosen.label());
            QuestGatesInternal.forceComplete(data, QuestGatesInternal.resolve(gateId), playerId);
            QuestGatesInternal.forceComplete(data, revealed, playerId);
            helper.assertTrue(group.getVisibleChapters(data).contains(revealed), "The chapter is visible first");

            int reset = QuestGatesInternal.resetGates(playerId, data, file, List.of());
            helper.assertTrue(reset > 0, "The reset must have touched something");

            helper.assertFalse(data.isCompleted(QuestGatesInternal.resolve(gateId)), "The gate is un-completed");
            helper.assertFalse(revealed.isVisible(data), "Its chapter is hidden again");
            helper.assertFalse(group.getVisibleChapters(data).contains(revealed),
                    "And the quest book would no longer list it");
            for (QuestTestChain.Gate gate : gates) {
                helper.assertFalse(data.isCompleted(QuestGatesInternal.resolve(QuestIds.parse(gate.codeString()))),
                        gate.label() + " gate must be clear after a reset");
            }
            // Clean means re-assignable: completing a gate again works exactly as it did the first time.
            QuestGatesInternal.forceComplete(data, QuestGatesInternal.resolve(gateId), playerId);
            helper.assertTrue(revealed.isVisible(data), "A re-assignment after a reset reveals the chapter again");
        } finally {
            QuestTestChain.remove(file);
        }
        helper.succeed();
    }

    /**
     * REGRESSION for "I picked a branch and nothing happened". The branch/variant binding was correct; the
     * CONFIGURED IDS were stale. Quest ids are assigned at creation, so every {@code testchain remove} +
     * {@code create} cycle invalidates whatever the config holds, and a dead id resolved to no quest,
     * opened nothing, and reported success anyway.
     *
     * <p>Two things are pinned here. First, {@code create} now writes its own ids into the config, so the
     * two cannot drift. Second, every failure mode is DISTINGUISHABLE - the old boolean return could not
     * tell "nothing configured" from "configured but deleted", which is why the failure was unactionable.
     */
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void creating_the_chain_writes_ids_the_gates_actually_resolve(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file");

        List<String> variantsBefore = List.copyOf(Config.INFECTED_VARIANT_GATE_QUEST_IDS.get());
        List<String> branchesBefore = List.copyOf(Config.HEALTHY_BRANCH_GATE_QUEST_IDS.get());
        QuestTestChain.remove(file);
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        try {
            List<String> configured = QuestGates.configuredGateIds();
            helper.assertValueEqual(configured.size(), gates.size(),
                    "Creating the chain must configure one gate id per gate");
            for (int i = 0; i < gates.size(); i++) {
                helper.assertValueEqual(configured.get(i), gates.get(i).codeString(),
                        "Configured gate " + i + " must be the id of the gate that was just created, in "
                                + "config order (variants first, then branches)");
                // The decisive check: the configured id resolves to a real quest. A stale id fails HERE.
                helper.assertTrue(QuestIds.quest(QuestIds.parse(configured.get(i))).isPresent(),
                        "Configured gate " + i + " (" + configured.get(i) + ") resolves to no quest - this is "
                                + "exactly the state in which a branch choice silently reveals nothing");
            }

            // The failure modes must stay distinguishable, so the player can be told which one happened.
            helper.assertValueEqual(QuestGates.GateResult.NOT_CONFIGURED.opened(), false, "Blank is a failure");
            helper.assertTrue(QuestGates.GateResult.NO_SUCH_QUEST.explain("healthy branch 0")
                            .contains("deleted and re-made"),
                    "A dead id must explain itself, not just fail");
            helper.assertTrue(QuestGates.GateResult.OPENED.opened(), "OPENED is the success case");
        } finally {
            QuestTestChain.remove(file);
            Config.INFECTED_VARIANT_GATE_QUEST_IDS.set(variantsBefore);
            Config.HEALTHY_BRANCH_GATE_QUEST_IDS.set(branchesBefore);
        }
        helper.succeed();
    }

    /**
     * REGRESSION: {@code remove} has to delete the chapters' files, not merely detach them.
     *
     * <p>It used to call {@code ChapterGroup.removeChapter}, which only unhooks a chapter in memory - the
     * {@code chapters/<filename>.snbt} survived the save and the chapter reloaded on the next server start.
     * So every remove/create cycle stacked another generation of gates in the world, and the config ended
     * up naming quests from a generation that was no longer the live one.
     */
    @GameTest(template = "empty_3x3", batch = "quest_gates", timeoutTicks = 600)
    public static void removing_the_chain_deletes_it_from_disk_too(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.getInstance().orElse(null);
        helper.assertTrue(file != null, "FTB Quests did not load a server quest file");

        QuestTestChain.remove(file);
        List<QuestTestChain.Gate> gates = QuestTestChain.create(file);
        Path chapters = file.getFolder().resolve("chapters");
        try {
            helper.assertTrue(Files.isDirectory(chapters), "The quest file writes chapters to " + chapters);
            helper.assertTrue(ourChapterFiles(chapters) > 0, "Creating the chain must write chapter files");

            QuestTestChain.remove(file);
            helper.assertValueEqual(ourChapterFiles(chapters), 0,
                    "Removing the chain must delete its chapter files - any left behind would reload as a "
                            + "duplicate chapter on the next server start");
            helper.assertValueEqual(QuestTestChain.list(file).size(), 0, "And nothing is left in memory");

            // The config must not be left naming quests that no longer exist.
            for (String id : QuestGates.configuredGateIds()) {
                helper.assertTrue(id.isBlank(),
                        "Removing the chain must clear its gate ids from the config, found '" + id + "'");
            }
        } finally {
            QuestTestChain.remove(file);
        }
        helper.assertValueEqual(gates.size(), InfectedVariants.COUNT + HealthyBranches.COUNT, "Sanity");
        helper.succeed();
    }

    /** Chapter files this scaffolding owns, recognised by the filenames {@code create} gives them. */
    private static int ourChapterFiles(Path chapters) {
        if (!Files.isDirectory(chapters)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(chapters)) {
            return (int) files.filter(p -> p.getFileName().toString().startsWith("apolinum_test_")).count();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list " + chapters, exception);
        }
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
