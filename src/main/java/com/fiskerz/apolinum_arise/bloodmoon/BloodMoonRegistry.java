package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.mosquito.MosquitoEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
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
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Apolinumarise.MODID);
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Apolinumarise.MODID);

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

    public static final DeferredHolder<SoundEvent, SoundEvent> BLOODMOON_AMBIENT_SOUND = SOUND_EVENTS.register("bloodmoon_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_ambient")));

    // One-shot "stinger" fired at the exact moment a Blood Moon begins (distinct from the loop above).
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOODMOON_START_SOUND = SOUND_EVENTS.register("bloodmoon_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_start")));

    // Mosquito ambience: frequent buzz while flying, rare chirp while idle (see MosquitoEntity).
    public static final DeferredHolder<SoundEvent, SoundEvent> MOSQUITO_FLY_SOUND = SOUND_EVENTS.register("mosquito_fly",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "mosquito_fly")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOSQUITO_IDLE_SOUND = SOUND_EVENTS.register("mosquito_idle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "mosquito_idle")));

    // MONSTER category for despawn rules and Enemy semantics; it never enters vanilla natural
    // spawning - MosquitoSpawner is its only source.
    public static final DeferredHolder<EntityType<?>, EntityType<MosquitoEntity>> MOSQUITO = ENTITY_TYPES.register("mosquito",
            () -> EntityType.Builder.of(MosquitoEntity::new, MobCategory.MONSTER)
                    .sized(0.45F, 0.35F)
                    .clientTrackingRange(10)
                    .build("mosquito"));

    // A StructurePlacementType is a functional interface returning its MapCodec; the shrine's
    // structure_set JSON references this by id ("apolinumarise:shrine_spread").
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<ShrineStructurePlacement>> SHRINE_PLACEMENT_TYPE =
            STRUCTURE_PLACEMENT_TYPES.register("shrine_spread", () -> () -> ShrineStructurePlacement.CODEC);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STRUCTURE_PLACEMENT_TYPES.register(modEventBus);
    }
}
