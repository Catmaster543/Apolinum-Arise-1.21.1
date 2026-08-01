package com.fiskerz.apolinum_arise.infection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Server-only per-player bookkeeping for the incubation symptom timeline (Phase 5), persisted so it
 * survives relog and death.
 *
 * <ul>
 *   <li>{@code lastDayProcessed} - highest symptom day number whose day-start hooks (messages, mole
 *       rolls, mole growth) have run. 0 = not yet initialised; the first observation seeds it to the
 *       current day without firing, so the bite day never back-fires.</li>
 *   <li>{@code lastSunsetDay} - calendar day whose sunset was already handled for the day-9 de-aggro
 *       latch, so it triggers exactly once.</li>
 *   <li>{@code enemiesIgnore} - set at day-9 sunset; hostile mobs stop targeting this player. Persists
 *       through the rest of incubation and is intentionally NOT cleared at the infection transition
 *       (Phase 6 owns what happens to it afterwards).</li>
 * </ul>
 */
public record SymptomTracker(int lastDayProcessed, int lastSunsetDay, boolean enemiesIgnore) {
    public static final SymptomTracker NONE = new SymptomTracker(0, 0, false);

    public static final Codec<SymptomTracker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("lastDayProcessed", 0).forGetter(SymptomTracker::lastDayProcessed),
            Codec.INT.optionalFieldOf("lastSunsetDay", 0).forGetter(SymptomTracker::lastSunsetDay),
            Codec.BOOL.optionalFieldOf("enemiesIgnore", false).forGetter(SymptomTracker::enemiesIgnore)
    ).apply(instance, SymptomTracker::new));

    public SymptomTracker withLastDayProcessed(int day) {
        return new SymptomTracker(day, lastSunsetDay, enemiesIgnore);
    }

    public SymptomTracker withLastSunsetDay(int day) {
        return new SymptomTracker(lastDayProcessed, day, enemiesIgnore);
    }

    public SymptomTracker withEnemiesIgnore(boolean value) {
        return new SymptomTracker(lastDayProcessed, lastSunsetDay, value);
    }
}
