package com.fiskerz.apolinum_arise.quests;

import java.util.ArrayList;
import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.skill.HealthyBranches;
import com.fiskerz.apolinum_arise.skill.InfectedVariants;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.CustomTask;

import net.minecraft.nbt.CompoundTag;

/**
 * Builds the placeholder quest content Phase 11 is tested against, so the gating can be proven in-game
 * without anyone hand-authoring seven chapters in the editor first.
 *
 * <p>It creates one "gates" chapter holding seven gate quests (three infected variants, four healthy
 * branches) and seven content chapters, each with a single quest that depends on its gate and is hidden
 * until that dependency completes. Creating the chain also WRITES its ids into
 * {@code infectedVariantGateQuestIds} / {@code healthyBranchGateQuestIds}, so the config can never point
 * at a stale generation of the chain - see {@link #writeGateIdsToConfig}.
 *
 * <p><b>The gates are locked against the player, not just hidden from them</b> - see
 * {@link #giveLockedTask}, which is the part of this file most worth reading before changing anything.
 * The gates chapter is also marked always-invisible, so it never appears in the quest book at all; author
 * real gate chapters the same way.
 *
 * <p>This is scaffolding, not content: {@code /apolinumquests testchain remove} deletes exactly what it
 * created, which it recognises by the {@value #TITLE_PREFIX} title prefix.
 */
final class QuestTestChain {
    private QuestTestChain() {}

    /** How created chapters are recognised later; nothing else should carry this prefix. */
    static final String TITLE_PREFIX = "[Apolinum Test] ";

    private static final String GATES_TITLE = TITLE_PREFIX + "Gates";
    private static final String HIDE_UNTIL_DEPS_COMPLETE = "hide_until_deps_complete";
    private static final String ALWAYS_INVISIBLE = "always_invisible";

    /** One created gate: the label it belongs to, and the hex id to paste into config. */
    record Gate(String label, String codeString) {}

    /**
     * Create the chain. Returns the gates in config order - the three variants first, then the four
     * branches - or an empty list if a chain already exists, since a second one would only produce a
     * confusing set of duplicate ids.
     */
    static List<Gate> create(ServerQuestFile file) {
        ChapterGroup group = file.getDefaultChapterGroup();
        if (!findGatesChapter(file).isEmpty()) {
            return List.of();
        }

        Chapter gates = new Chapter(file.newID(), file, group, "apolinum_test_gates");
        // Hide first, title second: hideChapter round-trips the chapter through readData, which reads its
        // keys unguarded, and the title is what findOurChapters matches on later.
        hideChapter(file, gates);
        gates.setRawTitle(GATES_TITLE);
        gates.onCreated();

        List<Gate> created = new ArrayList<>();
        for (int i = 0; i < InfectedVariants.COUNT; i++) {
            created.add(makeGatedPair(file, group, gates, "variant_" + i, "Infected Variant " + i));
        }
        for (int i = 0; i < HealthyBranches.COUNT; i++) {
            created.add(makeGatedPair(file, group, gates, "branch_" + i, "Healthy Branch " + i));
        }

        file.clearCachedData();
        file.markDirty();
        file.saveNow();
        writeGateIdsToConfig(created);
        Apolinumarise.LOGGER.info("[Quests] Created the Phase 11 placeholder test chain ({} gates).", created.size());
        return created;
    }

    /**
     * Point the config at the gates we just made, instead of asking a human to copy seven hex ids across.
     *
     * <p>This closes the failure that made branch selection look broken: quest ids are assigned at creation,
     * so every {@code remove} + {@code create} cycle invalidates whatever is in the config, and a config
     * holding a dead id resolves to no quest, opens nothing, and used to do it silently. Writing the ids
     * here means the two can never drift for the placeholder chain.
     */
    private static void writeGateIdsToConfig(List<Gate> created) {
        List<String> variants = new ArrayList<>();
        List<String> branches = new ArrayList<>();
        for (int i = 0; i < created.size(); i++) {
            (i < InfectedVariants.COUNT ? variants : branches).add(created.get(i).codeString());
        }
        Config.INFECTED_VARIANT_GATE_QUEST_IDS.set(variants);
        Config.HEALTHY_BRANCH_GATE_QUEST_IDS.set(branches);
        Config.HEALTHY_BRANCH_GATE_QUEST_IDS.save(); // one save flushes the whole spec
        Apolinumarise.LOGGER.info("[Quests] Wrote the new gate ids into the config: variants {}, branches {}.",
                variants, branches);
    }

    /**
     * Remove everything {@link #create} made, from memory AND from disk. Returns how many chapters went.
     *
     * <p><b>{@code deleteObjects} is the only removal that deletes the files.</b> This used to call
     * {@code ChapterGroup.removeChapter}, which merely detaches a chapter in memory: its
     * {@code chapters/<filename>.snbt} survived the save and the chapter came straight back on the next
     * server start. Every remove/create cycle therefore left another generation of gates behind, which is
     * how a world ended up with several {@code [Apolinum Test] Gates} chapters and a config pointing at
     * whichever generation happened to be oldest. {@code ServerQuestFile.deleteObjects} does the real job -
     * it drops the translations, detaches the object and deletes its file.
     */
    static int remove(ServerQuestFile file) {
        List<Chapter> ours = findOurChapters(file);
        if (ours.isEmpty()) {
            Apolinumarise.LOGGER.info("[Quests] No placeholder test chapters to remove.");
            return 0;
        }
        List<Long> ids = new ArrayList<>();
        for (Chapter chapter : ours) {
            ids.add(chapter.id);
        }
        file.deleteObjects(ids);
        file.clearCachedData();
        file.markDirty();
        file.saveNow();
        // Symmetric with create(): a config left pointing at deleted quests is the exact state that makes
        // an assignment look like it did nothing.
        clearGateIdsFromConfig();
        Apolinumarise.LOGGER.info("[Quests] Removed {} placeholder test chapter(s) and cleared their config ids.",
                ours.size());
        return ours.size();
    }

    private static void clearGateIdsFromConfig() {
        Config.INFECTED_VARIANT_GATE_QUEST_IDS.set(List.of("", "", ""));
        Config.HEALTHY_BRANCH_GATE_QUEST_IDS.set(List.of("", "", "", ""));
        Config.HEALTHY_BRANCH_GATE_QUEST_IDS.save();
    }

    /** The gates currently in the world, in creation order, so the ids can be re-printed at any time. */
    static List<Gate> list(ServerQuestFile file) {
        List<Gate> gates = new ArrayList<>();
        for (Chapter chapter : findGatesChapter(file)) {
            for (Quest quest : chapter.getQuests()) {
                gates.add(new Gate(quest.getRawTitle(), QuestIds.toCodeString(quest.id)));
            }
        }
        return gates;
    }

    // A gate quest in the gates chapter, plus a chapter of "content" that only appears once it completes.
    private static Gate makeGatedPair(ServerQuestFile file, ChapterGroup group, Chapter gates,
                                      String key, String label) {
        Quest gate = new Quest(file.newID(), gates);
        gate.setRawTitle(label + " Gate");
        gate.setPosition(0.0D, gates.getQuests().size() * 2.0D);
        gate.onCreated();
        giveLockedTask(file, gate);

        Chapter content = new Chapter(file.newID(), file, group, "apolinum_test_content_" + key);
        content.setRawTitle(TITLE_PREFIX + label);
        content.onCreated();

        Quest gated = new Quest(file.newID(), content);
        gated.setRawTitle(label + " Content");
        gated.onCreated();
        // The editor toggle that makes a dependency actually HIDE its dependents rather than merely lock
        // them - without it the content chapter would be visible from the start, just uncompletable.
        CompoundTag flags = new CompoundTag();
        flags.putBoolean(HIDE_UNTIL_DEPS_COMPLETE, true);
        gated.readData(flags, file.holderLookup());
        gated.addDependency(gate);
        giveCheckmarkTask(file, gated);

        return new Gate(label, QuestIds.toCodeString(gate.id));
    }

    /**
     * Give a GATE quest one task that nothing can ever complete. <b>Load-bearing; read before changing.</b>
     *
     * <p>A gate must satisfy two independent requirements, and they have bitten this code once each:
     *
     * <ol>
     *   <li><b>It must not complete itself.</b> A quest with NO tasks is "raw complete" for everybody -
     *       {@code QuestObject.isCompletedRaw} collects the quest's child tasks and returns true outright
     *       when that list is empty - and {@code ServerQuestFile.checkQuestBookOnLogin} runs
     *       {@code if (!data.isCompleted(q) && q.isCompletedRaw(data)) q.onCompleted(...)} over every quest
     *       on every login. Task-less gates therefore handed every chapter to every player on join.</li>
     *   <li><b>A player must not be able to complete it.</b> The first fix used a {@link CheckmarkTask},
     *       which is a MANUAL task: {@code CheckmarkTask.canSubmit} returns true unconditionally, and
     *       {@code SubmitTaskMessage}'s only server-side check is {@code TeamData.canStartTasks} - it
     *       validates neither visibility nor {@code canSubmit}. So anyone could tick a gate by hand and
     *       award themselves every variant and branch.</li>
     * </ol>
     *
     * <p>{@link CustomTask} with no {@code Check} attached closes both. Its constructor leaves
     * {@code check} null, and nothing sets one because we never listen for {@code CustomTaskEvent}, so:
     * {@code submitTask} returns immediately on the null check (a crafted submit packet does nothing),
     * {@code checkOnLogin()} is false (the login pass skips it), {@code autoSubmitOnPlayerTick()} is 0 with
     * no check (it is never polled), and {@code enableButton} defaults false (no GUI button at all). The
     * only remaining way in is {@code forceProgress}, which writes completion directly - which is exactly
     * our gate-opening call and nothing else.
     */
    private static void giveLockedTask(ServerQuestFile file, Quest quest) {
        CustomTask task = new CustomTask(file.newID(), quest);
        task.onCreated(); // registers it in the file's id map AND adds it to the quest
    }

    /**
     * Give a CONTENT quest a manual checkmark, so the placeholder chapters have something to click once
     * they are revealed. Safe here in a way it was not on a gate: nothing depends on a content quest, and
     * {@code SubmitTaskMessage} rejects the submit anyway until its gate is complete, because
     * {@code canStartTasks} requires the quest's dependencies to be done.
     */
    private static void giveCheckmarkTask(ServerQuestFile file, Quest quest) {
        CheckmarkTask task = new CheckmarkTask(file.newID(), quest);
        task.onCreated();
    }

    /**
     * Mark the gates chapter always-invisible, so the quest book never lists it and the gates have no UI a
     * player could reach. This is the second of the two barriers - the locked task above is the one that
     * actually holds, since the submit packet is not visibility-checked, but a gate a player can SEE is a
     * gate a player will try, so it should not be on screen either.
     *
     * <p>Done by round-tripping the chapter through FTB's own serializer rather than calling
     * {@code readData} with a one-key tag: {@code Chapter.readData} reads its keys UNGUARDED (plain
     * {@code getString}/{@code getBoolean}, no {@code contains} check), so a sparse tag would silently blank
     * the filename and every other field. Writing first means every key is present and only the one we
     * change differs.
     */
    private static void hideChapter(ServerQuestFile file, Chapter chapter) {
        CompoundTag data = new CompoundTag();
        chapter.writeData(data, file.holderLookup());
        data.putBoolean(ALWAYS_INVISIBLE, true);
        chapter.readData(data, file.holderLookup());
    }

    /** The content chapter a given gate reveals, found by the label {@link #create} returned with it. */
    static Chapter chapterFor(ServerQuestFile file, String label) {
        return findOurChapters(file).stream()
                .filter(c -> (TITLE_PREFIX + label).equals(c.getRawTitle()))
                .findFirst().orElse(null);
    }

    private static List<Chapter> findGatesChapter(ServerQuestFile file) {
        return findOurChapters(file).stream().filter(c -> GATES_TITLE.equals(c.getRawTitle())).toList();
    }

    private static List<Chapter> findOurChapters(ServerQuestFile file) {
        List<Chapter> ours = new ArrayList<>();
        for (Chapter chapter : file.getDefaultChapterGroup().getChapters()) {
            String title = chapter.getRawTitle();
            if (title != null && title.startsWith(TITLE_PREFIX)) {
                ours.add(chapter);
            }
        }
        return ours;
    }
}
