package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Global, persisted (SavedData on the Overworld, same pattern as {@link com.fiskerz.apolinum_arise.bloodmoon.ShrineGenerationState}
 * and BloodMoonState) state for the healthy-side skill system: a cumulative count of players who have
 * EVER completed incubation, plus a one-way "unlocked" latch that flips once that count reaches the
 * configured threshold. Like every global unlock in this mod it never re-locks, even if the live
 * infected count later drops (e.g. a future cure).
 */
public class HealthySkillState extends SavedData {
    private static final String DATA_NAME = Apolinumarise.MODID + "_healthyskill";
    private static final String KEY_UNLOCKED = "unlocked";
    private static final String KEY_EVER_INFECTED = "everInfectedCount";
    private static final SavedData.Factory<HealthySkillState> FACTORY =
            new SavedData.Factory<>(HealthySkillState::new, HealthySkillState::load, null);

    private boolean unlocked;
    private int everInfectedCount;

    public static boolean isUnlocked(ServerLevel level) {
        return get(level).unlocked;
    }

    public static int everInfectedCount(ServerLevel level) {
        return get(level).everInfectedCount;
    }

    /**
     * Records one incubation completion. Returns true only for the single call that crossed the
     * threshold and flipped the unlock (so the caller can run the one-time book-placement sweep).
     * Once unlocked, further completions still increment the count but never re-flip.
     */
    public static boolean recordIncubationCompletion(ServerLevel level) {
        HealthySkillState state = get(level);
        state.everInfectedCount++;
        state.setDirty();
        if (!state.unlocked && state.everInfectedCount >= Config.HEALTHY_UNLOCK_INFECTED_THRESHOLD.get()) {
            state.unlocked = true;
            Apolinumarise.LOGGER.info("[Skill] Healthy-side skill system unlocked (everInfectedCount={}).", state.everInfectedCount);
            return true;
        }
        return false;
    }

    /** Test-only: wipe the global latch/count so a gametest can exercise the flip deterministically. */
    static void resetForTest(ServerLevel level) {
        HealthySkillState state = get(level);
        state.unlocked = false;
        state.everInfectedCount = 0;
        state.setDirty();
    }

    // Always anchored to the Overworld so there is exactly one flag per server.
    static HealthySkillState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static HealthySkillState load(CompoundTag tag, HolderLookup.Provider registries) {
        HealthySkillState state = new HealthySkillState();
        state.unlocked = tag.getBoolean(KEY_UNLOCKED);
        state.everInfectedCount = tag.getInt(KEY_EVER_INFECTED);
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(KEY_UNLOCKED, unlocked);
        tag.putInt(KEY_EVER_INFECTED, everInfectedCount);
        return tag;
    }
}
