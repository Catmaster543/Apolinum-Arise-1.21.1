package com.fiskerz.apolinum_arise.skill;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.Abilities;
import com.fiskerz.apolinum_arise.ability.AbilitySide;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.dream.DreamManager;
import com.fiskerz.apolinum_arise.quests.QuestGates;

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
 *
 * <p>The one exception is {@link #switchTo}, behind the op-only {@code /apolinumskills setvariant}: a
 * testing tool that undoes the old variant's dream and gate and lands the new one through the same step.
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
        landVariant(player, variant);
        return variant;
    }

    /** What {@link #switchTo} did, for the admin command to report back. */
    public record Switch(int previous, int variant, List<String> unqueuedDreams, int gateObjectsClosed,
                         List<String> queuedDreams, @Nullable QuestGates.GateResult gate) {
        public boolean changed() {
            return previous != variant;
        }
    }

    /**
     * Admin/debug: put this player on {@code variant} as if their Blood Moon roll had landed there,
     * replacing whatever they held. Exists so variant testing does not mean re-rolling worlds until the
     * dice cooperate.
     *
     * <p>"As if it had landed there" is literal. The OLD variant's still-queued dreams are taken back out
     * and its quest gate (and what it was revealing) is closed again, then the new variant goes through
     * {@link #landVariant} - the very same step the real roll runs. A player who has never had a variant
     * just gets the second half.
     *
     * <p>Abilities are deliberately NOT revoked. Ones earned on another variant go dormant, because every
     * behaviour hook asks {@code AbilityManager.canUse}, which also requires the current variant - and they
     * wake up again on switching back, so a tester can earn all three once and then flip between them.
     * {@code /apolinumability revoke} is still there for a clean-slate run.
     *
     * <p>Switching to the variant they already hold changes nothing and says so ({@link Switch#changed}).
     */
    public static Switch switchTo(Player player, int variant) {
        if (!isValidIndex(variant)) {
            throw new IllegalArgumentException("infected variant " + variant + " is outside 0.." + (COUNT - 1));
        }
        SkillProfileData profile = player.getData(SkillAttachments.SKILL_PROFILE);
        int previous = profile.infectedVariant();
        if (previous == variant) {
            return new Switch(previous, variant, List.of(), 0, List.of(), null);
        }

        List<String> unqueued = new ArrayList<>();
        int closed = 0;
        if (isValidIndex(previous)) {
            for (String dreamId : dreamsFor(previous)) {
                if (DreamManager.unqueueDream(player, dreamId)) {
                    unqueued.add(dreamId);
                }
            }
            if (player instanceof ServerPlayer serverPlayer) {
                closed = QuestGates.resetGate(serverPlayer,
                        Config.getIndexed(Config.INFECTED_VARIANT_GATE_QUEST_IDS, previous));
            }
        }

        player.setData(SkillAttachments.SKILL_PROFILE, profile.withInfectedVariant(variant));
        Apolinumarise.LOGGER.info("[Skill] {} was switched from infected variant {} to {} by command.",
                player.getGameProfile().getName(), previous, variant);
        Landed landed = landVariant(player, variant);
        return new Switch(previous, variant, List.copyOf(unqueued), closed, landed.queuedDreams(), landed.gate());
    }

    private record Landed(List<String> queuedDreams, @Nullable QuestGates.GateResult gate) {}

    /**
     * Everything that happens together with a variant landing, shared by the roll and the admin switch so a
     * switched player is indistinguishable from one who rolled it: the variant's dreams are queued and its
     * quest gate is opened for this player alone.
     */
    private static Landed landVariant(Player player, int variant) {
        // The reveal dream first, then - Phase 12/13 - the prerequisite dream of every ability restricted to
        // this variant, so their unlock conditions become reachable the moment the player qualifies and never
        // for any other variant. An id naming no loaded script is skipped with a warning by the dream
        // engine, which is exactly what the placeholder reveal ids do until the real scripts are authored.
        List<String> dreams = dreamsFor(variant);
        for (String dreamId : dreams) {
            DreamManager.queueDream(player, dreamId);
            Apolinumarise.LOGGER.info("[Skill] Queued dream '{}' for {} (infected variant {}).",
                    dreamId, player.getGameProfile().getName(), variant);
        }

        QuestGates.GateResult gate = null;
        if (player instanceof ServerPlayer serverPlayer) {
            gate = SkillLogic.openGateFor(serverPlayer,
                    Config.getIndexed(Config.INFECTED_VARIANT_GATE_QUEST_IDS, variant),
                    "infected variant " + variant);
        }
        return new Landed(dreams, gate);
    }

    /**
     * The dreams that belong to a variant: its configured reveal dream (when there is one) followed by its
     * abilities' prerequisite dreams. Driven off the ability registry rather than a list of ids here, so
     * registering a new variant ability with a dream prerequisite needs no change in this class.
     */
    public static List<String> dreamsFor(int variant) {
        List<String> dreams = new ArrayList<>();
        String reveal = Config.getIndexed(Config.INFECTED_VARIANT_DREAM_IDS, variant);
        if (!reveal.isBlank()) {
            dreams.add(reveal);
        }
        for (String prerequisite : Abilities.prerequisiteDreamsFor(AbilitySide.INFECTED, variant)) {
            if (!dreams.contains(prerequisite)) {
                dreams.add(prerequisite);
            }
        }
        return List.copyOf(dreams);
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
