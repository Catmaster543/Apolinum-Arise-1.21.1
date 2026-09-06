package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The three healthy-side stats (Phase 11 item 3): rolled once, at the moment the book grants healthy-side
 * access, and never again.
 *
 * <p>They are written and then left alone. Nothing reads them until the player opens the skill screen for
 * the first time and the branch-choice screen displays them - there is deliberately no command, no HUD and
 * no gameplay effect yet, because what the numbers actually DO is content for a later task.
 */
public final class HealthyStats {
    private HealthyStats() {}

    /**
     * Roll all three stats if this player has not been rolled yet. Independent rolls, each uniform over
     * [healthyStatMin, healthyStatMax]. Returns the profile now in force, so callers can log or inspect it.
     */
    public static SkillProfileData assignOnAccessGranted(Player player) {
        SkillProfileData profile = player.getData(SkillAttachments.SKILL_PROFILE);
        if (profile.statsAssigned()) {
            Apolinumarise.LOGGER.debug("[Skill] {} already has rolled stats - not re-rolling.",
                    player.getGameProfile().getName());
            return profile;
        }
        RandomSource random = player.getRandom();
        SkillProfileData rolled = profile.withStats(rollOne(random), rollOne(random), rollOne(random));
        player.setData(SkillAttachments.SKILL_PROFILE, rolled);
        Apolinumarise.LOGGER.info("[Skill] {} rolled INT={} STR={} CRE={} (range {}..{}).",
                player.getGameProfile().getName(), rolled.intelligence(), rolled.strength(),
                rolled.creativity(), Config.HEALTHY_STAT_MIN.get(), Config.HEALTHY_STAT_MAX.get());
        return rolled;
    }

    /** One independent stat roll. A max below the min degrades to a fixed value rather than throwing. */
    static int rollOne(RandomSource random) {
        int min = Config.HEALTHY_STAT_MIN.get();
        int max = Math.max(min, Config.HEALTHY_STAT_MAX.get());
        return min == max ? min : min + random.nextInt(max - min + 1);
    }
}
