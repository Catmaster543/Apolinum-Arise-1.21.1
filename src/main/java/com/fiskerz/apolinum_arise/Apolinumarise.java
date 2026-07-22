package com.fiskerz.apolinum_arise;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonEvents;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.mosquito.MosquitoEntity;
import com.fiskerz.apolinum_arise.network.ModNetworking;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Apolinumarise.MODID)
public class Apolinumarise {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "apolinumarise";
    // Directly reference a slf4j logger, shared by the whole mod
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Apolinumarise(IEventBus modEventBus, ModContainer modContainer) {
        BloodMoonRegistry.register(modEventBus);
        com.fiskerz.apolinum_arise.infection.InfectionAttachments.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);
        modEventBus.addListener(Apolinumarise::addCreative);
        modEventBus.addListener(Apolinumarise::onEntityAttributeCreation);

        NeoForge.EVENT_BUS.addListener(Apolinumarise::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Apolinumarise::onLevelLoad);

        // SERVER config: per-world, admin-controlled (see config.Config)
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        LOGGER.info("Apolinum - arise! initialized");
    }

    // The awakening block lives in the vanilla Building Blocks tab for now; a dedicated tab can come later.
    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(BloodMoonRegistry.AWAKENING_BLOCK_ITEM);
        }
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(BloodMoonRegistry.MOSQUITO.get(), MosquitoEntity.createAttributes().build());
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        BloodMoonEvents.onLevelTick(event);
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        BloodMoonEvents.onEntityJoinLevel(event);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        BloodMoonEvents.onPlayerLoggedIn(event);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BloodMoonEvents.onPlayerLoggedOut(event);
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BloodMoonEvents.onPlayerRespawn(event);
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        BloodMoonEvents.onPlayerChangedDimension(event);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        BloodMoonEvents.onRegisterCommands(event);
    }

    private static void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        BloodMoonEvents.onLevelLoad(event);
    }
}
