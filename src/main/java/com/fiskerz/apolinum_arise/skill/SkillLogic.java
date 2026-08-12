package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;

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

    /** Grants healthy-side access if not already held. Returns true if it was newly granted. */
    public static boolean grantHealthyAccess(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.healthyAccess()) {
            return false;
        }
        player.setData(SkillAttachments.SKILL_ACCESS, data.grantHealthy());
        Apolinumarise.LOGGER.debug("[Skill] Granted healthy-side access to {}.", player.getGameProfile().getName());
        return true;
    }

    /** Grants infected-side access if not already held. Returns true if it was newly granted. */
    public static boolean grantInfectedAccess(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.infectedAccess()) {
            return false;
        }
        player.setData(SkillAttachments.SKILL_ACCESS, data.grantInfected());
        Apolinumarise.LOGGER.debug("[Skill] Granted infected-side access to {}.", player.getGameProfile().getName());
        return true;
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
     * STUB, ready for a future cure trigger (curing does not exist yet, so nothing calls this). Clears
     * infected-side access and (future) infected progress ONLY. Per the design a cured player does NOT
     * regain healthy access automatically - they must obtain a new book - so this clears nothing else.
     */
    public static void onPlayerCured(Player player) {
        SkillAccessData data = player.getData(SkillAttachments.SKILL_ACCESS);
        if (data.infectedAccess()) {
            player.setData(SkillAttachments.SKILL_ACCESS, data.clearedInfected());
            Apolinumarise.LOGGER.debug("[Skill] Cleared infected-side access from {} (cured).", player.getGameProfile().getName());
        }
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
            }
        }
    }
}
