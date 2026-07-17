package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Run via `gradlew runGameTestServer`. One test covers the whole flow deterministically,
// because the unlock flag is global to the gametest server's shared world.
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class AwakeningGameTests {

    @GameTest(template = "empty_3x3")
    public static void awakening_full_cycle(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);

        helper.assertFalse(BloodMoonState.isUnlocked(helper.getLevel()), "Unlock flag should start false");

        helper.setBlock(pos, BloodMoonRegistry.AWAKENING_BLOCK.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, player);

        helper.assertTrue(BloodMoonState.isUnlocked(helper.getLevel()), "Trigger should set the global unlock flag");
        helper.assertTrue(player.getHealth() == 1.0F, "Triggering player's health should be set to 1.0");
        helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "Triggering player should have Blindness");
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION), "Triggering player should have Nausea");
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Triggering player should have Weakness");
        helper.assertBlockPresent(Blocks.AIR, pos);

        // Flag is now set: a (re)loading awakening block must remove itself. Placing one runs the
        // same BlockEntity#onLoad -> scheduled tick path a chunk load runs for pre-existing shrines.
        helper.setBlock(pos, BloodMoonRegistry.AWAKENING_BLOCK.get());
        helper.succeedWhenBlockPresent(Blocks.AIR, pos);
    }

    // The awakening_shrine template NBT is intentionally not shipped yet; the structure must
    // generate as a silent no-op (blank template), never a crash. Runs the real jigsaw path.
    @GameTest(template = "empty_3x3")
    public static void shrine_generates_gracefully_without_nbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "awakening_shrine"));
        helper.assertTrue(structure != null, "awakening_shrine structure should be registered");

        ServerChunkCache chunkSource = level.getChunkSource();
        StructureStart start = structure.generate(
                level.registryAccess(),
                chunkSource.getGenerator(),
                chunkSource.getGenerator().getBiomeSource(),
                chunkSource.randomState(),
                level.getStructureManager(),
                level.getSeed(),
                new ChunkPos(40, 40),
                0,
                level,
                biome -> true);

        helper.assertTrue(start != null, "Structure generation with missing NBT must not crash");
        helper.succeed();
    }
}
