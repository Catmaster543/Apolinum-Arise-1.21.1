package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.mosquito.MosquitoSpawner;
import com.fiskerz.apolinum_arise.network.BloodMoonSyncPayload;
import com.fiskerz.apolinum_arise.util.MoonPhases;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BloodMoonEvents {
    private static final int EFFECT_REFRESH_FUDGE_TICKS = 20;
    // Pale, Terraria-esque green for the Blood Moon start announcement. Recolor here.
    private static final int BLOOD_MOON_START_MESSAGE_COLOR = 0xAAFFAA;
    private static final ResourceLocation MOB_DAMAGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_mob_damage");
    private static final ResourceLocation MOB_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_mob_health");

    private BloodMoonEvents() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!Config.ENABLE_MOD.get()) {
            return;
        }

        // Shrine proximity is independent of the Blood Moon unlock (shrines exist from world start).
        ShrineEffects.tick(serverLevel);
        tickOverworld(serverLevel);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || !(event.getEntity() instanceof LivingEntity livingEntity) || !(livingEntity instanceof Enemy)) {
            return;
        }

        // Buffs are Overworld-only; anywhere else this strips a stale modifier from
        // entities that were unloaded mid-Blood-Moon and carried it across.
        boolean active = serverLevel.dimension() == Level.OVERWORLD && BloodMoonState.isActive(serverLevel);
        updateMobModifiers(livingEntity, active);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAndApply(player);
            ShrineEffects.onLogin(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShrineEffects.onLogout(player);
        }
    }

    // Respawning can move a player into the Overworld (e.g. died in the Nether) without a
    // PlayerChangedDimensionEvent, so it needs the same sync as login/dimension change.
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAndApply(player);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAndApply(player);
        }
    }

    private static void syncAndApply(ServerPlayer player) {
        syncPlayerState(player);
        ServerLevel playerLevel = (ServerLevel) player.level();
        if (playerLevel.dimension() == Level.OVERWORLD && BloodMoonState.isActive(playerLevel)) {
            applyPlayerEffects(player, playerLevel);
        }
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BloodMoonCommands.register(event.getDispatcher(), event.getBuildContext());
        com.fiskerz.apolinum_arise.mosquito.MosquitoCommands.register(event.getDispatcher());
    }

    static void debugTick(ServerLevel overworld) {
        tickOverworld(overworld);
    }

    static void debugRoll(ServerLevel overworld) {
        if (BloodMoonState.isUnlocked(overworld)) {
            rollBloodMoon(overworld);
        }
    }

    static void debugFailRoll(ServerLevel overworld) {
        if (!BloodMoonState.isUnlocked(overworld)) {
            return;
        }

        double chance = BloodMoonState.getCurrentChance(overworld);
        double nextChance = Math.min(chance * Config.BLOOD_MOON_CHANCE_GROWTH.get(), Config.BLOOD_MOON_CHANCE_CAP.get());
        BloodMoonState.setCurrentChance(overworld, nextChance);
    }

    static void debugSuccessRoll(ServerLevel overworld) {
        if (!BloodMoonState.isUnlocked(overworld)) {
            return;
        }

        BloodMoonState.setCurrentChance(overworld, Config.BLOOD_MOON_BASE_CHANCE.get());
        startBloodMoon(overworld);
    }

    static void debugApplyPlayerEffects(ServerLevel overworld, Player player) {
        applyPlayerEffects(player, overworld);
    }

    public static void forceStart(MinecraftServer server) {
        startBloodMoon(server.overworld());
    }

    public static void forceStop(MinecraftServer server) {
        endBloodMoon(server.overworld());
    }

    public static double getCurrentChance(MinecraftServer server) {
        return BloodMoonState.getCurrentChance(server.overworld());
    }

    public static void setCurrentChance(MinecraftServer server, double chance) {
        BloodMoonState.setCurrentChance(server.overworld(), clampChance(chance));
    }

    private static void tickOverworld(ServerLevel overworld) {
        if (!Config.ENABLE_MOD.get() || !BloodMoonState.isUnlocked(overworld)) {
            return;
        }

        if (BloodMoonState.isActive(overworld) && !overworld.isNight()) {
            endBloodMoon(overworld);
        }

        long day = overworld.getDayTime() / Level.TICKS_PER_DAY;
        if (overworld.isNight() && BloodMoonState.getLastEvaluatedDay(overworld) != day) {
            BloodMoonState.setLastEvaluatedDay(overworld, day);
            rollBloodMoon(overworld);
        }

        if (BloodMoonState.isActive(overworld)) {
            if (overworld.getGameTime() % Config.EFFECT_REFRESH_INTERVAL_TICKS.get() == 0L) {
                refreshActivePlayers(overworld);
            }
            MosquitoSpawner.tick(overworld);
        }
    }

    private static void rollBloodMoon(ServerLevel overworld) {
        double chance = BloodMoonState.getCurrentChance(overworld);
        RandomSource random = overworld.getRandom();
        if (random.nextDouble() <= chance) {
            BloodMoonState.setCurrentChance(overworld, Config.BLOOD_MOON_BASE_CHANCE.get());
            startBloodMoon(overworld);
            return;
        }

        double nextChance = Math.min(chance * Config.BLOOD_MOON_CHANCE_GROWTH.get(), Config.BLOOD_MOON_CHANCE_CAP.get());
        BloodMoonState.setCurrentChance(overworld, nextChance);
    }

    private static void startBloodMoon(ServerLevel overworld) {
        BloodMoonState.setActive(overworld, true);
        syncOverworldPlayers(overworld, true);
        refreshActiveOverworldEntities(overworld);
        refreshActivePlayers(overworld);
        announceStart(overworld);
    }

    // Broadcast the start cue to every online player regardless of dimension: a pale-green chat
    // line plus a one-shot stinger played individually (non-positional) to each player.
    private static void announceStart(ServerLevel overworld) {
        Component message = Component.translatable("message.apolinumarise.bloodmoon.start")
                .withStyle(style -> style.withColor(TextColor.fromRgb(BLOOD_MOON_START_MESSAGE_COLOR)));
        for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            player.playNotifySound(BloodMoonRegistry.BLOODMOON_START_SOUND.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    private static void endBloodMoon(ServerLevel overworld) {
        BloodMoonState.setActive(overworld, false);
        syncOverworldPlayers(overworld, false);
        stripOverworldEntityModifiers(overworld);
        MosquitoSpawner.removeAll(overworld);
    }

    private static void syncPlayerState(ServerPlayer player) {
        ServerLevel playerLevel = (ServerLevel) player.level();
        boolean active = playerLevel.dimension() == Level.OVERWORLD && BloodMoonState.isActive(playerLevel);
        PacketDistributor.sendToPlayer(player, new BloodMoonSyncPayload(active));
    }

    private static void syncOverworldPlayers(ServerLevel overworld, boolean active) {
        PacketDistributor.sendToPlayersInDimension(overworld, new BloodMoonSyncPayload(active));
    }

    private static void refreshActiveOverworldEntities(ServerLevel overworld) {
        for (Entity entity : overworld.getAllEntities()) {
            if (entity instanceof LivingEntity livingEntity && livingEntity instanceof Enemy) {
                updateMobModifiers(livingEntity, true);
            }
        }
    }

    private static void stripOverworldEntityModifiers(ServerLevel overworld) {
        for (Entity entity : overworld.getAllEntities()) {
            if (entity instanceof LivingEntity livingEntity && livingEntity instanceof Enemy) {
                updateMobModifiers(livingEntity, false);
            }
        }
    }

    private static void updateMobModifiers(LivingEntity livingEntity, boolean active) {
        AttributeInstance damage = livingEntity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            if (active) {
                damage.addOrUpdateTransientModifier(new AttributeModifier(MOB_DAMAGE_MODIFIER_ID, Config.MOB_DAMAGE_MULTIPLIER.get() - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else {
                damage.removeModifier(MOB_DAMAGE_MODIFIER_ID);
            }
        }

        AttributeInstance health = livingEntity.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            if (active) {
                health.addOrUpdateTransientModifier(new AttributeModifier(MOB_HEALTH_MODIFIER_ID, Config.MOB_HEALTH_MULTIPLIER.get() - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else {
                health.removeModifier(MOB_HEALTH_MODIFIER_ID);
            }
        }
    }

    private static void refreshActivePlayers(ServerLevel overworld) {
        for (ServerPlayer player : overworld.getPlayers(candidate -> true)) {
            if (isSusceptible(player)) {
                applyPlayerEffects(player, overworld);
            }
        }
    }

    private static void applyPlayerEffects(Player player, ServerLevel overworld) {
        if (!isSusceptible(player)) {
            return;
        }

        int refreshInterval = Config.EFFECT_REFRESH_INTERVAL_TICKS.get();
        int effectDuration = refreshInterval + EFFECT_REFRESH_FUDGE_TICKS;
        int moonPhase = overworld.getMoonPhase();
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectDuration, Config.getWeaknessAmplifierForPhase(moonPhase)));

        int moonFullness = MoonPhases.fullnessPercent(moonPhase);
        if (moonFullness >= Config.MINING_FATIGUE_THRESHOLD_1.get()) {
            int amplifier = Config.MINING_FATIGUE_AMPLIFIER_1.get();
            if (moonFullness >= Config.MINING_FATIGUE_THRESHOLD_3.get()) {
                amplifier = Config.MINING_FATIGUE_AMPLIFIER_3.get();
            } else if (moonFullness >= Config.MINING_FATIGUE_THRESHOLD_2.get()) {
                amplifier = Config.MINING_FATIGUE_AMPLIFIER_2.get();
            }
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, effectDuration, amplifier));
        }
    }

    private static boolean isSusceptible(Player player) {
        return true;
    }

    private static double clampChance(double chance) {
        return Mth.clamp(chance, 0.0D, Config.BLOOD_MOON_CHANCE_CAP.get());
    }
}