package com.fiskerz.apolinum_arise.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMind;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunch;

/**
 * The ability registry: every {@link Ability} the mod knows about, keyed by id.
 *
 * <p>A plain static map rather than a Minecraft registry on purpose. Abilities are pure code-side
 * descriptors - nothing datapack-driven, nothing synced, nothing that needs a numeric network id - so a
 * registry object would buy nothing and cost a registration lifecycle. Entries are declared in the static
 * initialiser below; the class is touched from {@code Apolinumarise}'s constructor so that runs at load.
 */
public final class Abilities {
    private Abilities() {}

    private static final Map<String, Ability> BY_ID = new LinkedHashMap<>();

    static {
        // Phase 12's proving ability: infected, variant 1 only, earned by watching the whispers dream and
        // then being handed the wand. See the hivemind package for everything it actually does.
        register(Ability.restrictedTo(HiveMind.ABILITY_ID, AbilitySide.INFECTED, HiveMind.VARIANT,
                new UnlockCondition.DreamWatched(HiveMind.DREAM_ID)));
        // Phase 13: infected, variant 2 only. No item and no drop - watching the dream is the whole
        // unlock, so the condition being met IS the ability being available.
        register(Ability.restrictedTo(StrongPunch.ABILITY_ID, AbilitySide.INFECTED, StrongPunch.VARIANT,
                new UnlockCondition.DreamWatched(StrongPunch.DREAM_ID)));
    }

    /**
     * Every dream that is a prerequisite for an ability belonging to this side and sub-division.
     *
     * <p>This is how variant/branch assignment knows which dreams to queue, WITHOUT the skill package
     * naming individual abilities. A new variant ability with a {@link UnlockCondition.DreamWatched}
     * condition is queued automatically the moment it is registered here - the assignment code does not
     * change. Conditions of other types contribute nothing, which is correct: they are not dreams.
     */
    public static List<String> prerequisiteDreamsFor(AbilitySide side, int subdivision) {
        List<String> dreams = new ArrayList<>();
        for (Ability ability : BY_ID.values()) {
            if (ability.side() != side
                    || ability.restrictedTo().isEmpty()
                    || ability.restrictedTo().getAsInt() != subdivision) {
                continue;
            }
            if (ability.unlockCondition() instanceof UnlockCondition.DreamWatched watched
                    && !dreams.contains(watched.dreamId())) {
                dreams.add(watched.dreamId());
            }
        }
        return dreams;
    }

    /** Register an ability. Returns it back so declarations can stay one expression. */
    public static Ability register(Ability ability) {
        Ability previous = BY_ID.put(ability.id(), ability);
        if (previous != null) {
            Apolinumarise.LOGGER.warn("[Ability] '{}' was registered twice - the later definition wins.",
                    ability.id());
        }
        return ability;
    }

    public static Optional<Ability> get(String abilityId) {
        return Optional.ofNullable(BY_ID.get(abilityId));
    }

    public static Collection<Ability> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Forces the static initialiser to run, so registration happens at a known point during mod load. */
    public static void init() {
        Apolinumarise.LOGGER.info("[Ability] {} abilit(ies) registered: {}", BY_ID.size(), BY_ID.keySet());
    }
}
