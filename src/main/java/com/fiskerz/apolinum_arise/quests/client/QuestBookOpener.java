package com.fiskerz.apolinum_arise.quests.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.quests.ApolinumQuests;

/**
 * Opens FTB Quests' own quest book from our skill button/keybind (Phase 11 item 5).
 *
 * <p>{@code ClientQuestFile.openGui()} is the same public entry point FTB's own keybind uses, so the book
 * behaves exactly as it normally does - including the part that matters here: its chapter list is built
 * from {@code ChapterGroup.getVisibleChapters(TeamData)}, which filters on {@code Chapter.isVisible} for
 * THAT player's team data. So the per-player gate completion is all the chapter filtering we need; there is
 * no navigation code to add on our side.
 *
 * <p>Split into a facade ({@link #open()}) and {@link QuestBookOpenerInternal} for the same reason the
 * server-side gate helper is: nothing here resolves an FTB class unless FTB Quests is actually installed.
 */
public final class QuestBookOpener {
    private QuestBookOpener() {}

    /** Returns false when the book could not be opened, so the caller can fall back to our own shell. */
    public static boolean open() {
        if (!ApolinumQuests.isQuestsLoaded()) {
            Apolinumarise.LOGGER.debug("[Quests] FTB Quests is not installed - cannot open the quest book.");
            return false;
        }
        return QuestBookOpenerInternal.open();
    }
}
