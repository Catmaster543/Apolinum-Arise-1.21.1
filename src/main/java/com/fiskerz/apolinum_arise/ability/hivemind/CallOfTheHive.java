package com.fiskerz.apolinum_arise.ability.hivemind;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.AbilityManager;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Call of the Hive (Phase 15): right-click the Hive-Mind wand to pull every mob in the surrounding chunks
 * to you. Branch 1's second ability, layered onto the wand Branch 1 already has.
 *
 * <p>The chain: unlocking the Hive-Mind queues the {@value #DREAM_ID} dream (see
 * {@code AbilityManager.unlockAbility}); watching it through is the whole unlock. A player without the
 * ability gets the wand's normal right-click - which is nothing - so the item is unchanged for them.
 *
 * <p>The cooldown lives in a persisted attachment as a game-time deadline, exactly like Digging In's, and
 * is mirrored onto the wand as the vanilla item cooldown purely for display. The attachment is what is
 * enforced; the vanilla cooldown is lost on relog/respawn and re-applied from it then.
 */
public final class CallOfTheHive {
    private CallOfTheHive() {}

    /** Registered ability id. */
    public static final String ABILITY_ID = "infected_branch1_callofthehive";

    /** The dream whose completion unlocks it, queued at the moment the Hive-Mind is unlocked. */
    public static final String DREAM_ID = "callofthehive_whispers";

    /** The infected variant this belongs to: Branch 1, variant index 0 - the same as the Hive-Mind. */
    public static final int VARIANT = HiveMind.VARIANT;

    /** Whether right-clicking the wand calls the hive for this player: unlocked AND still variant {@link #VARIANT}. */
    public static boolean isActiveFor(Player player) {
        return AbilityManager.canUse(player, ABILITY_ID);
    }

    /**
     * Server side of a right-click by someone who {@link #isActiveFor has the ability}. Returns how many mobs
     * were teleported; zero means nothing happened and the cooldown was NOT started - including when it was
     * already running, or when every eligible mob had nowhere safe to land.
     */
    public static int call(ServerLevel level, Player caster) {
        long now = level.getGameTime();
        CallOfTheHiveData data = caster.getData(HiveMindAttachments.CALL_OF_THE_HIVE);
        if (!data.readyAt(now)) {
            // The vanilla item cooldown normally stops the click before it gets here; this is the backstop.
            return 0;
        }
        CallOfTheHiveEffect.Outcome outcome = CallOfTheHiveEffect.call(level, caster,
                Config.CALL_OF_THE_HIVE_CHUNK_RADIUS.get(), Config.CALL_OF_THE_HIVE_SPREAD_RADIUS.get());
        Apolinumarise.LOGGER.debug("[CallOfTheHive] {} called {} of {} eligible mob(s).",
                caster.getGameProfile().getName(), outcome.moved(), outcome.eligible());
        if (outcome.moved() == 0) {
            return 0;
        }
        int ticks = cooldownTicks();
        caster.setData(HiveMindAttachments.CALL_OF_THE_HIVE, new CallOfTheHiveData(now + ticks));
        if (ticks > 0) {
            caster.getCooldowns().addCooldown(HiveMindRegistry.HIVE_MIND_STICK.get(), ticks);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        return outcome.moved();
    }

    /**
     * Put the remaining cooldown back on the wand's vanilla overlay. Vanilla item cooldowns live on the player
     * entity and die with it - on relog, on respawn, and client-side on a dimension change - so this runs at
     * each of those from the persisted deadline. The overlay restarts its sweep from full at that point, but
     * it empties at exactly the right moment.
     */
    public static void restoreCooldownDisplay(Player player) {
        long remaining = player.getData(HiveMindAttachments.CALL_OF_THE_HIVE)
                .remainingTicksAt(player.level().getGameTime());
        if (remaining > 0L) {
            player.getCooldowns().addCooldown(HiveMindRegistry.HIVE_MIND_STICK.get(),
                    (int) Math.min(remaining, Integer.MAX_VALUE));
        }
    }

    public static int cooldownTicks() {
        return (int) Math.round(Config.CALL_OF_THE_HIVE_COOLDOWN_SECONDS.get() * 20.0D);
    }
}
