package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.quests.QuestGates;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative skill-access operations: granting each side, mutual exclusivity, the (not yet
 * wired) cure stub, and the two reuse-the-existing-hook entry points -
 * {@link #onIncubationComplete} (isIncubating -> isInfected) and {@link #onBloodMoonStart}.
 */
public final class SkillLogic {
    private SkillLogic() {}

    // ---------------------------------------------------------------- queries (also readable client-side)

    public static boolean hasHealthyAccess(Player player) {
        return player.getData(SkillAttachments.SKILL_ACCESS).healthyAccess();
    }

    public static boolean hasInfectedAccess(Player player) {
        return player.getData(SkillAttachments.SKILL_ACCESS).infectedAccess();
    }

    public static boolean hasAnyAccess(Player player) {
        return player.getData(SkillAttachments.SKILL_ACCESS).hasAny();
    }

    // ---------------------------------------------------------------- grants
    // (Player, not ServerPlayer, so gametests can drive them with mock players - the same pattern
    // InfectionLogic uses. Callers always pass a server-side player.)

    /**
     * Grants healthy-side access if not already held. Returns true if it was newly granted.
     *
     * <p>Phase 11: this is also the moment the three healthy stats are rolled. Deliberately in here rather
     * than in {@link SkillBookItem}, so the roll can never drift away from the grant - every path that
     * opens the healthy side rolls stats, by construction.
     */
    public static boolean grantHealthyAccess(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.healthyAccess()) {
            return false;
        }
        player.setData(SkillAttachments.SKILL_ACCESS, data.grantHealthy());
        Apolinumarise.LOGGER.debug("[Skill] Granted healthy-side access to {}.", player.getGameProfile().getName());
        HealthyStats.assignOnAccessGranted(player);
        return true;
    }

    /**
     * Grants infected-side access if not already held. Returns true if it was newly granted.
     *
     * <p>Phase 11: this is also the moment the infected variant is assigned, its reveal dream queued, and
     * its quest gate opened - see {@link InfectedVariants#assignOnAccessGranted}. Same reasoning as the
     * healthy stats above: the assignment lives with the grant so the two can never happen apart.
     */
    public static boolean grantInfectedAccess(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.infectedAccess()) {
            return false;
        }
        player.setData(SkillAttachments.SKILL_ACCESS, data.grantInfected());
        Apolinumarise.LOGGER.debug("[Skill] Granted infected-side access to {}.", player.getGameProfile().getName());
        InfectedVariants.assignOnAccessGranted(player);
        return true;
    }

    // ---------------------------------------------------------------- Phase 11 profile

    public static SkillProfileData profile(Player player) {
        return player.getData(SkillAttachments.SKILL_PROFILE);
    }

    /** True when a healthy-side player still owes us their one-time branch choice. */
    public static boolean needsBranchChoice(Player player) {
        return hasHealthyAccess(player) && !profile(player).hasHealthyBranch();
    }

    /**
     * Record the player's permanent branch choice and open that branch's quest gate for them alone.
     * Server-authoritative and strictly one-shot: an out-of-range index, a player without healthy-side
     * access, or a player who already chose is rejected without changing anything. Returns true only when
     * the choice was actually stored.
     */
    public static boolean chooseHealthyBranch(Player player, int branch) {
        if (!HealthyBranches.isValidIndex(branch)) {
            Apolinumarise.LOGGER.warn("[Skill] {} tried to choose branch {}, which is out of range 0..{}.",
                    player.getGameProfile().getName(), branch, HealthyBranches.COUNT - 1);
            return false;
        }
        if (!hasHealthyAccess(player)) {
            Apolinumarise.LOGGER.warn("[Skill] {} tried to choose a branch without healthy-side access.",
                    player.getGameProfile().getName());
            return false;
        }
        SkillProfileData data = profile(player);
        if (data.hasHealthyBranch()) {
            Apolinumarise.LOGGER.debug("[Skill] {} already chose branch {} - the choice is permanent, ignoring {}.",
                    player.getGameProfile().getName(), data.healthyBranch(), branch);
            return false;
        }
        player.setData(SkillAttachments.SKILL_PROFILE, data.withHealthyBranch(branch));
        Apolinumarise.LOGGER.info("[Skill] {} chose healthy branch {} (permanent).",
                player.getGameProfile().getName(), branch);
        // Same mechanism as the infected variants: complete this branch's gate only, and never touch the
        // other three - leaving them incomplete is what keeps their chapters hidden.
        if (player instanceof ServerPlayer serverPlayer) {
            openGateFor(serverPlayer, Config.getIndexed(Config.HEALTHY_BRANCH_GATE_QUEST_IDS, branch),
                    "healthy branch " + branch);
        }
        return true;
    }

    /**
     * Open one gate and, when it does not open, SAY SO to the player who just earned it.
     *
     * <p>This exists because the failure used to be invisible. A branch choice stored the branch, told the
     * player "you have committed to Branch I - there is no going back", and then quietly failed to reveal
     * anything because the configured id pointed at a quest that had been deleted and re-made. The
     * assignment is still correct and still permanent in that case - only the quest content is missing - so
     * the right response is to tell them rather than to roll the choice back.
     */
    static void openGateFor(ServerPlayer player, String gateCodeString, String what) {
        QuestGates.GateResult result = QuestGates.completeGate(player, gateCodeString, what);
        if (!result.opened()) {
            player.sendSystemMessage(Component.literal("[Apolinum] " + result.explain(what))
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- mutual exclusivity

    /** Clears healthy-side access and (future) healthy progress. Called when a player becomes infected. */
    public static void clearHealthySide(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.healthyAccess()) {
            player.setData(SkillAttachments.SKILL_ACCESS, data.clearedHealthy());
            Apolinumarise.LOGGER.debug("[Skill] Cleared healthy-side access from {} (became infected).", player.getGameProfile().getName());
        }
    }

    /**
     * The single "this player is no longer infected" path. Clears infected-side access and (future)
     * infected progress ONLY. Per the design a player who leaves the infected side does NOT regain healthy
     * access automatically - they must obtain a new book - so this clears nothing else.
     *
     * <p>Phase 10a bugfix: infected-side access is granted to every infected player at the next Blood Moon,
     * but nothing used to take it back when they stopped being infected, and the attachment is persisted +
     * copyOnDeath. A player moved back to healthy/incubating by {@code /infection set} therefore kept a
     * stale {@code infectedAccess}, which is all {@code hasAnyAccess} looks at - so the skill GUI still
     * opened for them. Every "left the infected side" transition now runs through here.
     */
    public static void onNoLongerInfected(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.infectedAccess()) {
            player.setData(SkillAttachments.SKILL_ACCESS, data.clearedInfected());
            Apolinumarise.LOGGER.debug("[Skill] Cleared stale infected-side access from {} (no longer infected).",
                    player.getGameProfile().getName());
        }
    }

    /**
     * STUB, ready for a future cure trigger (curing does not exist yet, so nothing calls this). A cure is
     * just one way of leaving the infected side, so it shares the implementation above.
     */
    public static void onPlayerCured(Player player) {
        onNoLongerInfected(player);
    }

    // ---------------------------------------------------------------- existing-hook entry points

    /**
     * Called from the single isIncubating -> isInfected transition hook (InfectionSymptoms#onBecameInfected):
     * bump the global completion counter (possibly unlocking the healthy system + placing books) and clear
     * this player's healthy-side access (mutual exclusivity - they are now infected).
     */
    public static void onIncubationComplete(Player player) {
        ServerLevel overworld = player.getServer().overworld();
        clearHealthySide(player);
        boolean justUnlocked = HealthySkillState.recordIncubationCompletion(overworld);
        if (justUnlocked) {
            ShrineBookInserter.placeInLoadedShrines(overworld);
        }
    }

    /**
     * Called from the Blood Moon false->true start hook (BloodMoonEvents#startBloodMoon): every currently
     * infected player who lacks infected-side access gets it now. This is what makes the infected unlock
     * land on "the next Blood Moon after infection" with no separate scheduling.
     */
    public static void onBloodMoonStart(ServerLevel overworld) {
        for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
            if (InfectionLogic.isInfected(player)) {
                grantInfectedAccess(player);
                // Backfill, deliberately outside the grant. grantInfectedAccess returns false when the
                // player ALREADY holds access, and would then skip the assignment entirely - which strands
                // anyone whose access predates Phase 11, or whose variant a debug reset has cleared. The
                // assignment is idempotent, so running it every Blood Moon is free for everyone else.
                InfectedVariants.assignOnAccessGranted(player);
            }
        }
    }

    /**
     * Same backfill for the healthy side: a player whose access predates Phase 11 has no rolled stats, and
     * the branch-choice screen would show them three zeroes. Cheap enough to check on every login, and a
     * no-op the moment they have been rolled once.
     */
    public static void onLogin(ServerPlayer player) {
        if (hasHealthyAccess(player) && !profile(player).statsAssigned()) {
            Apolinumarise.LOGGER.info("[Skill] {} holds healthy access with no rolled stats - backfilling.",
                    player.getGameProfile().getName());
            HealthyStats.assignOnAccessGranted(player);
        }
    }

    /**
     * Debug reset: forget this player's variant and branch so the normal assignment triggers can run again.
     * Their rolled stats and their access flags are left alone - access is what makes the triggers fire at
     * all, and re-rolling stats would change a number the player may already have seen.
     */
    public static void resetAssignments(ServerPlayer player) {
        player.setData(SkillAttachments.SKILL_PROFILE, profile(player).withoutAssignments());
        Apolinumarise.LOGGER.info("[Skill] Cleared {}'s variant and branch assignments.",
                player.getGameProfile().getName());
    }
}
