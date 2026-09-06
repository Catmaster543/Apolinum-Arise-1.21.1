package com.fiskerz.apolinum_arise.util;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Generic structure-instance lookup: the Phase 2 shrine check ({@code ShrineEffects.isWithinShrine})
 * generalised to any structure type, so the dream system can anchor a script to a dynamically located
 * world feature.
 *
 * <p>Like the original it only inspects ALREADY LOADED chunks - it never forces generation, so it is safe
 * to call every time a dream starts.
 */
public final class StructureLocator {
    private StructureLocator() {}

    /** True if {@code pos} lies inside a generated instance of {@code key}, inflated by {@code margin}. */
    public static boolean isWithin(ServerLevel level, ResourceKey<Structure> key, BlockPos pos, int margin) {
        return forEachNearbyStart(level, key, pos, 1,
                start -> start.getBoundingBox().inflatedBy(margin).isInside(pos) ? start : null) != null;
    }

    /**
     * Centre of the nearest loaded instance of {@code key} within {@code chunkRadius} chunks of
     * {@code origin}, or empty if none is loaded nearby.
     */
    public static Optional<BlockPos> findNearestCenter(ServerLevel level, ResourceKey<Structure> key,
                                                       BlockPos origin, int chunkRadius) {
        Structure structure = resolve(level, key);
        if (structure == null) {
            return Optional.empty();
        }
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (StructureStart start : level.structureManager()
                        .startsForStructure(new ChunkPos(chunkX, chunkZ), candidate -> candidate == structure)) {
                    if (!start.isValid()) {
                        continue;
                    }
                    BlockPos center = start.getBoundingBox().getCenter();
                    double distance = center.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = center;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // Shared 3x3-style scan used by isWithin; returns the first non-null mapper result.
    private static StructureStart forEachNearbyStart(ServerLevel level, ResourceKey<Structure> key, BlockPos pos,
                                                     int chunkRadius, java.util.function.Function<StructureStart, StructureStart> mapper) {
        Structure structure = resolve(level, key);
        if (structure == null) {
            return null;
        }
        int centerChunkX = pos.getX() >> 4;
        int centerChunkZ = pos.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (StructureStart start : level.structureManager()
                        .startsForStructure(new ChunkPos(chunkX, chunkZ), candidate -> candidate == structure)) {
                    if (start.isValid()) {
                        StructureStart result = mapper.apply(start);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Structure resolve(ServerLevel level, ResourceKey<Structure> key) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
    }
}
