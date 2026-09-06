package com.fiskerz.apolinum_arise.dream;

import java.util.ArrayList;
import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world-wide list of permanent category broadcasts (Phase 10b item 3), persisted as SavedData on the
 * Overworld - the same pattern as BloodMoonState / HealthySkillState.
 *
 * <p>Entries never expire and are never removed. A broadcast is not "sent" to anyone at issue time; it
 * simply exists from then on, and every player is checked against the full list at each category-change
 * point (login, infection completion, cure). That is what makes it reach future joiners and players who
 * only later enter the category, exactly once each - the per-player
 * {@link DreamData#receivedBroadcasts()} set is what prevents repeats.
 */
public class DreamBroadcasts extends SavedData {
    private static final String DATA_NAME = Apolinumarise.MODID + "_dreambroadcasts";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_DREAM = "dream";

    private static final SavedData.Factory<DreamBroadcasts> FACTORY =
            new SavedData.Factory<>(DreamBroadcasts::new, DreamBroadcasts::load, null);

    /** One permanent broadcast: everyone who is ever in {@code category} eventually receives {@code dreamId}. */
    public record Entry(DreamCategory category, String dreamId) {
        /** Stable identity used in the per-player received set. */
        public String key() {
            return category.name() + ":" + dreamId;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static DreamBroadcasts get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Key for an entry, without needing an instance. */
    public static String key(DreamCategory category, String dreamId) {
        return category.name() + ":" + dreamId;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Add a permanent broadcast. Returns false if this exact category+dream is already broadcast. */
    public boolean add(DreamCategory category, String dreamId) {
        String key = key(category, dreamId);
        for (Entry entry : entries) {
            if (entry.key().equals(key)) {
                return false;
            }
        }
        entries.add(new Entry(category, dreamId));
        setDirty();
        Apolinumarise.LOGGER.info("[Dream] Broadcast registered: {} -> {} (permanent).", category, dreamId);
        return true;
    }

    /** Test-only: wipe the list so a gametest can exercise it deterministically. */
    public void clearForTest() {
        entries.clear();
        setDirty();
    }

    private static DreamBroadcasts load(CompoundTag tag, HolderLookup.Provider registries) {
        DreamBroadcasts state = new DreamBroadcasts();
        ListTag list = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                state.entries.add(new Entry(DreamCategory.valueOf(entry.getString(KEY_CATEGORY)),
                        entry.getString(KEY_DREAM)));
            } catch (IllegalArgumentException exception) {
                Apolinumarise.LOGGER.warn("[Dream] Dropping broadcast with unknown category: {}", entry);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag compound = new CompoundTag();
            compound.putString(KEY_CATEGORY, entry.category().name());
            compound.putString(KEY_DREAM, entry.dreamId());
            list.add(compound);
        }
        tag.put(KEY_ENTRIES, list);
        return tag;
    }
}
