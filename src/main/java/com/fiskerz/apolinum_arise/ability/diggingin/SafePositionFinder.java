package com.fiskerz.apolinum_arise.ability.diggingin;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where to put a player who has to stop being burrowed right now.
 *
 * <p>Used by the max-duration auto-eject and by every forced exit (death, downed, logout, dimension
 * change, teleport). <b>It must never fail</b> - a player left inside rock with collision restored would
 * suffocate - so the search is backed by a fallback chain that ends at something guaranteed to exist.
 *
 * <ol>
 *   <li>Nearest position within {@code diggingInSafeSearchRadius} where the player's full standing
 *       bounding box is clear of blocks, preferring one standing on solid ground and never over lava.</li>
 *   <li>Failing that, the world surface at the same X/Z, from the {@code MOTION_BLOCKING_NO_LEAVES}
 *       heightmap - always defined, even mid-ocean.</li>
 *   <li>Failing even that, the player's respawn point, and then the level's shared spawn.</li>
 * </ol>
 */
public final class SafePositionFinder {
    private SafePositionFinder() {}

    /** Convenience for the real call site: pull the origin, radius and respawn point off the player. */
    public static Vec3 findEjectPosition(ServerPlayer player) {
        return findEjectPosition(player.serverLevel(), player, player.blockPosition(),
                Config.DIGGING_IN_SAFE_SEARCH_RADIUS.get(), player.getRespawnPosition());
    }

    /**
     * Searched in rings outwards from {@code origin}, so the first hit is also the nearest.
     *
     * <p>Takes everything explicitly rather than reading it off a {@link ServerPlayer}, so a gametest can
     * run the real search against real blocks in a built structure. {@code player} is used only for its
     * standing bounding-box dimensions, which any player provides.
     */
    public static Vec3 findEjectPosition(ServerLevel level, Player player, BlockPos origin, int radius,
                                         BlockPos respawnPosition) {
        // Pass 1: standing on solid ground. Pass 2: merely clear - a cave ceiling pocket beats suffocating.
        for (boolean requireGround : new boolean[]{true, false}) {
            for (int ring = 0; ring <= radius; ring++) {
                Vec3 found = searchRing(level, player, origin, ring, requireGround);
                if (found != null) {
                    Apolinumarise.LOGGER.debug("[DiggingIn] Eject found at {} (ring {}, ground={}).",
                            found, ring, requireGround);
                    return found;
                }
            }
        }

        // Fallback 2: the surface directly above. Defined everywhere, so this is near-certain to work.
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin);
        Vec3 surfacePos = Vec3.atBottomCenterOf(surface);
        if (isClear(level, player, surfacePos)) {
            Apolinumarise.LOGGER.info("[DiggingIn] No safe spot within {} blocks - using the surface at {}.",
                    radius, surface);
            return surfacePos;
        }

        // Fallback 3: their bed, else world spawn. Last resort, and still better than a wall.
        BlockPos fallback = respawnPosition != null ? respawnPosition : level.getSharedSpawnPos();
        Apolinumarise.LOGGER.warn("[DiggingIn] No safe spot and no clear surface - falling back to {}.", fallback);
        return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fallback));
    }

    /** The hollow shell of a cube at Chebyshev distance {@code ring}; ring 0 is the origin itself. */
    private static Vec3 searchRing(ServerLevel level, Player player, BlockPos origin, int ring,
                                   boolean requireGround) {
        for (int dy = -ring; dy <= ring; dy++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // Only the shell: anything strictly inside was covered by a smaller ring already.
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dy) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (candidate.getY() < level.getMinBuildHeight() + 1
                            || candidate.getY() > level.getMaxBuildHeight() - 2) {
                        continue;
                    }
                    Vec3 position = Vec3.atBottomCenterOf(candidate);
                    if (!isClear(level, player, position) || isOverLava(level, candidate)) {
                        continue;
                    }
                    if (requireGround && !hasSolidGround(level, candidate)) {
                        continue;
                    }
                    return position;
                }
            }
        }
        return null;
    }

    /** The player's full STANDING box must fit - the burrowed box is a swimming pose and smaller. */
    private static boolean isClear(ServerLevel level, Player player, Vec3 feet) {
        AABB standing = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
        // Null entity: a pure block-collision test, so other entities standing there do not veto a spot.
        return level.noCollision(null, standing.deflate(1.0E-7));
    }

    private static boolean hasSolidGround(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
    }

    /** Lava underfoot or at the feet themselves - an "open space" that kills is not a safe eject. */
    private static boolean isOverLava(ServerLevel level, BlockPos feet) {
        return level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(feet.below()).is(FluidTags.LAVA);
    }
}
