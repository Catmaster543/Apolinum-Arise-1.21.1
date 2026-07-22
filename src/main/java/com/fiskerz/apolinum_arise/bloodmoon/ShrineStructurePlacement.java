package com.fiskerz.apolinum_arise.bloodmoon;

import java.util.Optional;

import com.fiskerz.apolinum_arise.config.Config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * A {@code random_spread} placement that also rejects candidate chunks inside a square around the
 * world origin. The exclusion has to live in Java (not datapack JSON) because the distance/toggle
 * are read live from the mod's SERVER config during worldgen. Extends the vanilla class so the
 * spread maths are inherited verbatim; only {@link #isPlacementChunk} is extended.
 */
public class ShrineStructurePlacement extends RandomSpreadStructurePlacement {
    // placementCodec (the 5 shared StructurePlacement fields) is protected-static on StructurePlacement,
    // inherited here; .and(...) appends the three RandomSpread fields, matching the 8-arg constructor.
    public static final MapCodec<ShrineStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            placementCodec(instance).and(instance.group(
                    Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                    Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)
            )).apply(instance, ShrineStructurePlacement::new));

    public ShrineStructurePlacement(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
                                    float frequency, int salt, Optional<StructurePlacement.ExclusionZone> exclusionZone,
                                    int spacing, int separation, RandomSpreadType spreadType) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int x, int z) {
        if (!super.isPlacementChunk(structureState, x, z)) {
            return false;
        }

        boolean generationUnlocked = ShrineGenerationState.isGenerationUnlocked();
        // Activation-gated worlds: nothing generates until /shrine activate flips the flag.
        if (Config.SHRINE_REQUIRE_ACTIVATION_COMMAND.get() && !generationUnlocked) {
            return false;
        }
        // Once activated (in any config), the origin-distance exclusion is bypassed entirely.
        if (generationUnlocked) {
            return true;
        }
        if (!Config.SHRINE_SPAWN_EXCLUSION_ENABLED.get()) {
            return true;
        }
        int distance = Config.SHRINE_SPAWN_EXCLUSION_DISTANCE.get();
        // Chunk centre in block coordinates; the box check forbids only when BOTH axes are inside.
        int blockX = (x << 4) + 8;
        int blockZ = (z << 4) + 8;
        return Math.abs(blockX) >= distance || Math.abs(blockZ) >= distance;
    }

    @Override
    public StructurePlacementType<?> type() {
        return BloodMoonRegistry.SHRINE_PLACEMENT_TYPE.get();
    }
}
