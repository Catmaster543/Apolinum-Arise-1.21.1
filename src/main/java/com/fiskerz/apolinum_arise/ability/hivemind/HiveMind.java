package com.fiskerz.apolinum_arise.ability.hivemind;

import com.fiskerz.apolinum_arise.ability.AbilityManager;
import com.fiskerz.apolinum_arise.dream.DreamManager;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.skill.SkillLogic;

import net.minecraft.world.entity.player.Player;

/**
 * The Hive-Mind ability: Phase 12's one real ability, used to prove the registry/unlock architecture
 * against something that actually does a thing.
 *
 * <p>The chain end to end: an infected player rolls <b>variant 1</b> at their first Blood Moon, which
 * queues the {@value #DREAM_ID} dream; watching that dream to the end satisfies the ability's unlock
 * condition; from then on, killing a mob has a small chance to drop the wand, and receiving the wand IS
 * the unlock - there is no separate activation. Swinging an unlocked wand turns everything nearby on
 * whatever it just hit.
 */
public final class HiveMind {
    private HiveMind() {}

    /** Registered ability id. */
    public static final String ABILITY_ID = "infected_branch1_hivemind";

    /** The dream whose completion is the prerequisite, queued when the variant is assigned. */
    public static final String DREAM_ID = "hivemind_whispers";

    /** The infected variant this ability belongs to - "Branch 1" in the task, variant index 1. */
    public static final int VARIANT = 1;

    /**
     * Whether the wand may drop for this player at all. Checked BEFORE any random roll, so a player who
     * does not qualify never rolls - the drop simply does not exist for them.
     *
     * <p>All four conditions matter and each rules out a different mistake: still infected (a cured player
     * keeps the variant field but not the side), the right variant, the dream actually watched, and not
     * already unlocked - the wand is the unlock, so there is nothing to gain from a second one.
     */
    public static boolean canDropFor(Player player) {
        return InfectionLogic.isInfected(player)
                && SkillLogic.profile(player).infectedVariant() == VARIANT
                && DreamManager.hasWatched(player, DREAM_ID)
                && !AbilityManager.hasAbility(player, ABILITY_ID);
    }

    /** Whether this player's swings should trigger the hive-mind effect. Possessing the wand is not enough. */
    public static boolean isUnlockedFor(Player player) {
        return AbilityManager.hasAbility(player, ABILITY_ID);
    }
}
