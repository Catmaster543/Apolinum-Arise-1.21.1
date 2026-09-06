package com.fiskerz.apolinum_arise.quests;

import java.util.ArrayList;
import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.skill.HealthyBranches;
import com.fiskerz.apolinum_arise.skill.InfectedVariants;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;

import net.minecraft.nbt.CompoundTag;

/**
 * Builds the placeholder quest content Phase 11 is tested against, so the gating can be proven in-game
 * without anyone hand-authoring seven chapters in the editor first.
 *
 * <p>It creates one "gates" chapter holding seven empty gate quests (three infected variants, four healthy
 * branches) and seven content chapters, each with a single quest that depends on its gate and is hidden
 * until that dependency completes. Paste the printed ids into {@code infectedVariantGateQuestIds} and
 * {@code healthyBranchGateQuestIds} and the whole reveal path runs against real FTB Quests objects.
 *
 * <p>This is scaffolding, not content: {@code /apolinumquests testchain remove} deletes exactly what it
 * created, which it recognises by the {@value #TITLE_PREFIX} title prefix. The gates chapter is
 * deliberately left VISIBLE so the gate state can be watched during a test - in real content that chapter
 * should be marked "always invisible" in the editor.
 */
final class QuestTestChain {
    private QuestTestChain() {}

    /** How created chapters are recognised later; nothing else should carry this prefix. */
    static final String TITLE_PREFIX = "[Apolinum Test] ";

    private static final String GATES_TITLE = TITLE_PREFIX + "Gates";
    private static final String HIDE_UNTIL_DEPS_COMPLETE = "hide_until_deps_complete";

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
        Apolinumarise.LOGGER.info("[Quests] Created the Phase 11 placeholder test chain ({} gates).", created.size());
        return created;
    }

    /** Remove everything {@link #create} made. Returns how many chapters were deleted. */
    static int remove(ServerQuestFile file) {
        ChapterGroup group = file.getDefaultChapterGroup();
        List<Chapter> ours = findOurChapters(file);
        for (Chapter chapter : ours) {
            group.removeChapter(chapter);
        }
        if (!ours.isEmpty()) {
            file.clearCachedData();
            file.markDirty();
            file.saveNow();
        }
        Apolinumarise.LOGGER.info("[Quests] Removed {} placeholder test chapter(s).", ours.size());
        return ours.size();
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

        return new Gate(label, QuestIds.toCodeString(gate.id));
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
