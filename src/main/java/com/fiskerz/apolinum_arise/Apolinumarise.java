package com.fiskerz.apolinum_arise;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonEvents;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.downed.DownedAttachments;
import com.fiskerz.apolinum_arise.downed.DownedEvents;
import com.fiskerz.apolinum_arise.dream.DreamAttachments;
import com.fiskerz.apolinum_arise.dream.DreamEvents;
import com.fiskerz.apolinum_arise.dream.DreamRegistry;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionEvents;
import com.fiskerz.apolinum_arise.infection.InfectionMenus;
import com.fiskerz.apolinum_arise.infection.SymptomAttachments;
import com.fiskerz.apolinum_arise.mosquito.MosquitoEntity;
import com.fiskerz.apolinum_arise.network.ModNetworking;
import com.fiskerz.apolinum_arise.quests.ApolinumQuests;
import com.fiskerz.apolinum_arise.quests.QuestDebugCommand;
import com.fiskerz.apolinum_arise.skill.ShrineBookInserter;
import com.fiskerz.apolinum_arise.skill.SkillAttachments;
import com.fiskerz.apolinum_arise.skill.SkillRegistry;
import com.fiskerz.apolinum_arise.sleep.SleepAttachments;
import com.fiskerz.apolinum_arise.sleep.SleepEvents;

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
        InfectionAttachments.register(modEventBus);
        SymptomAttachments.register(modEventBus);
        InfectionMenus.register(modEventBus);
        DownedAttachments.register(modEventBus);
        SkillRegistry.register(modEventBus);
        SkillAttachments.register(modEventBus);
        SleepAttachments.register(modEventBus);
        DreamAttachments.register(modEventBus);
        DreamRegistry.register(modEventBus);
        // Phase 11a: FTB Quests integration. No-op (and never touches FTB classes) when it is absent.
        ApolinumQuests.init();
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

        // Phase 9: skill-book placement into shrine lecterns as their chunks load (after the flag flips).
        NeoForge.EVENT_BUS.addListener(ShrineBookInserter::onChunkLoad);

        // Phase 5 infection symptom timeline (its own listeners; Phase 3/4 files untouched).
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onStartTracking);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(InfectionEvents::onItemPickup);

        // Phase 7 downed/revive system (server-side listeners).
        NeoForge.EVENT_BUS.addListener(DownedEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(DownedEvents::onBlockBreak);

        // Phase 10b dream system (server-side listeners).
        NeoForge.EVENT_BUS.addListener(DreamEvents::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onPlayerLoggedOut);

        // Phase 10a sleep bar / pass-out (server-side listeners).
        NeoForge.EVENT_BUS.addListener(SleepEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(SleepEvents::onSleepFinished);
        NeoForge.EVENT_BUS.addListener(SleepEvents::onCanPlayerSleep);
        NeoForge.EVENT_BUS.addListener(SleepEvents::onCanContinueSleeping);
        NeoForge.EVENT_BUS.addListener(SleepEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SleepEvents::onPlayerLoggedOut);

        // SERVER config: per-world, admin-controlled (see config.Config)
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        LOGGER.info("Apolinum - arise! initialized");
    }

    // The awakening block lives in the vanilla Building Blocks tab for now; a dedicated tab can come later.
    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(BloodMoonRegistry.AWAKENING_BLOCK_ITEM);
        }
        // The skill book lives in Tools & Utilities so it can be handed out for testing before shrines exist.
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SkillRegistry.SKILL_BOOK.get());
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
        com.fiskerz.apolinum_arise.dream.DreamCommands.register(event.getDispatcher());
        if (ApolinumQuests.isQuestsLoaded()) {
            QuestDebugCommand.register(event.getDispatcher());
        }
    }

    private static void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        BloodMoonEvents.onLevelLoad(event);
    }
}
