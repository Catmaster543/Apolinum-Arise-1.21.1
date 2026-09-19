package com.fiskerz.apolinum_arise.skill;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.dream.DreamManager;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Which of the three infected variants a player turns out to be (Phase 11 item 2).
 *
 * <p>The roll happens at exactly one moment - {@link SkillLogic#grantInfectedAccess} succeeding, i.e. the
 * first Blood Moon after their infection completes - and never again. Three things fire together there,
 * because they are one event: the variant is written, its reveal dream is queued, and its FTB Quests gate
 * is completed for that player alone. The other two variants' gates are simply left alone, which is what
 * keeps their chapters hidden forever; there is no separate lock to apply or to get wrong.
 */
public final class InfectedVariants {
    private InfectedVariants() {}

    /** Fixed by design: three variants. */
    public static final int COUNT = 3;

    public static boolean isValidIndex(int index) {
        return index >= 0 && index < COUNT;
    }

    /**
     * Assign this player's variant if they do not have one yet, then queue its dream and open its gate.
     * Returns the variant they now hold - the existing one if this is a repeat call, so it is safe to run
     * from any "infected access granted" path without double-assigning.
     *
     * <p>Takes {@link Player} rather than {@link ServerPlayer} so gametests can drive it with a mock,
     * matching the rest of {@link SkillLogic}. The quest half only runs for a real server player.
     */
    public static int assignOnAccessGranted(Player player) {
        SkillProfileData profile = player.getData(SkillAttachments.SKILL_PROFILE);
        if (profile.hasInfectedVariant()) {
            Apolinumarise.LOGGER.debug("[Skill] {} already holds infected variant {} - not re-rolling.",
                    player.getGameProfile().getName(), profile.infectedVariant());
            return profile.infectedVariant();
        }

        int variant = roll(player.getRandom());
        player.setData(SkillAttachments.SKILL_PROFILE, profile.withInfectedVariant(variant));
        Apolinumarise.LOGGER.info("[Skill] {} was assigned infected variant {} (weights {}).",
                player.getGameProfile().getName(), variant, Config.INFECTED_VARIANT_WEIGHTS.get());

        // The reveal dream, queued at the same instant. It plays the next time they lie down; an id that
        // names no loaded script is skipped with a warning by the dream engine, which is exactly what the
        // placeholder defaults do until the real scripts are authored.
        String dreamId = Config.getIndexed(Config.INFECTED_VARIANT_DREAM_IDS, variant);
        if (dreamId.isBlank()) {
            Apolinumarise.LOGGER.debug("[Skill] No reveal dream configured for infected variant {}.", variant);
        } else {
            DreamManager.queueDream(player, dreamId);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            SkillLogic.openGateFor(serverPlayer,
                    Config.getIndexed(Config.INFECTED_VARIANT_GATE_QUEST_IDS, variant),
                    "infected variant " + variant);
        }
        return variant;
    }

    /**
     * Weighted pick over {@code infectedVariantWeights}. Falls back to a uniform pick when the weights are
     * missing, all zero, or otherwise unusable - a misconfigured weight list must never leave a player
     * without a variant, since this is their one and only chance to get one.
     */
    static int roll(RandomSource random) {
        List<? extends Double> weights = Config.INFECTED_VARIANT_WEIGHTS.get();
        double total = 0.0D;
        for (int i = 0; i < COUNT; i++) {
            total += weightAt(weights, i);
        }
        if (total <= 0.0D) {
            Apolinumarise.LOGGER.warn("[Skill] infectedVariantWeights sums to {} - falling back to a uniform "
                    + "pick so the player still gets a variant.", total);
            return random.nextInt(COUNT);
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < COUNT; i++) {
            roll -= weightAt(weights, i);
            if (roll < 0.0D) {
                return i;
            }
        }
        return COUNT - 1; // only reachable through floating-point drift on the very last boundary
    }

    private static double weightAt(List<? extends Double> weights, int index) {
        if (index >= weights.size()) {
            return 0.0D;
        }
        Double weight = weights.get(index);
        return weight == null || weight < 0.0D ? 0.0D : weight;
    }
}
