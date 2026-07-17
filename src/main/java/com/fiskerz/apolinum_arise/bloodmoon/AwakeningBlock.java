package com.fiskerz.apolinum_arise.bloodmoon;

import javax.annotation.Nullable;

import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.network.AwakeningSoundPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one-time trigger block hidden in the awakening shrine. Stepping on/into it or
 * right-clicking it (both funnel into {@link #tryTrigger}) permanently unlocks Blood
 * Moons server-wide, at a heavy personal cost to the player who touched it.
 */
public class AwakeningBlock extends Block implements EntityBlock {
    public AwakeningBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AwakeningBlockEntity(pos, state);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        tryTrigger(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        tryTrigger(level, pos, entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // PASS for creative/spectator so shrine builders can interact/place against it freely
        if (player.isCreative() || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        tryTrigger(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Scheduled by AwakeningBlockEntity#onLoad: once the global flag is set, any loading chunk
    // (old or newly generated) silently loses its awakening block.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (BloodMoonState.isUnlocked(level)) {
            level.removeBlock(pos, false);
        }
    }

    private static void tryTrigger(Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Survival/adventure players only: mobs can't consume the one-time event, and creative
        // builders can handle the block while building shrines without setting it off.
        if (!(entity instanceof Player player) || player.isSpectator() || player.isCreative()) {
            return;
        }
        if (!Config.ENABLE_MOD.get()) {
            return;
        }
        // Check-and-set FIRST: the global flag doubles as the double-trigger guard, so a second
        // touch in the same tick (or a second block anywhere in the world) can never re-fire.
        if (!BloodMoonState.tryUnlock(serverLevel)) {
            serverLevel.removeBlock(pos, false); // stale leftover after unlock: clean up quietly
            return;
        }

        player.setHealth((float) (double) Config.AWAKENING_HEALTH_LEFT.get());
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                Config.AWAKENING_BLINDNESS_DURATION_TICKS.get(), Config.AWAKENING_BLINDNESS_AMPLIFIER.get()));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                Config.AWAKENING_NAUSEA_DURATION_TICKS.get(), Config.AWAKENING_NAUSEA_AMPLIFIER.get()));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                Config.AWAKENING_WEAKNESS_DURATION_TICKS.get(), Config.AWAKENING_WEAKNESS_AMPLIFIER.get()));

        serverLevel.removeBlock(pos, false);

        serverLevel.getServer().getPlayerList()
                .broadcastSystemMessage(Component.translatable("message.apolinumarise.bloodmoon_awakened"), false);
        PacketDistributor.sendToAllPlayers(new AwakeningSoundPayload(pos, serverLevel.dimension().location()));
    }
}
