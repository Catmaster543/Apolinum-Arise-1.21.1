package com.fiskerz.apolinum_arise.infection;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonState;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.downed.DownedManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative core of the Phase 8 bite bar and player-to-player spread. An infected player builds
 * a 0-100 "bite" charge (stored in {@link InfectionData#biteBar()}); at full charge they can bite a downed
 * healthy player to roll starting that target's incubation.
 *
 * <p>The fill is a list of independent {@link FillContributor}s (darkness is the first). Adding a new
 * contribution source later is a single list entry - the tick loop never changes.
 */
public final class BiteBar {
    private BiteBar() {}

    /** One independent source of bar fill. Returns the percent-of-bar to add for this player THIS TICK. */
    @FunctionalInterface
    public interface FillContributor {
        float perTick(ServerPlayer player);
    }

    // Ordered, independent fill sources. More are planned; each is just another entry here.
    private static final List<FillContributor> CONTRIBUTORS = List.of(
            BiteBar::darknessContribution
    );

    private static final int TICKS_PER_20_SECONDS = 400;
    // Write the synced+persisted attachment at most once/second instead of every tick, so a slowly filling
    // bar doesn't spam a sync packet 20x/second. The unflushed fraction (< one tick of fill) is transient.
    private static final int SYNC_FLUSH_INTERVAL_TICKS = 20;

    // Transient fractional accumulator, so setData (and its resync) happens ~1/s, not every tick.
    private static final Map<UUID, Float> PENDING = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ per-tick fill

    /** Called every tick for an infected player (from InfectionSymptoms). No-op unless a contributor fires. */
    public static void tickFill(ServerPlayer player, long gameTime) {
        InfectionData data = player.getData(InfectionAttachments.INFECTION);
        if (!data.infected() || data.biteBar() >= 100.0F) {
            PENDING.remove(player.getUUID()); // already full, or no longer infected: nothing to accrue
            return;
        }
        float delta = 0.0F;
        for (FillContributor contributor : CONTRIBUTORS) {
            delta += Math.max(0.0F, contributor.perTick(player));
        }
        // A separately-tunable boost applied on top of the summed contributor rate while a Blood Moon
        // is active (state anchored to the Overworld, so it holds regardless of the player's dimension).
        if (delta > 0.0F && BloodMoonState.isActive(player.serverLevel())) {
            delta *= (float) (double) Config.BITE_BAR_BLOOD_MOON_MULTIPLIER.get();
        }
        if (delta > 0.0F) {
            PENDING.merge(player.getUUID(), delta, Float::sum);
        }
        Float pending = PENDING.get(player.getUUID());
        if (pending == null || pending <= 0.0F) {
            return;
        }
        // Flush on the interval, or immediately if this fill would top the bar out (so 100% lands promptly).
        if (gameTime % SYNC_FLUSH_INTERVAL_TICKS == 0L || data.biteBar() + pending >= 100.0F) {
            player.setData(InfectionAttachments.INFECTION, data.withBiteBar(data.biteBar() + pending));
            PENDING.remove(player.getUUID());
        }
    }

    // First contributor: fill while in mob-spawn-level darkness (time-adjusted effective light, not raw sky).
    private static float darknessContribution(ServerPlayer player) {
        return isInQualifyingDarkness(player)
                ? (float) (Config.BITE_BAR_FILL_RATE_PERCENT_PER_20SEC.get() / TICKS_PER_20_SECONDS)
                : 0.0F;
    }

    /**
     * True when the position's EFFECTIVE, time-of-day-adjusted brightness is at or below the threshold.
     * {@code getMaxLocalRawBrightness} folds block light with sky light reduced by the current sky-darken
     * (so open sky reads bright by day and dark by night) - the same "actually visible" value hostile-mob
     * spawning uses - rather than the unadjusted raw {@code getBrightness(LightLayer.SKY, ...)}.
     */
    public static boolean isInQualifyingDarkness(ServerPlayer player) {
        int effectiveLight = player.level().getMaxLocalRawBrightness(player.blockPosition());
        return effectiveLight <= Config.BITE_BAR_DARKNESS_THRESHOLD.get();
    }

    // ------------------------------------------------------------------ bite resolution

    /**
     * Server-authoritative resolution of a single bite press. Validates the biter (infected + full bar) and
     * the target (downed + healthy + in range/faced); on a valid attempt plays the SFX, rolls infection, and
     * resets the biter's bar to 0 - regardless of whether the infection roll succeeded.
     */
    public static void attemptBite(ServerPlayer biter, int targetId) {
        InfectionData biterData = biter.getData(InfectionAttachments.INFECTION);
        if (!biterData.infected() || biterData.biteBar() < 100.0F) {
            return; // must be fully infected with a full bar
        }
        if (!(biter.level().getEntity(targetId) instanceof ServerPlayer target)
                || target == biter
                || !DownedManager.isDowned(target)
                || !InfectionLogic.isHealthy(target)
                || !withinBiteRange(biter, target)
                || !(biter.level() instanceof ServerLevel serverLevel)) {
            return; // not a valid bite: no attempt, so no sound and no bar reset
        }

        // Reuse the normal attack swing as a free animation stand-in (broadcast to nearby clients).
        biter.swing(InteractionHand.MAIN_HAND, true);
        // SFX on EVERY attempt, success or failure.
        serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                BloodMoonRegistry.BITE_SOUND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        boolean success = biter.getRandom().nextDouble() < Config.BITE_INFECTION_CHANCE.get();
        if (success) {
            // Same infection-start path the mosquito bite uses (Phase 4/5) - no duplicated transition logic.
            InfectionLogic.startIncubation(target, serverLevel);
        }
        // Reset the biter's bar on BOTH success and failure.
        biter.setData(InfectionAttachments.INFECTION, biterData.withBiteBar(0.0F));
        PENDING.remove(biter.getUUID());
        Apolinumarise.LOGGER.debug("Bite: {} bit {} (infectionRoll={}).",
                biter.getGameProfile().getName(), target.getGameProfile().getName(), success);
    }

    /** Server-side reach test mirroring the revive check: within range and roughly faced. */
    public static boolean withinBiteRange(ServerPlayer biter, ServerPlayer target) {
        double range = Config.BITE_RANGE.get();
        Vec3 eye = biter.getEyePosition();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
        if (toTarget.length() > range) {
            return false;
        }
        return biter.getLookAngle().dot(toTarget.normalize()) >= 0.8D;
    }
}
