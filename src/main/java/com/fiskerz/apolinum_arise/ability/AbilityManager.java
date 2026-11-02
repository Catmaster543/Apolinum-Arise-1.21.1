package com.fiskerz.apolinum_arise.ability;

import java.util.Set;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.dream.DreamManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read and write a player's unlocked abilities. The whole public surface of the ability system for
 * everything outside this package.
 */
public final class AbilityManager {
    private AbilityManager() {}

    /** Whether this player has ever earned the ability. NOT whether it works for them right now - see {@link #canUse}. */
    public static boolean hasAbility(Player player, String abilityId) {
        return player.getData(AbilityAttachments.ABILITIES).has(abilityId);
    }

    /**
     * Whether this player can use the ability right now: earned it, AND is currently the kind of player it
     * belongs to (side and variant/branch). Every behaviour hook should ask this rather than
     * {@link #hasAbility}.
     *
     * <p>The two answers differ once a player's side or variant changes after the unlock - an infection
     * cured, or {@code /apolinumskills setvariant}. The ability then goes dormant instead of being revoked,
     * and comes back by itself if they return to the variant that earned it. Before the admin switch
     * existed, the variant could never change after an unlock, so checking the unlock alone happened to be
     * enough; with the switch it would have left a variant-1 punch working on a variant-0 player.
     */
    public static boolean canUse(Player player, String abilityId) {
        return hasAbility(player, abilityId)
                && Abilities.get(abilityId).map(ability -> ability.appliesTo(player)).orElse(false);
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
        // Abilities chained onto this one have their dream queued now - the Hive-Mind's unlock queues the
        // Call of the Hive dream. Here rather than in any one grant path, so the wand drop, the grantwand
        // command and a plain /apolinumability unlock all behave the same.
        for (String dreamId : Abilities.dreamsQueuedOnUnlockOf(abilityId)) {
            DreamManager.queueDream(player, dreamId);
        }
        return true;
    }

    /**
     * Grant every {@linkplain Ability#unlocksWhenMet auto-unlocking} ability this player now qualifies for.
     * Called whenever a dream is marked watched, which is the only event that can complete such a condition
     * today. Idempotent: abilities already held are skipped.
     */
    public static void grantMetAutoUnlocks(Player player) {
        for (Ability ability : Abilities.all()) {
            if (ability.unlocksWhenMet() && !hasAbility(player, ability.id()) && ability.isUnlockableBy(player)) {
                unlockAbility(player, ability.id());
            }
        }
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

    // ---------------------------------------------------------------- keybind slot routing (Phase 14)

    /**
     * Route a press of one of the three shared ability slots to whichever ability this player actually has
     * in it, and do nothing at all otherwise.
     *
     * <p>At most one ability can match: slots are declared per ability, and the side/variant restrictions
     * are mutually exclusive, so a player can never hold two abilities claiming the same slot. The loop
     * returns on the first match anyway, and logs if a second one would also have matched - that would mean
     * two abilities were registered into the same slot for the same side and variant, which is an authoring
     * mistake worth hearing about rather than resolving silently.
     *
     * <p>Silence is the correct response to an unmatched press: an unbound slot, a slot whose ability the
     * player has not unlocked, or a player on the wrong side must give away nothing.
     */
    public static void onKeybindSlotPressed(ServerPlayer player, int slot) {
        if (slot < Ability.MIN_KEYBIND_SLOT || slot > Ability.MAX_KEYBIND_SLOT) {
            return;
        }
        Ability matched = Abilities.findKeybindAbility(player, slot).orElse(null);
        if (matched == null || matched.keyPressHandler() == null) {
            return;
        }
        Apolinumarise.LOGGER.debug("[Ability] Slot {} -> '{}' for {}.",
                slot, matched.id(), player.getGameProfile().getName());
        matched.keyPressHandler().onKeyPress(player);
    }
}
