package com.fiskerz.apolinum_arise.ability;

import java.util.OptionalInt;

import com.fiskerz.apolinum_arise.skill.SkillProfileData;

import net.minecraft.world.entity.player.Player;

/**
 * One ability's definition: who may have it, and what they must have done first.
 *
 * <p>This is the DESCRIPTOR only. It holds no behaviour - what an ability actually does lives with the
 * thing that triggers it (an item, an event handler, later a keybind), which asks
 * {@link AbilityManager#hasAbility} and acts accordingly. Keeping the registry behaviour-free is what lets
 * abilities be triggered by completely different mechanisms without the registry knowing about any of them.
 *
 * @param id             stable string id, also the key persisted in a player's unlocked set
 * @param side           which half of the game this belongs to
 * @param restrictedTo   the variant (infected) or branch (healthy) this is limited to, or empty for an
 *                       ability the whole side can have - not every ability needs a restriction
 * @param unlockCondition what must be true before the ability may be unlocked
 */
public record Ability(String id, AbilitySide side, OptionalInt restrictedTo, UnlockCondition unlockCondition) {

    /** An ability available to a whole side, with no variant/branch restriction. */
    public static Ability of(String id, AbilitySide side, UnlockCondition unlockCondition) {
        return new Ability(id, side, OptionalInt.empty(), unlockCondition);
    }

    /** An ability limited to one variant (infected) or one branch (healthy). */
    public static Ability restrictedTo(String id, AbilitySide side, int subdivision, UnlockCondition condition) {
        return new Ability(id, side, OptionalInt.of(subdivision), condition);
    }

    /**
     * Whether this player is the right KIND of player for this ability - correct side, and the right
     * variant/branch when it is restricted. Says nothing about whether they have earned it.
     *
     * <p>A player with no variant/branch assigned ({@link SkillProfileData#UNASSIGNED}) matches no
     * restricted ability, which is what keeps an ability from leaking to someone mid-assignment.
     */
    public boolean appliesTo(Player player) {
        if (!side.holds(player)) {
            return false;
        }
        if (restrictedTo.isEmpty()) {
            return true;
        }
        int subdivision = side.subdivisionOf(player);
        return subdivision != SkillProfileData.UNASSIGNED && subdivision == restrictedTo.getAsInt();
    }

    /** Whether this player could unlock it right now: right kind of player, and the prerequisite is met. */
    public boolean isUnlockableBy(Player player) {
        return appliesTo(player) && unlockCondition.isMet(player);
    }

    public String describeRestriction() {
        return restrictedTo.isEmpty()
                ? side + " (any)"
                : side + " " + (side == AbilitySide.INFECTED ? "variant " : "branch ") + restrictedTo.getAsInt();
    }
}
