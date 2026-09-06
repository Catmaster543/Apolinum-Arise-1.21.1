package com.fiskerz.apolinum_arise.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.network.chat.Component;

/**
 * The four healthy-side branches: their placeholder names and the two stats each one favours.
 *
 * <p>The preferences are advisory only - nothing checks a player's rolled stats against the branch they
 * pick. They exist so the choice screen can tell a player which of their three numbers each branch cares
 * about, and whether it wants that number high or low.
 *
 * <p>Read live from {@code healthyBranchStatPreferences}, which is a SERVER config and therefore synced to
 * clients, so the choice screen can render straight from it.
 */
public final class HealthyBranches {
    private HealthyBranches() {}

    /** Fixed by design: four branches, one permanent choice. */
    public static final int COUNT = 4;

    /** One stat a branch cares about, and which end of the range it favours. */
    public record StatPreference(HealthyStat stat, boolean favorHigh) {
        public Component describe() {
            return Component.translatable(
                    favorHigh ? "gui.apolinumarise.branch.pref.high" : "gui.apolinumarise.branch.pref.low",
                    stat.displayName());
        }
    }

    public static boolean isValidIndex(int index) {
        return index >= 0 && index < COUNT;
    }

    /**
     * Placeholder display name for a branch. Real names arrive with the real content; until then the lang
     * file supplies "Branch I".."Branch IV", which is enough to prove the choice mechanism.
     */
    public static Component displayName(int index) {
        return Component.translatable("gui.apolinumarise.branch." + index);
    }

    /**
     * The stats branch {@code index} favours, parsed from config. Malformed entries are logged and skipped
     * rather than crashing the screen, so a typo costs an annotation, not the GUI.
     */
    public static List<StatPreference> preferences(int index) {
        String raw = Config.getIndexed(Config.HEALTHY_BRANCH_STAT_PREFERENCES, index);
        List<StatPreference> parsed = new ArrayList<>(2);
        if (raw.isBlank()) {
            return parsed;
        }
        for (String pair : raw.split(",")) {
            parse(pair).ifPresentOrElse(parsed::add, () -> Apolinumarise.LOGGER.warn(
                    "[Skill] healthyBranchStatPreferences entry {} has an unparseable pair '{}' "
                            + "(expected STAT:HIGH or STAT:LOW) - skipped.", index, pair.trim()));
        }
        return parsed;
    }

    private static Optional<StatPreference> parse(String pair) {
        String[] halves = pair.split(":");
        if (halves.length != 2) {
            return Optional.empty();
        }
        String direction = halves[1].trim().toUpperCase(Locale.ROOT);
        if (!direction.equals("HIGH") && !direction.equals("LOW")) {
            return Optional.empty();
        }
        return HealthyStat.parse(halves[0]).map(stat -> new StatPreference(stat, direction.equals("HIGH")));
    }
}
