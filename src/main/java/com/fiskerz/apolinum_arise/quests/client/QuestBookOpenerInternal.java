package com.fiskerz.apolinum_arise.quests.client;

import com.fiskerz.apolinum_arise.Apolinumarise;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;

/**
 * The FTB-touching half of {@link QuestBookOpener}. Never call this directly.
 */
final class QuestBookOpenerInternal {
    private QuestBookOpenerInternal() {}

    static boolean open() {
        // exists() guards the window between joining a world and the quest file arriving from the server.
        if (!ClientQuestFile.exists()) {
            Apolinumarise.LOGGER.warn("[Quests] The quest file has not synced to this client yet - "
                    + "not opening the book.");
            return false;
        }
        ClientQuestFile.openGui();
        return true;
    }
}
