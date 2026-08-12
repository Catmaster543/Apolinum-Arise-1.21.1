package com.fiskerz.apolinum_arise.skill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fiskerz.apolinum_arise.bloodmoon.ShrineEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Puts one skill book into every empty Lectern that sits inside a generated shrine, once the healthy
 * skill system is unlocked. Mirrors the Phase 2 awakening-block self-removal: it acts both when the
 * flag flips (a one-time sweep of currently-loaded shrines) and on every subsequent chunk load, so it
 * works retroactively for already-generated shrines and for any generated afterward.
 */
public final class ShrineBookInserter {
    private ShrineBookInserter() {}

    /** ChunkEvent.Load hook: when a shrine chunk (re)loads after unlock, fill its empty lecterns. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (!HealthySkillState.isUnlocked(level)) {
            return;
        }
        // Defer off the load path (mutating a chunk mid-load is unsafe - same reasoning as the awakening
        // block's scheduled tick); the task queue runs it on the server thread once the load settles.
        ChunkPos pos = chunk.getPos();
        level.getServer().execute(() -> {
            if (level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk loaded) {
                fillChunk(level, loaded);
            }
        });
    }

    /** One-time sweep when the flag flips: fill every currently-loaded shrine chunk. */
    public static void placeInLoadedShrines(ServerLevel overworld) {
        int viewDistance = overworld.getServer().getPlayerList().getViewDistance() + 1;
        Set<Long> visited = new HashSet<>();
        // Loaded chunks are those kept loaded by players; scan each player's view-distance square. Any
        // shrine not currently loaded is handled by onChunkLoad when it next loads.
        for (ServerPlayer player : overworld.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    int cx = center.x + dx;
                    int cz = center.z + dz;
                    if (!visited.add(ChunkPos.asLong(cx, cz))) {
                        continue;
                    }
                    if (overworld.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk) {
                        fillChunk(overworld, chunk);
                    }
                }
            }
        }
    }

    // Collect empty lecterns first (avoid mutating the map we are iterating), then insert one book into
    // each that is actually inside a shrine bounding box.
    private static void fillChunk(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> emptyLecterns = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            if (entry.getValue() instanceof LecternBlockEntity lectern && !lectern.hasBook()) {
                emptyLecterns.add(entry.getKey().immutable());
            }
        }
        for (BlockPos pos : emptyLecterns) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LecternBlock && !state.getValue(LecternBlock.HAS_BOOK)
                    && ShrineEffects.isWithinShrine(level, pos, 0)) {
                LecternBlock.tryPlaceBook(null, level, pos, state, new ItemStack(SkillRegistry.SKILL_BOOK.get()));
            }
        }
    }
}
