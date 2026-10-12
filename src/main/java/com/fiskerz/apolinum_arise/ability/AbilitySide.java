package com.fiskerz.apolinum_arise.ability;

import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.skill.SkillLogic;
import com.fiskerz.apolinum_arise.skill.SkillProfileData;

import net.minecraft.world.entity.player.Player;

/**
 * Which half of the game an ability belongs to. The two sides are mutually exclusive in play, and each has
 * its own notion of the sub-division an ability may be restricted to (see {@link Ability#restrictedTo}):
 * the infected side splits by rolled VARIANT, the healthy side by chosen BRANCH.
 */
public enum AbilitySide {
    /** Healthy players; the sub-division is the branch they picked on the choice screen. */
    HEALTHY {
        @Override
        public boolean holds(Player player) {
            return SkillLogic.hasHealthyAccess(player);
        }

        @Override
        public int subdivisionOf(Player player) {
            return SkillLogic.profile(player).healthyBranch();
        }
    },
    /** Infected players; the sub-division is the variant rolled at their first Blood Moon. */
    INFECTED {
        @Override
        public boolean holds(Player player) {
            return InfectionLogic.isInfected(player);
        }

        @Override
        public int subdivisionOf(Player player) {
            return SkillLogic.profile(player).infectedVariant();
        }
    };

    /** Whether this player is currently on this side at all. */
    public abstract boolean holds(Player player);

    /**
     * This player's variant (infected) or branch (healthy), or {@link SkillProfileData#UNASSIGNED} when they
     * have not been given one. An unassigned player matches no restricted ability.
     */
    public abstract int subdivisionOf(Player player);
}
