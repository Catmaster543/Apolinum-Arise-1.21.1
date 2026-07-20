package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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

        BloodMoonState.setUnlocked(helper.getLevel(), false);
        BloodMoonState.setActive(helper.getLevel(), false);
        BloodMoonState.setCurrentChance(helper.getLevel(), 0.01D);
        BloodMoonState.setLastEvaluatedDay(helper.getLevel(), -1L);

        helper.assertFalse(BloodMoonState.isUnlocked(helper.getLevel()), "Unlock flag should start false");

        helper.setBlock(pos, BloodMoonRegistry.AWAKENING_BLOCK.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, player);

        helper.assertTrue(BloodMoonState.isUnlocked(helper.getLevel()), "Trigger should set the global unlock flag");
        helper.assertTrue(player.getHealth() == 1.0F, "Triggering player's health should be set to 1.0");
        helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "Triggering player should have Blindness");
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION), "Triggering player should have Nausea");
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Triggering player should have Weakness");

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

    // Own batch: batches run sequentially, so this can't race awakening_full_cycle's async tail
    // over the shared global BloodMoonState (tests within one batch all tick simultaneously).
    @GameTest(template = "empty_3x3", batch = "bloodmoon")
    public static void blood_moon_escalates_and_cycles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BloodMoonState.setUnlocked(level, false);
        BloodMoonState.tryUnlock(level);
        BloodMoonState.setCurrentChance(level, 0.01D);
        BloodMoonState.setLastEvaluatedDay(level, -1L);
        BloodMoonState.setActive(level, false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));

        BloodMoonEvents.debugFailRoll(level);
        helper.assertFalse(BloodMoonState.isActive(level), "Blood Moon should not start on a failed roll");
        helper.assertTrue(BloodMoonState.getCurrentChance(level) > 0.01D, "Chance should escalate after a failed roll");
        helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "Player debuffs should not apply while inactive");
        helper.assertFalse(zombie.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_mob_damage")),
            "Hostile mobs should not be buffed while inactive");

        BloodMoonState.setCurrentChance(level, 1.0D);
        BloodMoonEvents.debugSuccessRoll(level);

        helper.assertTrue(BloodMoonState.isActive(level), "Blood Moon should start when the roll succeeds");
        helper.assertTrue(zombie.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_mob_damage")),
            "Loaded hostile mobs should gain the Blood Moon attack modifier");

        // Vanilla phase 4 is the NEW moon (0% full): Weakness still applies, Mining Fatigue must not.
        helper.setDayTime(4 * Level.TICKS_PER_DAY);
        BloodMoonEvents.debugApplyPlayerEffects(level, player);
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Players should gain Blood Moon Weakness while active");
        helper.assertFalse(player.hasEffect(MobEffects.DIG_SLOWDOWN), "Mining Fatigue must not apply at the new moon");

        // Vanilla phase 0 is the FULL moon (100% full): now Mining Fatigue kicks in.
        helper.setDayTime(8 * Level.TICKS_PER_DAY);
        BloodMoonEvents.debugApplyPlayerEffects(level, player);
        helper.assertTrue(player.hasEffect(MobEffects.DIG_SLOWDOWN), "Players should gain Mining Fatigue while the moon is full enough");

        BloodMoonEvents.forceStop(level.getServer());

        helper.assertFalse(BloodMoonState.isActive(level), "Blood Moon should end at dawn");
        helper.assertFalse(zombie.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_mob_damage")),
            "Hostile mob modifiers should be removed at dawn");
        BloodMoonState.setUnlocked(level, false);
        BloodMoonState.setCurrentChance(level, 0.01D);
        BloodMoonState.setActive(level, false);
        helper.succeed();
    }
}
