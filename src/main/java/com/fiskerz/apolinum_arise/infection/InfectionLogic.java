package com.fiskerz.apolinum_arise.infection;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Patch 2 infection state machine. No visible consequence of being infected exists yet other
 * than gating Blood Moon susceptibility - later work builds on this.
 */
public final class InfectionLogic {
    private InfectionLogic() {}

    // Same day-counting the Blood Moon nightly check uses, so incubation lands on the same boundary.
    private static int currentDay(Level level) {
        return (int) (level.getDayTime() / Level.TICKS_PER_DAY);
    }

    /** Called after a mosquito bite lands. Rolls incubation only for a clean (not yet touched) player. */
    public static void onBite(Player player, ServerLevel level) {
        InfectionData data = player.getData(InfectionAttachments.INFECTION);
        if (data.incubating() || data.infected()) {
            return; // already in the state machine: no re-roll, no effect
        }
        if (level.getRandom().nextDouble() < Config.INFECTION_CHANCE_PER_BITE.get()) {
            int day = currentDay(level);
            player.setData(InfectionAttachments.INFECTION, data.beginIncubating(day));
            Apolinumarise.LOGGER.debug("Infection: {} began incubating on day {}.", player.getGameProfile().getName(), day);
        }
    }

    /**
     * Runs on the Blood Moon dusk-detection boundary for every online player (any dimension): an
     * incubating player whose incubation window has elapsed becomes fully infected.
     */
    public static void onDuskTransition(ServerLevel overworld) {
        int day = currentDay(overworld);
        for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
            promoteIfDue(player, day);
        }
    }

    /** Catch-up for a player who was offline across the boundary; their attachment persisted. */
    public static void onLogin(ServerPlayer player) {
        promoteIfDue(player, currentDay(player.serverLevel()));
    }

    // Package-visible for gametests (which use mock Players).
    static void promoteIfDue(Player player, int currentDay) {
        InfectionData data = player.getData(InfectionAttachments.INFECTION);
        if (data.incubating() && currentDay - data.infectionStartDay() >= Config.INFECTION_INCUBATION_DAYS.get()) {
            player.setData(InfectionAttachments.INFECTION, data.becomeInfected());
            Apolinumarise.LOGGER.debug("Infection: {} became fully infected on day {}.", player.getGameProfile().getName(), currentDay);
        }
    }

    public static boolean isInfected(Player player) {
        return player.getData(InfectionAttachments.INFECTION).infected();
    }
}
