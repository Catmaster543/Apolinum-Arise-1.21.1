package com.fiskerz.apolinum_arise.ability;

import java.util.Set;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.world.entity.player.Player;

/**
 * Read and write a player's unlocked abilities. The whole public surface of the ability system for
 * everything outside this package.
 */
public final class AbilityManager {
    private AbilityManager() {}

    public static boolean hasAbility(Player player, String abilityId) {
        return player.getData(AbilityAttachments.ABILITIES).has(abilityId);
    }

    /**
     * Unlock an ability for this player. Returns true only when it was newly unlocked, so a caller can
     * tell a first grant from a repeat.
     *
     * <p>Deliberately does NOT re-check the unlock condition. The condition says when a player becomes
     * ELIGIBLE; the grant is a separate event that the triggering system owns - for the Hive-Mind wand,
     * receiving the item IS the unlock, and by then the condition has already been checked as part of drop
     * eligibility. Re-checking here would also make a legitimate admin grant impossible. An unknown id is
     * still refused, since that can only be a typo.
     */
    public static boolean unlockAbility(Player player, String abilityId) {
        if (Abilities.get(abilityId).isEmpty()) {
            Apolinumarise.LOGGER.warn("[Ability] Refusing to unlock unknown ability '{}' for {}.",
                    abilityId, player.getGameProfile().getName());
            return false;
        }
        AbilityData data = player.getData(AbilityAttachments.ABILITIES);
        AbilityData next = data.with(abilityId);
        if (next == data) {
            return false;
        }
        player.setData(AbilityAttachments.ABILITIES, next);
        Apolinumarise.LOGGER.info("[Ability] {} unlocked '{}'.", player.getGameProfile().getName(), abilityId);
        return true;
    }

    /** Debug only: abilities are permanent in normal play. Returns true if one was actually removed. */
    public static boolean revokeAbility(Player player, String abilityId) {
        AbilityData data = player.getData(AbilityAttachments.ABILITIES);
        AbilityData next = data.without(abilityId);
        if (next == data) {
            return false;
        }
        player.setData(AbilityAttachments.ABILITIES, next);
        Apolinumarise.LOGGER.info("[Ability] {} lost '{}' (debug revoke).",
                player.getGameProfile().getName(), abilityId);
        return true;
    }

    public static Set<String> unlocked(Player player) {
        return player.getData(AbilityAttachments.ABILITIES).unlocked();
    }
}
