package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry objects for the skill system (currently just the healthy-side access book). */
public final class SkillRegistry {
    private SkillRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Apolinumarise.MODID);

    // Single-copy stacking would be more book-like, but leaving the default max stack keeps it simple;
    // the grant only ever consumes one. Texture/model pending (assets/apolinumarise/models/item/skill_book.json
    // -> textures/item/skill_book.png); a missing model renders as the magenta/black placeholder, no crash.
    public static final DeferredItem<SkillBookItem> SKILL_BOOK =
            ITEMS.register("skill_book", () -> new SkillBookItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
