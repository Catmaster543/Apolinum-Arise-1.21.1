package com.fiskerz.apolinum_arise.dream;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry objects for the Phase 10b dream system. */
public final class DreamRegistry {
    private DreamRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Apolinumarise.MODID);

    // Tracking range must be > 0 (unlike vanilla Marker) or the client never receives the entity and the
    // camera packet silently does nothing. updateInterval 1 keeps the flown path smooth.
    public static final DeferredHolder<EntityType<?>, EntityType<DreamCameraEntity>> DREAM_CAMERA =
            ENTITY_TYPES.register("dream_camera",
                    () -> EntityType.Builder.<DreamCameraEntity>of(DreamCameraEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .noSummon()
                            .build("dream_camera"));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
