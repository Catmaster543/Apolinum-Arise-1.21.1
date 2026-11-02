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
 * @param unlocksWhenMet  whether meeting the condition IS the unlock (granted automatically when a dream is
 *                        marked watched), rather than something else - an item drop - doing the granting
 */
public record Ability(String id, AbilitySide side, OptionalInt restrictedTo, UnlockCondition unlockCondition,
                      OptionalInt keybindSlot, KeyPressHandler keyPressHandler, boolean unlocksWhenMet) {

    /** Lowest and highest ability keybind slot. Three slots, shared by every ability in the game. */
    public static final int MIN_KEYBIND_SLOT = 1;
    public static final int MAX_KEYBIND_SLOT = 3;

    /** An ability available to a whole side, with no variant/branch restriction and no keybind. */
    public static Ability of(String id, AbilitySide side, UnlockCondition unlockCondition) {
        return new Ability(id, side, OptionalInt.empty(), unlockCondition, OptionalInt.empty(), null, false);
    }

    /** An ability limited to one variant (infected) or one branch (healthy), with no keybind. */
    public static Ability restrictedTo(String id, AbilitySide side, int subdivision, UnlockCondition condition) {
        return new Ability(id, side, OptionalInt.of(subdivision), condition, OptionalInt.empty(), null, false);
    }

    /**
     * The same ability, granted automatically the moment its condition is met (checked whenever a dream is
     * marked watched - see {@link AbilityManager#grantMetAutoUnlocks}). Off by default because for some
     * abilities the condition is only a prerequisite: the Hive-Mind's is met by its dream, but the wand drop
     * is the unlock.
     */
    public Ability unlockedWhenMet() {
        return new Ability(id, side, restrictedTo, unlockCondition, keybindSlot, keyPressHandler, true);
    }

    /**
     * The same ability, triggered by one of the three shared ability keybind slots.
     *
     * <p>Kept as a copy-with rather than another constructor parameter so the many abilities that are not
     * keybind-driven - an item, a passive, a held input - declare nothing about keybinds at all.
     */
    public Ability withKeybind(int slot, KeyPressHandler handler) {
        if (slot < MIN_KEYBIND_SLOT || slot > MAX_KEYBIND_SLOT) {
            throw new IllegalArgumentException("Ability keybind slot must be "
                    + MIN_KEYBIND_SLOT + ".." + MAX_KEYBIND_SLOT + ", got " + slot);
        }
        return new Ability(id, side, restrictedTo, unlockCondition, OptionalInt.of(slot),
                java.util.Objects.requireNonNull(handler, "a keybind slot needs a handler"), unlocksWhenMet);
    }

    /** Whether this ability is the one that should answer a press of {@code slot} - before any player check. */
    public boolean usesKeybindSlot(int slot) {
        return keybindSlot.isPresent() && keybindSlot.getAsInt() == slot;
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
