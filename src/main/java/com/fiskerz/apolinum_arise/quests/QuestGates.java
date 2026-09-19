package com.fiskerz.apolinum_arise.quests;

import java.util.ArrayList;
import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.server.level.ServerPlayer;

/**
 * "Reveal this content, for this player only" - the single entry point the rest of the mod uses for the
 * per-player quest visibility mechanism established in Phase 11a.
 *
 * <p><b>Why the split.</b> This class deliberately imports NOTHING from FTB Quests; every FTB type lives in
 * {@link QuestGatesInternal}, which is only ever reached from inside an {@code isQuestsLoaded()} branch. So
 * on an installation without FTB Quests the JVM never has to resolve an FTB class to run any of this - the
 * calls just report a failure and the mod carries on. Callers do not need their own guard.
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
     * Why a gate did or did not open. This used to be a bare boolean, which meant a stale config id - the
     * single most likely thing to go wrong, because quest ids change whenever content is re-created - left
     * the player with a cheerful "branch chosen" message and no content, and nothing but a log line to say
     * why. Callers are expected to put {@link #explain} in front of whoever triggered the assignment.
     */
    public enum GateResult {
        OPENED("the gate was completed"),
        NOT_CONFIGURED("no gate quest id is configured for it - paste one into the config, or run "
                + "/apolinumquests testchain create to generate the placeholder chain"),
        QUESTS_NOT_INSTALLED("FTB Quests is not installed"),
        NO_QUEST_FILE("no server quest file is loaded yet"),
        UNPARSEABLE_ID("the configured id is not a valid quest code string"),
        NO_SUCH_QUEST("the configured id does not match any quest - it was almost certainly deleted and "
                + "re-made, which assigns a new id; re-run /apolinumquests testchain create or re-paste it");

        private final String reason;

        GateResult(String reason) {
            this.reason = reason;
        }

        public boolean opened() {
            return this == OPENED;
        }

        /** One sentence naming what failed to unlock and why, suitable for chat or a log line. */
        public String explain(String what) {
            return opened()
                    ? "Opened the " + what + " gate."
                    : "No quest content was revealed for " + what + ": " + reason + ".";
        }
    }

    /**
     * Force-complete {@code gateCodeString} (the 16-digit hex id copied out of the quest editor, or written
     * by {@code testchain create}) for this player alone.
     *
     * <p>Every failure is soft - the assignment it belongs to still stands - but none of them is silent any
     * more: the result says exactly which one happened so the caller can tell the player.
     */
    public static GateResult completeGate(ServerPlayer player, String gateCodeString, String what) {
        GateResult result = completeGateQuietly(player, gateCodeString);
        if (result.opened()) {
            return result;
        }
        Apolinumarise.LOGGER.warn("[Quests] {} (player {}, configured id '{}').",
                result.explain(what), player.getGameProfile().getName(), gateCodeString);
        return result;
    }

    private static GateResult completeGateQuietly(ServerPlayer player, String gateCodeString) {
        if (gateCodeString == null || gateCodeString.isBlank()) {
            return GateResult.NOT_CONFIGURED;
        }
        if (!ApolinumQuests.isQuestsLoaded()) {
            return GateResult.QUESTS_NOT_INSTALLED;
        }
        return QuestGatesInternal.completeGate(player, gateCodeString);
    }

    /**
     * Check a configured gate id WITHOUT completing anything - what {@code /apolinumquests verifygates}
     * reports, and the fastest answer to "I picked a branch and nothing happened".
     */
    public static GateResult checkGate(ServerPlayer player, String gateCodeString) {
        if (gateCodeString == null || gateCodeString.isBlank()) {
            return GateResult.NOT_CONFIGURED;
        }
        if (!ApolinumQuests.isQuestsLoaded()) {
            return GateResult.QUESTS_NOT_INSTALLED;
        }
        return QuestGatesInternal.checkGate(player, gateCodeString);
    }

    /**
     * Un-complete every variant and branch gate for this player, and everything those gates were revealing,
     * putting them back to a genuinely ungated state. Returns how many quest objects were reset.
     *
     * <p>The gates come from config plus the placeholder test chain, so this works against authored content
     * and scaffolding alike. It does NOT touch this mod's own {@code SkillProfileData} - the caller clears
     * that, because the quest package has no business writing skill state.
     */
    public static int resetAllGates(ServerPlayer player) {
        if (!ApolinumQuests.isQuestsLoaded()) {
            return 0;
        }
        return QuestGatesInternal.resetGates(player, configuredGateIds());
    }

    /** How many known gates are still complete for this player - the check that a reset actually landed. */
    public static int completedGateCount(ServerPlayer player) {
        if (!ApolinumQuests.isQuestsLoaded()) {
            return 0;
        }
        return QuestGatesInternal.countCompletedGates(player, configuredGateIds());
    }

    /** Every configured gate id, variants first then branches - the order the config fields are in. */
    public static List<String> configuredGateIds() {
        List<String> configured = new ArrayList<>();
        configured.addAll(Config.INFECTED_VARIANT_GATE_QUEST_IDS.get());
        configured.addAll(Config.HEALTHY_BRANCH_GATE_QUEST_IDS.get());
        return configured;
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
