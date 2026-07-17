package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// Server-wide "blood moon unlocked" flag, persisted via SavedData on the Overworld's DataStorage
// so it is dimension-agnostic and survives restarts. Later phases check isUnlocked() to know
// whether Blood Moons are possible at all.
public class BloodMoonState extends SavedData {
    private static final String DATA_NAME = Apolinumarise.MODID + "_bloodmoon";
    private static final SavedData.Factory<BloodMoonState> FACTORY =
            new SavedData.Factory<>(BloodMoonState::new, BloodMoonState::load, null);

    private boolean unlocked;

    public static boolean isUnlocked(ServerLevel level) {
        return get(level).unlocked;
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
        state.setDirty();
        return true;
    }

    // Always anchored to the Overworld regardless of which dimension asks, so there is exactly one flag per server.
    private static BloodMoonState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static BloodMoonState load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoonState state = new BloodMoonState();
        state.unlocked = tag.getBoolean("unlocked");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("unlocked", unlocked);
        return tag;
    }
}
