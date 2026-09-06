package com.fiskerz.apolinum_arise.quests;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.server.level.ServerPlayer;

/**
 * "Reveal this content, for this player only" - the single entry point the rest of the mod uses for the
 * per-player quest visibility mechanism established in Phase 11a.
 *
 * <p><b>Why the split.</b> This class deliberately imports NOTHING from FTB Quests; every FTB type lives in
 * {@link QuestGatesInternal}, which is only ever reached from inside an {@code isQuestsLoaded()} branch. So
 * on an installation without FTB Quests the JVM never has to resolve an FTB class to run any of this - the
 * calls just return false and the mod carries on. Callers do not need their own guard.
 *
 * <p><b>What "completing a gate" means.</b> FTB Quests has no per-player visibility setter -
 * {@code isVisible(TeamData)} is a read-only derived query. The only lever is that player's own progress,
 * so the pattern is: author a hidden gate quest, make the real chapter's quests depend on it with "hide
 * until dependencies complete", and force-complete the gate for exactly the player who should see it.
 * Gates that are never completed simply stay incomplete, which is all the "lock" the other variants and
 * branches need.
 */
public final class QuestGates {
    private QuestGates() {}

    /**
     * Force-complete {@code gateCodeString} (the 16-digit hex id copied out of the quest editor) for this
     * player alone. Returns true only when a real quest object was actually progressed.
     *
     * <p>Every failure is soft and logged: no FTB Quests installed, an unconfigured or malformed id, no
     * quest file loaded yet, or no quest with that id. That matters because the shipped config defaults are
     * deliberately empty - variant and branch assignment must keep working before any content is authored.
     */
    public static boolean completeGate(ServerPlayer player, String gateCodeString, String what) {
        if (gateCodeString == null || gateCodeString.isBlank()) {
            Apolinumarise.LOGGER.info("[Quests] No gate quest configured for {} - {} keeps their assignment, "
                    + "but no quest content is revealed. Paste the quest id into the config once it exists.",
                    what, player.getGameProfile().getName());
            return false;
        }
        if (!ApolinumQuests.isQuestsLoaded()) {
            Apolinumarise.LOGGER.info("[Quests] FTB Quests is not installed - skipping the {} gate for {}.",
                    what, player.getGameProfile().getName());
            return false;
        }
        return QuestGatesInternal.completeGate(player, gateCodeString, what);
    }

    /**
     * A one-line human-readable state of a configured gate for this player, for the debug readout: whether
     * it is configured, whether it resolves to a real quest, and whether it is complete for them.
     */
    public static String describeGate(ServerPlayer player, String gateCodeString) {
        if (gateCodeString == null || gateCodeString.isBlank()) {
            return "not configured";
        }
        if (!ApolinumQuests.isQuestsLoaded()) {
            return gateCodeString + " (FTB Quests not installed)";
        }
        return QuestGatesInternal.describeGate(player, gateCodeString);
    }
}
