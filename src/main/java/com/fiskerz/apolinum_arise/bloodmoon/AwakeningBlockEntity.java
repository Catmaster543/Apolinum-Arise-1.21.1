package com.fiskerz.apolinum_arise.bloodmoon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exists purely for its load hook: whenever the chunk holding an awakening block loads
 * (freshly generated or years old), schedule a block tick that removes the block if the
 * global unlock already happened. No world scanning needed.
 */
public class AwakeningBlockEntity extends BlockEntity {
    public AwakeningBlockEntity(BlockPos pos, BlockState state) {
        super(BloodMoonRegistry.AWAKENING_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Mutating the chunk mid-load is unsafe; a 1-tick scheduled tick does the removal
        // (see AwakeningBlock#tick) once the level is fully ready.
        if (level != null && !level.isClientSide) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }
}
