package com.fiskerz.apolinum_arise.ability;

import com.fiskerz.apolinum_arise.dream.DreamManager;

import net.minecraft.world.entity.player.Player;

/**
 * What a player must have done before an ability is allowed to unlock.
 *
 * <p>Deliberately an interface with one implementation rather than an enum or a flag: "watched dream X" is
 * only the first condition type this system needs, and later ones (a quest completed, a boss killed, a
 * structure reached) are new records here and nothing else. {@link Ability} stores one of these and never
 * looks inside it, so adding a type touches no existing code.
 *
 * <p>A condition is a PREREQUISITE, not the unlock itself. It says the player is now allowed to obtain the
 * ability; something else - looting an item, completing a quest - is what actually calls
 * {@link AbilityManager#unlockAbility}.
 */
public interface UnlockCondition {

    boolean isMet(Player player);

    /** One short phrase for debug readouts and logs, e.g. {@code dream 'hivemind_whispers' watched}. */
    String describe();

    /** Nothing to do: the ability is available to anyone on its side as soon as it is granted. */
    UnlockCondition NONE = new UnlockCondition() {
        @Override
        public boolean isMet(Player player) {
            return true;
        }

        @Override
        public String describe() {
            return "no prerequisite";
        }
    };

    /** The player has watched {@code dreamId} through to its end at least once. */
    record DreamWatched(String dreamId) implements UnlockCondition {
        @Override
        public boolean isMet(Player player) {
            return DreamManager.hasWatched(player, dreamId);
        }

        @Override
        public String describe() {
            return "dream '" + dreamId + "' watched";
        }
    }
}
