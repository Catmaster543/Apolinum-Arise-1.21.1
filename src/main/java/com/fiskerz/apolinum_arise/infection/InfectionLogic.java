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
            startIncubation(player, level);
        }
    }

    /**
     * Begins incubation for a clean player, guarded so an already-incubating/infected player is untouched.
     * This is the single infection-start path shared by the mosquito bite (Phase 4) and the Phase 8
     * player-to-player bite - the roll that decides WHETHER to start lives in each caller, but the actual
     * transition (start-day stamp, attachment write, diagnostics) is here so there is exactly one copy.
     */
    public static void startIncubation(Player player, ServerLevel level) {
        InfectionData data = player.getData(InfectionAttachments.INFECTION);
        if (data.incubating() || data.infected()) {
            return;
        }
        int day = currentDay(level);
        player.setData(InfectionAttachments.INFECTION, data.beginIncubating(day));
        // [Phase 6 A2 diagnostics] record exactly what start-day and time-of-day the infection lands on.
        Apolinumarise.LOGGER.info("[InfectionDay] BITE {} startDay={} rawDayTime={} timeOfDay={} (night={})",
                player.getGameProfile().getName(), day, level.getDayTime(), level.getDayTime() % Level.TICKS_PER_DAY, level.isNight());
    }

    /**
     * Runs on the Blood Moon dusk-detection boundary for every online player (any dimension): an
     * incubating player whose incubation window has elapsed becomes fully infected.
     */
    public static void onDuskTransition(ServerLevel overworld) {
        int day = currentDay(overworld);
        // [Phase 6 A2 diagnostics] the exact tick/day this dusk boundary is evaluated against.
        Apolinumarise.LOGGER.info("[InfectionDay] DUSK-CHECK currentDay={} rawDayTime={} timeOfDay={} night={}",
                day, overworld.getDayTime(), overworld.getDayTime() % Level.TICKS_PER_DAY, overworld.isNight());
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
        if (!data.incubating()) {
            return;
        }
        int delta = currentDay - data.infectionStartDay();
        int incubationDays = Config.INFECTION_INCUBATION_DAYS.get();
        // Phase 6 A2 fix. Root cause (see [InfectionDay] logs): the previous condition `delta >= incubationDays`
        // completed incubation at delta == incubationDays, i.e. dayNumber incubationDays+1 - one moonrise
        // too late (day 11 for a 10-day incubation). The intended point is the moonrise of day N itself,
        // where day N = dayNumber == incubationDays == delta+1, i.e. delta == incubationDays-1.
        boolean due = delta >= Math.max(0, incubationDays - 1);
        // [Phase 6 A2 diagnostics] the actual day-count delta at every check and the threshold it compares
        // against, so the fix is observed in logs rather than guessed.
        Apolinumarise.LOGGER.info("[InfectionDay] CHECK {} startDay={} currentDay={} delta={} incubationDays={} due={} (dayNumber={})",
                player.getName().getString(), data.infectionStartDay(), currentDay, delta, incubationDays, due, delta + 1);
        if (due) {
            player.setData(InfectionAttachments.INFECTION, data.becomeInfected());
            Apolinumarise.LOGGER.info("[InfectionDay] PROMOTE {} -> infected at currentDay={} delta={} (dayNumber={})",
                    player.getName().getString(), currentDay, delta, delta + 1);
        }
    }

    public static boolean isInfected(Player player) {
        return player.getData(InfectionAttachments.INFECTION).infected();
    }

    /** Clean = neither incubating nor infected. The only valid target state for a Phase 8 bite. */
    public static boolean isHealthy(Player player) {
        return player.getData(InfectionAttachments.INFECTION).healthy();
    }
}
