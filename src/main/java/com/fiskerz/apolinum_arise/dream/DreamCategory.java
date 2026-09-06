package com.fiskerz.apolinum_arise.dream;

import com.fiskerz.apolinum_arise.infection.InfectionLogic;

import net.minecraft.world.entity.player.Player;

/**
 * The two broadcast audiences, reusing the existing infection-state split - the same one the skill system
 * uses for its two sides: INFECTED is {@code isInfected()}, HEALTHY is everyone else (clean or incubating).
 */
public enum DreamCategory {
    HEALTHY,
    INFECTED;

    public static DreamCategory of(Player player) {
        return InfectionLogic.isInfected(player) ? INFECTED : HEALTHY;
    }
}
