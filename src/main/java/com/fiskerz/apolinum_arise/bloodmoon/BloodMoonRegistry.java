package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// All registry objects of the blood moon system.
public final class BloodMoonRegistry {
    private BloodMoonRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Apolinumarise.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Apolinumarise.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Apolinumarise.MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Apolinumarise.MODID);

    // Slight strength so a stray left-click doesn't instantly break it (default of() would be instabreak);
    // it is still meant to be triggered, not mined.
    public static final DeferredBlock<AwakeningBlock> AWAKENING_BLOCK = BLOCKS.register("awakening_block",
            () -> new AwakeningBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)));

    public static final DeferredItem<BlockItem> AWAKENING_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("awakening_block", AWAKENING_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AwakeningBlockEntity>> AWAKENING_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("awakening_block",
                    () -> BlockEntityType.Builder.of(AwakeningBlockEntity::new, AWAKENING_BLOCK.get()).build(null));

    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKENING_SOUND = SOUND_EVENTS.register("bloodmoon_awakening",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_awakening")));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
    }
}
