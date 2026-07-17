package com.fiskerz.apolinum_arise;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.network.ModNetworking;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

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
        modEventBus.addListener(ModNetworking::register);
        modEventBus.addListener(Apolinumarise::addCreative);

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
}
