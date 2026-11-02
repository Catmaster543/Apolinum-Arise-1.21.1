package com.fiskerz.apolinum_arise.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingIn;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingInManager;
import com.fiskerz.apolinum_arise.ability.hivemind.CallOfTheHive;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMind;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunch;

import net.minecraft.world.entity.player.Player;

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
        // Phase 12 proving ability: infected, Branch 1 = variant 0 only, earned by watching the whispers
        // dream and then being handed the wand. See the hivemind package for what it does.
        register(Ability.restrictedTo(HiveMind.ABILITY_ID, AbilitySide.INFECTED, HiveMind.VARIANT,
                new UnlockCondition.DreamWatched(HiveMind.DREAM_ID)));
        // Phase 13: infected, Branch 2 = variant 1 only. No item and no drop - watching the dream is the
        // whole unlock, so the condition being met IS the ability being available.
        register(Ability.restrictedTo(StrongPunch.ABILITY_ID, AbilitySide.INFECTED, StrongPunch.VARIANT,
                new UnlockCondition.DreamWatched(StrongPunch.DREAM_ID)));
        // Phase 14: infected, Branch 3 = variant 2 only. The first keybind-driven ability (slot 1, R),
        // which is why the registry carries a slot and a press hook at all.
        register(Ability.restrictedTo(DiggingIn.ABILITY_ID, AbilitySide.INFECTED, DiggingIn.VARIANT,
                        new UnlockCondition.DreamWatched(DiggingIn.DREAM_ID))
                .withKeybind(DiggingIn.KEYBIND_SLOT, DiggingInManager::onKeyPress));
        // Phase 15: Branch 1's second ability. Its dream is queued when the Hive-Mind unlocks (not at variant
        // assignment), and watching it is the whole unlock. Used by right-clicking the Hive-Mind wand.
        register(Ability.restrictedTo(CallOfTheHive.ABILITY_ID, AbilitySide.INFECTED, CallOfTheHive.VARIANT,
                        new UnlockCondition.DreamAfterAbility(HiveMind.ABILITY_ID, CallOfTheHive.DREAM_ID))
                .unlockedWhenMet());
    }

    /**
     * The dreams to queue the moment {@code abilityId} is unlocked: those of every ability whose condition
     * is "{@code abilityId} unlocked, then this dream watched". Registry-driven like
     * {@link #prerequisiteDreamsFor}, so chaining a further ability needs no change at the unlock site.
     */
    public static List<String> dreamsQueuedOnUnlockOf(String abilityId) {
        List<String> dreams = new ArrayList<>();
        for (Ability ability : BY_ID.values()) {
            if (ability.unlockCondition() instanceof UnlockCondition.DreamAfterAbility chained
                    && chained.abilityId().equals(abilityId)
                    && !dreams.contains(chained.dreamId())) {
                dreams.add(chained.dreamId());
            }
        }
        return dreams;
    }

    /**
     * The ability this player has in keybind {@code slot}, if any - the whole of the slot lookup, split out
     * from the router so it can be exercised without a networked player.
     *
     * <p>Empty is the normal, common answer: an unbound slot, a slot whose ability they have not unlocked,
     * or a player on the wrong side or variant. At most one ability can ever match, because slots are
     * declared per ability and the side/variant restrictions are mutually exclusive.
     */
    public static Optional<Ability> findKeybindAbility(Player player, int slot) {
        for (Ability ability : BY_ID.values()) {
            if (ability.usesKeybindSlot(slot)
                    && AbilityManager.hasAbility(player, ability.id())
                    && ability.appliesTo(player)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
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
