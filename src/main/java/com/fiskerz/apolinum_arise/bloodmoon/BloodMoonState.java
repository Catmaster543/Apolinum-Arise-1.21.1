package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// Server-wide "blood moon unlocked" flag, persisted via SavedData on the Overworld's DataStorage
// so it is dimension-agnostic and survives restarts. Later phases check isUnlocked() to know
// whether Blood Moons are possible at all.
public class BloodMoonState extends SavedData {
    private static final String DATA_NAME = Apolinumarise.MODID + "_bloodmoon";
    private static final String KEY_UNLOCKED = "unlocked";
    private static final String KEY_CURRENT_CHANCE = "currentChance";
    private static final String KEY_LAST_EVALUATED_DAY = "lastEvaluatedDay";
    private static final String KEY_ACTIVE = "active";
    private static final SavedData.Factory<BloodMoonState> FACTORY =
            new SavedData.Factory<>(BloodMoonState::new, BloodMoonState::load, null);

    private boolean unlocked;
    private double currentChance = 0.01D;
    private long lastEvaluatedDay = -1L;
    private boolean active;

    public static boolean isUnlocked(ServerLevel level) {
        return get(level).unlocked;
    }

    public static boolean isActive(ServerLevel level) {
        return get(level).active;
    }

    public static double getCurrentChance(ServerLevel level) {
        return get(level).currentChance;
    }

    public static long getLastEvaluatedDay(ServerLevel level) {
        return get(level).lastEvaluatedDay;
    }

    public static void unlock(ServerLevel level) {
        tryUnlock(level);
    }

    /** Check-and-set: flips the flag and returns true only for the single call that performed the unlock. */
    public static boolean tryUnlock(ServerLevel level) {
        BloodMoonState state = get(level);
        if (state.unlocked) {
            return false;
        }
        state.unlocked = true;
        state.currentChance = Config.BLOOD_MOON_BASE_CHANCE.get();
        state.lastEvaluatedDay = -1L;
        state.active = false;
        state.setDirty();
        return true;
    }

    public static void setActive(ServerLevel level, boolean active) {
        BloodMoonState state = get(level);
        if (state.active != active) {
            state.active = active;
            state.setDirty();
        }
    }

    static void setUnlocked(ServerLevel level, boolean unlocked) {
        BloodMoonState state = get(level);
        if (state.unlocked != unlocked) {
            state.unlocked = unlocked;
            state.setDirty();
        }
    }

    public static void setCurrentChance(ServerLevel level, double currentChance) {
        BloodMoonState state = get(level);
        if (Double.compare(state.currentChance, currentChance) != 0) {
            state.currentChance = currentChance;
            state.setDirty();
        }
    }

    public static void setLastEvaluatedDay(ServerLevel level, long lastEvaluatedDay) {
        BloodMoonState state = get(level);
        if (state.lastEvaluatedDay != lastEvaluatedDay) {
            state.lastEvaluatedDay = lastEvaluatedDay;
            state.setDirty();
        }
    }

    // Always anchored to the Overworld regardless of which dimension asks, so there is exactly one flag per server.
    static BloodMoonState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static BloodMoonState load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoonState state = new BloodMoonState();
        state.unlocked = tag.getBoolean(KEY_UNLOCKED);
        state.currentChance = tag.contains(KEY_CURRENT_CHANCE) ? tag.getDouble(KEY_CURRENT_CHANCE) : Config.BLOOD_MOON_BASE_CHANCE.get();
        state.lastEvaluatedDay = tag.contains(KEY_LAST_EVALUATED_DAY) ? tag.getLong(KEY_LAST_EVALUATED_DAY) : -1L;
        state.active = tag.getBoolean(KEY_ACTIVE);
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(KEY_UNLOCKED, unlocked);
        tag.putDouble(KEY_CURRENT_CHANCE, currentChance);
        tag.putLong(KEY_LAST_EVALUATED_DAY, lastEvaluatedDay);
        tag.putBoolean(KEY_ACTIVE, active);
        return tag;
    }
}
