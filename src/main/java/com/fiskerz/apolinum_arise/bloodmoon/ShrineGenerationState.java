package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persisted (SavedData on the Overworld, same style as BloodMoonState) one-way flag that
 * `/shrine activate` sets. Because the custom StructurePlacement runs during worldgen with no
 * ServerLevel handle, it can't read SavedData directly - so the flag is mirrored into a static
 * cache ({@link #isGenerationUnlocked}) that is primed on overworld load and updated on activation.
 */
public class ShrineGenerationState extends SavedData {
    private static final String DATA_NAME = Apolinumarise.MODID + "_shrinegen";
    private static final String KEY_UNLOCKED = "unlocked";
    private static final SavedData.Factory<ShrineGenerationState> FACTORY =
            new SavedData.Factory<>(ShrineGenerationState::new, ShrineGenerationState::load, null);

    // Read by worldgen (ShrineStructurePlacement); SavedData below is the persistent source of truth.
    private static volatile boolean cachedUnlocked = false;

    private boolean unlocked;

    public static boolean isGenerationUnlocked() {
        return cachedUnlocked;
    }

    /** Prime the worldgen-facing cache from persisted data; call on overworld load, before new-chunk gen. */
    public static void refreshCache(ServerLevel overworld) {
        cachedUnlocked = get(overworld).unlocked;
    }

    /** One-way switch. Returns true only for the call that actually performed the activation. */
    public static boolean unlock(ServerLevel level) {
        ShrineGenerationState state = get(level);
        cachedUnlocked = true;
        if (state.unlocked) {
            return false;
        }
        state.unlocked = true;
        state.setDirty();
        return true;
    }

    public static boolean isUnlocked(ServerLevel level) {
        return get(level).unlocked;
    }

    // Always anchored to the Overworld so there is exactly one flag per server.
    static ShrineGenerationState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ShrineGenerationState load(CompoundTag tag, HolderLookup.Provider registries) {
        ShrineGenerationState state = new ShrineGenerationState();
        state.unlocked = tag.getBoolean(KEY_UNLOCKED);
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(KEY_UNLOCKED, unlocked);
        return tag;
    }
}
