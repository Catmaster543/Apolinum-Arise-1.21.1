package com.fiskerz.apolinum_arise.ability.hivemind;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry objects for the Hive-Mind ability (currently just the wand). */
public final class HiveMindRegistry {
    private HiveMindRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Apolinumarise.MODID);

    // stacksTo(1): it is a weapon, and the drop is a one-off unlock rather than a resource.
    // NOTE: no .attributes(...) here, deliberately - see HiveMindStickItem for why the damage has to come
    // from getDefaultAttributeModifiers instead if the config value is to mean anything.
    public static final DeferredItem<HiveMindStickItem> HIVE_MIND_STICK =
            ITEMS.register("hive_mind_stick", () -> new HiveMindStickItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
