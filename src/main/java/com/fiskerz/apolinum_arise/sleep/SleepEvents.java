package com.fiskerz.apolinum_arise.sleep;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side NeoForge glue for the Phase 10a sleep system. Registered from the main mod class.
 */
public final class SleepEvents {
    private SleepEvents() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        SleepManager.onServerTick(event.getServer());
    }

    /**
     * Vanilla refuses to let anyone into a bed during the day ({@code ServerPlayer.startSleepInBed} returns
     * {@link Player.BedSleepingProblem#NOT_POSSIBLE_NOW} when {@code level.isDay()}), but the sleep bar is
     * meant to refill from the act of lying down at ANY time - just at the reduced day rate. Allow it.
     *
     * <p>Reaching this handler with exactly NOT_POSSIBLE_NOW means every other vanilla condition already
     * passed (alive, natural dimension, in range, not obstructed, bed not occupied) and the respawn point
     * has already been set, so there is nothing to re-validate here.
     *
     * <p>We lay the player down ourselves instead of letting vanilla continue, because vanilla's own path
     * ends in {@code ServerLevel.updateSleepingPlayerList()}, which would enter a daytime napper into the
     * level's sleep status. Once counted, 100 ticks later {@code ServerLevel.tick} would treat them as
     * "everyone is asleep", skip a whole day of world time, and eject them from the bed - so a daytime nap
     * would last five seconds and cost a day. Going through {@link net.minecraft.world.entity.LivingEntity}
     * directly keeps them out of that bookkeeping while still setting the sleeping pose and position, which
     * is all the client needs: it opens the "Leave Bed" screen purely from the synced {@code isSleeping()}.
     */
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            return; // some other refusal, or already allowed - leave vanilla alone
        }
        ServerPlayer player = event.getEntity();
        player.startSleeping(event.getPos());
        // OTHER_PROBLEM carries no message, so vanilla stops here silently rather than telling the player
        // they "can only sleep at night" - they are, in fact, now lying in the bed.
        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        Apolinumarise.LOGGER.debug("[Sleep] {} lay down during the day at {} (reduced refill rate).",
                player.getGameProfile().getName(), event.getPos());
    }

    /**
     * The daytime refusal is re-checked every tick in {@code Player.tick}, which would kick a daytime
     * napper straight back out. Only NOT_POSSIBLE_NOW (the day check) is overridden - NOT_POSSIBLE_HERE
     * from {@code checkBedExists} still wakes them when the bed is destroyed underneath them.
     */
    public static void onCanContinueSleeping(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof Player
                && event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            event.setContinueSleeping(true);
        }
    }

    // Vanilla decided the night is being skipped (time jumps to morning). Fired while the sleepers are
    // still asleep, so we can credit exactly the players who slept through it.
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SleepManager.onSleepFinished(level);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SleepManager.onLogin(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SleepManager.onLogout(player);
        }
    }
}
