package com.fiskerz.apolinum_arise.quests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests reward that plays one of our dreams when the quest is completed (Phase 11a).
 *
 * <p>Extends FTB Quests' {@code Reward} base: {@link #getType()} + {@link #claim} are the required overrides,
 * the read/write pairs persist and sync the single {@code dreamId} field, and {@link #fillConfigGroup} is
 * what makes that field appear as an editable text box in the in-game quest editor.
 */
public class PlayDreamReward extends Reward {
    private static final String KEY_DREAM_ID = "dream_id";

    private String dreamId = "";

    public PlayDreamReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return ApolinumQuests.playDreamType();
    }

    public String getDreamId() {
        return dreamId;
    }

    // ---------------------------------------------------------------- persistence (quest file on disk)

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString(KEY_DREAM_ID, dreamId);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        dreamId = nbt.getString(KEY_DREAM_ID);
    }

    // ---------------------------------------------------------------- sync (server -> client editor/book)

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(dreamId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buf) {
        super.readNetData(buf);
        dreamId = buf.readUtf();
    }

    // ---------------------------------------------------------------- in-game editor field

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // (key, current value, setter, default) - renders as a text box in the reward's edit dialog.
        config.addString(KEY_DREAM_ID, dreamId, value -> dreamId = value, "");
    }

    // ---------------------------------------------------------------- grant

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        DreamBridge.queueDream(player, dreamId);
    }
}
