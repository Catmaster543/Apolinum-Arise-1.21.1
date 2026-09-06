package com.fiskerz.apolinum_arise.quests;

import com.fiskerz.apolinum_arise.Apolinumarise;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * FTB Quests integration entry point (Phase 11a).
 *
 * <p>FTB Quests has no registry event or DeferredRegister for reward types - registration is a plain static
 * call, {@code RewardTypes.register(ResourceLocation, RewardType.Provider, Supplier&lt;Icon&gt;)}, which
 * inserts into the public {@code RewardTypes.TYPES} map. Touching that class runs its static initialiser,
 * so FTB's own built-in types are always registered first regardless of when we call.
 *
 * <p>Every FTB type reference is confined to this package and reached only behind
 * {@link #isQuestsLoaded()}, so the mod still loads if FTB Quests is absent.
 */
public final class ApolinumQuests {
    private ApolinumQuests() {}

    public static final String FTB_QUESTS_MOD_ID = "ftbquests";

    /** Reward type id as it appears in the quest file: {@code apolinumarise:play_dream}. */
    public static final ResourceLocation PLAY_DREAM_ID =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "play_dream");

    private static RewardType playDreamType;

    public static boolean isQuestsLoaded() {
        return ModList.get().isLoaded(FTB_QUESTS_MOD_ID);
    }

    /** The registered type, used by {@link PlayDreamReward#getType()}. Null until {@link #init()} runs. */
    public static RewardType playDreamType() {
        return playDreamType;
    }

    /** Called from the mod constructor. Safe (and a no-op) when FTB Quests is not installed. */
    public static void init() {
        if (!isQuestsLoaded()) {
            Apolinumarise.LOGGER.info("[Quests] FTB Quests not present - skipping quest integration.");
            return;
        }
        playDreamType = RewardTypes.register(
                PLAY_DREAM_ID,
                PlayDreamReward::new,
                () -> Icon.getIcon("minecraft:item/clock"));
        Apolinumarise.LOGGER.info("[Quests] Registered reward type {} (Play Dream).", PLAY_DREAM_ID);
    }
}
