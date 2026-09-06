package com.fiskerz.apolinum_arise.skill;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.network.chat.Component;

/** The three healthy-side stats, rolled once when the book grants access. */
public enum HealthyStat {
    INTELLIGENCE,
    STRENGTH,
    CREATIVITY;

    /** Lang key, e.g. {@code gui.apolinumarise.stat.intelligence}. */
    public String translationKey() {
        return "gui.apolinumarise.stat." + name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    /** Parse a config token ("INTELLIGENCE", "intelligence"). Empty when it names no stat. */
    public static Optional<HealthyStat> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        for (HealthyStat stat : values()) {
            if (stat.name().equalsIgnoreCase(token.trim())) {
                return Optional.of(stat);
            }
        }
        return Optional.empty();
    }
}
