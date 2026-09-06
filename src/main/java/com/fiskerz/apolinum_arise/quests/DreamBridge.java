package com.fiskerz.apolinum_arise.quests;

import com.fiskerz.apolinum_arise.dream.DreamManager;

import net.minecraft.server.level.ServerPlayer;

/**
 * The seam between the FTB Quests reward and the dream system. Phase 11a left this as a logged no-op
 * because Phase 10b did not exist yet; now that it does, it is the one-line delegation it was always
 * meant to be.
 */
public final class DreamBridge {
    private DreamBridge() {}

    /** The dream system now exists, so a queued dream really is played. */
    public static boolean isDreamSystemAvailable() {
        return true;
    }

    public static void queueDream(ServerPlayer player, String dreamId) {
        DreamManager.queueDream(player, dreamId);
    }
}
