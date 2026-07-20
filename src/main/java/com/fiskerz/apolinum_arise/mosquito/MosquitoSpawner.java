package com.fiskerz.apolinum_arise.mosquito;

import java.util.List;

import javax.annotation.Nullable;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonState;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.util.MoonPhases;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Custom scheduled spawn system for mosquitoes - deliberately NOT vanilla natural spawn weighting.
// Ticked from BloodMoonEvents while a Blood Moon is active; also swept clean from there when it ends.
public final class MosquitoSpawner {
    // Same "not too close, not too far" clearance idea vanilla hostile spawning uses:
    // candidates 24-48 blocks from the anchor player, and never within 24 of any player.
    private static final double MIN_SPAWN_DISTANCE = 24.0D;
    private static final double MAX_SPAWN_DISTANCE = 48.0D;
    private static final int PLACEMENT_ATTEMPTS = 8;
    private static final int UNDERGROUND_MARGIN = 8;

    private MosquitoSpawner() {}

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % Config.MOSQUITO_SPAWN_INTERVAL_TICKS.get() == 0L) {
            attemptSpawn(level);
        }
    }

    // One scheduled spawn attempt: gate on active/cap, roll the phase-scaled chance, then try to
    // place near a random online player. Package-visible so gametests can drive it deterministically.
    static void attemptSpawn(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || !BloodMoonState.isActive(level)) {
            return;
        }

        int alive = countMosquitoes(level);
        int cap = Config.MOSQUITO_MAX_CONCURRENT.get();
        if (alive >= cap) {
            Apolinumarise.LOGGER.debug("Mosquito spawn attempt skipped: at concurrent cap ({}/{}).", alive, cap);
            return;
        }

        int moonPhase = level.getMoonPhase();
        double chance = Config.MOSQUITO_SPAWN_BASE_CHANCE.get() * MoonPhases.fullnessMultiplier(moonPhase);
        double roll = level.getRandom().nextDouble();
        boolean success = roll <= chance;
        Apolinumarise.LOGGER.debug("Mosquito spawn roll: chance={} roll={} -> {} (alive {}/{}, moonPhase={}).",
                String.format("%.4f", chance), String.format("%.4f", roll), success ? "PASS" : "fail", alive, cap, moonPhase);
        if (!success) {
            return;
        }

        List<ServerPlayer> players = level.getPlayers(player -> !player.isSpectator() && player.isAlive());
        if (players.isEmpty()) {
            Apolinumarise.LOGGER.debug("Mosquito spawn roll passed but no eligible players are online; skipping.");
            return;
        }

        MosquitoEntity spawned = trySpawnNear(level, players.get(level.getRandom().nextInt(players.size())).position());
        if (spawned != null) {
            Apolinumarise.LOGGER.info("Mosquito spawned at {} in {}.", spawned.blockPosition().toShortString(), level.dimension().location());
        } else {
            Apolinumarise.LOGGER.debug("Mosquito spawn roll passed but no valid placement was found this attempt.");
        }
    }

    // Placement only (no chance roll): split surface/underground per config, respect the height
    // band, and give up quietly after a few failed candidates - the next interval retries anyway.
    @Nullable
    static MosquitoEntity trySpawnNear(ServerLevel level, Vec3 anchor) {
        RandomSource random = level.getRandom();
        boolean underground = random.nextInt(100) < Config.MOSQUITO_UNDERGROUND_SPAWN_WEIGHT_PERCENT.get();

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = MIN_SPAWN_DISTANCE + random.nextDouble() * (MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE);
            int x = Mth.floor(anchor.x + Math.cos(angle) * distance);
            int z = Mth.floor(anchor.z + Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, Mth.floor(anchor.y), z))) {
                continue;
            }

            int startY;
            if (underground) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int lowest = level.getMinBuildHeight() + UNDERGROUND_MARGIN;
                int highest = surfaceY - UNDERGROUND_MARGIN;
                if (highest <= lowest) {
                    continue;
                }
                startY = random.nextIntBetweenInclusive(lowest, highest);
            } else {
                startY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;
            }

            // Same height-band logic the wander goal uses, so spawn and hover heights agree.
            // Underground candidates must start in an existing air pocket (no climbing out of stone).
            Vec3 pos = MosquitoEntity.pickHoverPoint(level, random, new Vec3(x + 0.5D, startY, z + 0.5D), 0, !underground);
            if (pos == null || level.hasNearbyAlivePlayer(pos.x, pos.y, pos.z, MIN_SPAWN_DISTANCE)) {
                continue;
            }

            MosquitoEntity mosquito = BloodMoonRegistry.MOSQUITO.get().create(level);
            if (mosquito == null) {
                return null;
            }
            mosquito.moveTo(pos.x, pos.y, pos.z, random.nextFloat() * 360.0F, 0.0F);
            mosquito.finalizeSpawn(level, level.getCurrentDifficultyAt(mosquito.blockPosition()), MobSpawnType.EVENT, null);
            level.addFreshEntity(mosquito);
            return mosquito;
        }
        return null;
    }

    // Debug spawn used by /mosquito forcespawn: ignores Blood Moon state and the concurrent cap, and
    // marks the entity persistent so MosquitoEntity's lifecycle self-removal leaves it alone. Isolates
    // "does the entity/AI/rendering work" from "does the scheduler work".
    @Nullable
    public static MosquitoEntity forceSpawnAt(ServerLevel level, Vec3 pos) {
        MosquitoEntity mosquito = BloodMoonRegistry.MOSQUITO.get().create(level);
        if (mosquito == null) {
            return null;
        }
        mosquito.moveTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mosquito.setPersistenceRequired();
        mosquito.finalizeSpawn(level, level.getCurrentDifficultyAt(mosquito.blockPosition()), MobSpawnType.COMMAND, null);
        level.addFreshEntity(mosquito);
        Apolinumarise.LOGGER.info("Mosquito force-spawned at {} in {}.", mosquito.blockPosition().toShortString(), level.dimension().location());
        return mosquito;
    }

    public static int countMosquitoes(ServerLevel level) {
        return level.getEntities(BloodMoonRegistry.MOSQUITO.get(), Entity::isAlive).size();
    }

    /** The end-of-Blood-Moon sweep: remove every loaded mosquito immediately. */
    public static void removeAll(ServerLevel level) {
        for (MosquitoEntity mosquito : level.getEntities(BloodMoonRegistry.MOSQUITO.get(), entity -> true)) {
            mosquito.discard();
        }
    }
}
