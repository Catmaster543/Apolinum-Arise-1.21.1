package com.fiskerz.apolinum_arise.bloodmoon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Proximity behaviour around every generated awakening shrine (not just the one that unlocked the
 * mod): while a player is within the shrine's bounding box expanded by {@link #PROXIMITY_MARGIN},
 * they suffer Mining Fatigue I and, once per entry, an actionbar warning.
 */
public final class ShrineEffects {
    public static final ResourceKey<Structure> SHRINE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "awakening_shrine"));

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int EFFECT_FUDGE_TICKS = 20;
    private static final int PROXIMITY_MARGIN = 5;
    private static final int MINING_FATIGUE_AMPLIFIER = 0; // level I

    // Session-scoped only (intentionally not persisted): true = this player was inside a shrine's
    // proximity box at the previous check. Drives the once-per-entry warning.
    private static final Map<UUID, Boolean> insideLastCheck = new ConcurrentHashMap<>();

    private ShrineEffects() {}

    public static void tick(ServerLevel overworld) {
        if (overworld.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        for (ServerPlayer player : overworld.players()) {
            boolean inside = isNearShrine(overworld, player.blockPosition());
            boolean wasInside = insideLastCheck.getOrDefault(player.getUUID(), Boolean.FALSE);
            if (inside) {
                // Reapplied slightly longer than the interval so it never visibly flickers.
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                        CHECK_INTERVAL_TICKS + EFFECT_FUDGE_TICKS, MINING_FATIGUE_AMPLIFIER, false, false, true));
                if (!wasInside) {
                    player.displayClientMessage(Component.translatable("message.apolinumarise.shrine.unwell"), true);
                }
            }
            insideLastCheck.put(player.getUUID(), inside);
        }
    }

    // Re-arm on login: the player wasn't tracked while offline, so baseline their state to where
    // they actually are. Logging in already inside must not fire the warning; it re-fires only on a
    // fresh outside -> inside transition later.
    public static void onLogin(ServerPlayer player) {
        boolean inside = player.level() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD
                && isNearShrine(level, player.blockPosition());
        insideLastCheck.put(player.getUUID(), inside);
    }

    public static void onLogout(ServerPlayer player) {
        insideLastCheck.remove(player.getUUID());
    }

    private static boolean isNearShrine(ServerLevel level, BlockPos pos) {
        Structure shrine = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(SHRINE);
        if (shrine == null) {
            return false;
        }
        int centerChunkX = pos.getX() >> 4;
        int centerChunkZ = pos.getZ() >> 4;
        // The 3x3 chunk neighbourhood covers the 5-block margin even at chunk edges. Only already
        // loaded chunks are queried, so this never forces generation.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(chunkX, chunkZ), candidate -> candidate == shrine)) {
                    if (start.isValid() && start.getBoundingBox().inflatedBy(PROXIMITY_MARGIN).isInside(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
