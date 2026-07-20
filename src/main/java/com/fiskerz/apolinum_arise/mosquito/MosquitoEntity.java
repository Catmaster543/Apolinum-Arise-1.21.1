package com.fiskerz.apolinum_arise.mosquito;

import javax.annotation.Nullable;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonState;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.util.MoonPhases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// A Blood-Moon-only flying pest. Movement is modelled on Bee's flying setup (FlyingMoveControl +
// FlyingPathNavigation) rather than Phantom's swoop AI. It exists exclusively through
// MosquitoSpawner and self-removes whenever the Blood Moon is not active.
public class MosquitoEntity extends Monster implements GeoEntity {
    // Exact clip names from the user's Blockbench export - case-sensitive.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("Idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("Fly");
    private static final int LIFECYCLE_CHECK_INTERVAL_TICKS = 20;
    private static final int FLOOR_SCAN_DEPTH = 48;
    private static final int CLIMB_OUT_LIMIT = 16;
    // Fly: a negative interval means resetAmbientSoundTime() sets ambientSoundTime to +100, so the
    // vanilla baseTick check (random.nextInt(1000) < ambientSoundTime++) wins again within ~8 ticks
    // -> the buzz repeats fast enough to read as continuous. Idle: a long, jittered gap -> rare chirp.
    private static final int FLY_BUZZ_REPRIME = 100;
    private static final int IDLE_CHIRP_MIN_INTERVAL = 300;
    private static final int IDLE_CHIRP_JITTER = 400;

    private final AnimatableInstanceCache geckoCache = GeckoLibUtil.createInstanceCache(this);

    public MosquitoEntity(EntityType<? extends MosquitoEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
    }

    // Registry-time defaults mirror the verified 1.21.1 Zombie values (ATTACK_DAMAGE 3.0,
    // FOLLOW_RANGE 35 doubled to 70). The SERVER config is not loaded yet when attribute
    // suppliers are built, so config + moon-phase scaling is applied per-entity in finalizeSpawn.
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 70.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new MosquitoWanderGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        applyBloodMoonScaling();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    // Bite damage scales with moon fullness via the shared Phase 3 mapping; detection range is the
    // config value (default 2x Zombie). Base values only - the Phase 3 Blood Moon hostile-mob
    // modifiers still stack on top like they do for any other Enemy.
    public void applyBloodMoonScaling() {
        double damage = Config.MOSQUITO_BASE_DAMAGE.get() * MoonPhases.fullnessMultiplier(level().getMoonPhase());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Config.MOSQUITO_DETECTION_RANGE.get());
    }

    @Override
    public void tick() {
        super.tick();
        // Self-policing lifecycle: mosquitoes may only exist in the Overworld during an active
        // Blood Moon. The end-of-Blood-Moon sweep removes loaded ones instantly; this catches
        // ones that were in unloaded chunks at that moment, portal travellers, and stray /summons.
        // Persistent mosquitoes (spawned via /mosquito forcespawn for testing) are exempt so they
        // can be examined regardless of Blood Moon state.
        if (!level().isClientSide && !isPersistenceRequired() && tickCount % LIFECYCLE_CHECK_INTERVAL_TICKS == 0
                && level() instanceof ServerLevel serverLevel
                && (serverLevel.dimension() != Level.OVERWORLD || !BloodMoonState.isActive(serverLevel))) {
            discard();
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    /**
     * Picks a hover/spawn point up to {@code horizontalRadius} blocks from {@code origin}, at the
     * configured 2-10 band above whatever solid floor (or liquid surface) the chosen column has.
     * Scanning down from the origin's height makes surface and cave behaviour identical: the band
     * is always relative to the local floor, and cave ceilings shrink it downward as needed.
     *
     * @param climbOutOfSolid when the chosen column is solid at origin height (e.g. a hillside),
     *                        step upward out of it first; underground spawn candidates pass false
     *                        so a buried pick is skipped instead of tunnelling to the surface
     * @return the point, or null if the column has no reachable floor/air space (attempt skipped)
     */
    @Nullable
    public static Vec3 pickHoverPoint(Level level, RandomSource random, Vec3 origin, int horizontalRadius, boolean climbOutOfSolid) {
        int x = Mth.floor(origin.x) + (horizontalRadius > 0 ? random.nextIntBetweenInclusive(-horizontalRadius, horizontalRadius) : 0);
        int z = Mth.floor(origin.z) + (horizontalRadius > 0 ? random.nextIntBetweenInclusive(-horizontalRadius, horizontalRadius) : 0);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, Mth.floor(origin.y) + 2, z);

        if (!level.isEmptyBlock(cursor)) {
            if (!climbOutOfSolid) {
                return null;
            }
            int climb = CLIMB_OUT_LIMIT;
            while (!level.isEmptyBlock(cursor)) {
                if (--climb < 0 || cursor.getY() >= level.getMaxBuildHeight() - 2) {
                    return null;
                }
                cursor.move(Direction.UP);
            }
        }

        int scan = FLOOR_SCAN_DEPTH;
        while (level.isEmptyBlock(cursor)) {
            if (--scan < 0 || cursor.getY() <= level.getMinBuildHeight()) {
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        int floorY = cursor.getY();

        int minHeight = Config.MOSQUITO_HOVER_MIN_HEIGHT.get();
        int maxHeight = Math.max(minHeight, Config.MOSQUITO_HOVER_MAX_HEIGHT.get());
        // Only heights connected to the floor by contiguous air are valid: a point on the far side
        // of a cave ceiling would be unreachable to fly to - and wrong to spawn a mosquito into.
        int reachable = 0;
        while (reachable < maxHeight && level.isEmptyBlock(cursor.set(x, floorY + reachable + 1, z))) {
            reachable++;
        }
        if (reachable < minHeight) {
            return null;
        }
        return Vec3.atBottomCenterOf(new BlockPos(x, floorY + random.nextIntBetweenInclusive(minHeight, reachable), z));
    }

    // "In the Fly state" for both animation intent and sound: moving toward a hover point or a target.
    // When the wander goal has parked it (navigation done, no target), it is hovering = idle.
    private boolean isInFlyState() {
        return this.getTarget() != null || !this.getNavigation().isDone();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInFlyState() ? BloodMoonRegistry.MOSQUITO_FLY_SOUND.get() : BloodMoonRegistry.MOSQUITO_IDLE_SOUND.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        // Selected fresh at each reset (right after a sound plays), so it tracks the current state.
        return isInFlyState() ? -FLY_BUZZ_REPRIME : IDLE_CHIRP_MIN_INTERVAL + this.random.nextInt(IDLE_CHIRP_JITTER);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Single locomotion controller: Fly while moving toward a hover point or target, Idle while
        // hovering. A future "attack" clip slots in as an extra branch here (e.g. keyed off a synced
        // biting flag or triggerAnim) before the locomotion fallback - no restructuring needed;
        // until then, biting simply keeps the current locomotion animation, per the Phase 4 spec.
        controllers.add(new AnimationController<>(this, "locomotion", 4,
                state -> state.setAndContinue(state.isMoving() ? FLY : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }
}
