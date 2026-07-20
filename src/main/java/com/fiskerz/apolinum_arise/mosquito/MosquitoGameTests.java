package com.fiskerz.apolinum_arise.mosquito;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonEvents;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonState;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Own batch (batches run sequentially) so the multi-tick active-Blood-Moon window here can't race
// the other tests over the shared global BloodMoonState.
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class MosquitoGameTests {

    // Covers: no spawns while inactive, persistence while active, the end-of-Blood-Moon sweep,
    // and self-removal of a mosquito that (re)appears while no Blood Moon is running.
    @GameTest(template = "empty_3x3", batch = "mosquito", timeoutTicks = 200)
    public static void mosquito_exists_only_during_blood_moon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BloodMoonState.tryUnlock(level);
        BloodMoonState.setActive(level, false);
        MosquitoSpawner.removeAll(level);
        // The nightly tick handler ends any Blood Moon it sees during the day; the multi-tick
        // phases below keep isActive true across ticks, so this test must run at night.
        helper.setNight();

        // Inactive: the scheduled attempt must be a no-op regardless of RNG or player presence.
        MosquitoSpawner.attemptSpawn(level);
        helper.assertTrue(MosquitoSpawner.countMosquitoes(level) == 0, "No mosquito may spawn while the Blood Moon is inactive");

        BloodMoonEvents.forceStart(level.getServer());
        MosquitoEntity active = helper.spawn(BloodMoonRegistry.MOSQUITO.get(), new BlockPos(1, 2, 1));

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(active.isAlive(), "Mosquito must persist while the Blood Moon is active");

            BloodMoonEvents.forceStop(level.getServer());
            helper.assertTrue(active.isRemoved(), "Ending the Blood Moon must sweep all loaded mosquitoes immediately");

            // A non-persistent mosquito appearing while inactive (unloaded-chunk stragglers, /summon)
            // self-removes via its periodic lifecycle check. Built directly, NOT via helper.spawn(),
            // because that force-sets persistence - which is exactly the flag /mosquito forcespawn
            // relies on to survive, so a persistent one must (correctly) NOT self-remove.
            MosquitoEntity straggler = BloodMoonRegistry.MOSQUITO.get().create(level);
            helper.assertTrue(straggler != null, "Mosquito entity type must create an instance");
            BlockPos stragglerPos = helper.absolutePos(new BlockPos(1, 2, 1));
            straggler.moveTo(stragglerPos.getX() + 0.5D, stragglerPos.getY(), stragglerPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(straggler);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(straggler.isRemoved(), "A non-persistent mosquito outside an active Blood Moon must remove itself");
                helper.succeed();
            });
        });
    }

    // Empirical placement-success measurement for the spawn-math review. Builds a wide stone floor
    // (so candidates 24-48 blocks from the anchor land on real ground), drives trySpawnNear many
    // times, and logs how many produced a mosquito. This isolates the placement/clearance half of
    // the spawner from the (deliberately low) chance roll. Non-asserting beyond ">0" so it can't
    // flake the build; the logged ratio is the actual observation. Own batch: it calls the global
    // removeAll(), which would otherwise clobber the concurrent mosquito-lifecycle test.
    @GameTest(template = "empty_3x3", batch = "mosquito_placement", timeoutTicks = 200)
    public static void mosquito_placement_success_rate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        int platformY = center.getY() + 30;
        int radius = 52; // covers the 24-48 block candidate ring in every direction

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    level.setBlock(cursor.set(center.getX() + dx, platformY, center.getZ() + dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }

        Vec3 anchor = new Vec3(center.getX() + 0.5D, platformY + 3, center.getZ() + 0.5D);
        int attempts = 120;
        int successes = 0;
        for (int i = 0; i < attempts; i++) {
            if (MosquitoSpawner.trySpawnNear(level, anchor) != null) {
                successes++;
            }
        }
        MosquitoSpawner.removeAll(level);

        Apolinumarise.LOGGER.info("Mosquito placement success rate over flat ground: {}/{} trySpawnNear calls produced a mosquito.", successes, attempts);
        helper.assertTrue(successes > 0, "trySpawnNear must succeed on open flat ground (got " + successes + "/" + attempts + ")");
        helper.succeed();
    }

    // Validates the /mosquito forcespawn backend: it must create a mosquito that survives even with
    // no active Blood Moon (persistence exempts it from the lifecycle self-removal). Own batch so its
    // global spawn/state changes never race the other mosquito tests.
    @GameTest(template = "empty_3x3", batch = "mosquito_forcespawn", timeoutTicks = 100)
    public static void forcespawn_survives_without_blood_moon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BloodMoonState.setActive(level, false);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));

        MosquitoEntity forced = MosquitoSpawner.forceSpawnAt(level, pos);
        helper.assertTrue(forced != null, "forceSpawnAt must create a mosquito");
        helper.assertTrue(forced.isPersistenceRequired(), "A force-spawned mosquito must be persistent");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(forced.isAlive(), "A force-spawned mosquito must survive with no active Blood Moon");
            forced.discard();
            helper.succeed();
        });
    }

    // The shared hover/spawn point picker must always land inside the configured height band
    // above the column's floor - the same code path drives wandering and spawn placement.
    // The 14-tall empty template keeps the whole 2-10 band inside the test volume, away from the
    // framework's scaffolding around each structure. Scans stay pinned to a single column
    // (radius 0), so the floor each pick sees is exactly the stone this test places.
    @GameTest(template = "empty_tall", batch = "mosquito")
    public static void hover_points_stay_within_height_band(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int minHeight = Config.MOSQUITO_HOVER_MIN_HEIGHT.get();
        int maxHeight = Config.MOSQUITO_HOVER_MAX_HEIGHT.get();

        // Open column: floor stone with 13 blocks of clear air above - the full band is available.
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        BlockPos openFloor = helper.absolutePos(new BlockPos(1, 0, 1));
        Vec3 openOrigin = Vec3.atBottomCenterOf(openFloor);

        // Capped column: floor stone with a "cave ceiling" 3 above - only height 2 is reachable,
        // and a pick must never land on the far side of the ceiling.
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(0, 3, 0), Blocks.STONE);
        BlockPos cappedFloor = helper.absolutePos(new BlockPos(0, 0, 0));
        Vec3 cappedOrigin = Vec3.atBottomCenterOf(cappedFloor);

        for (int i = 0; i < 64; i++) {
            Vec3 open = MosquitoEntity.pickHoverPoint(level, level.getRandom(), openOrigin, 0, false);
            helper.assertTrue(open != null, "The open column must always yield a hover point");
            int openHeight = (int) Math.round(open.y) - openFloor.getY();
            helper.assertTrue(openHeight >= minHeight && openHeight <= maxHeight,
                    "Open-column height " + openHeight + " must stay within [" + minHeight + ", " + maxHeight + "]");

            Vec3 capped = MosquitoEntity.pickHoverPoint(level, level.getRandom(), cappedOrigin, 0, false);
            helper.assertTrue(capped != null, "The capped column must still yield its reachable height");
            int cappedHeight = (int) Math.round(capped.y) - cappedFloor.getY();
            helper.assertTrue(cappedHeight == minHeight,
                    "Capped-column height " + cappedHeight + " must clamp to " + minHeight + " below the ceiling, never beyond it");
        }
        helper.succeed();
    }
}
