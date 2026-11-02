package com.fiskerz.apolinum_arise.ability.hivemind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What Call of the Hive does: every non-player mob in the caster's chunk and the chunks around it, at full
 * world height, is teleported to the caster - except tamed pets (any owner) and anything carrying a player.
 *
 * <h2>Arrival</h2>
 * Mobs are set down one by one on spots around the caster, nearest rings first and shuffled within a ring so
 * they spread rather than queue in a line. A spot is only used if the mob's whole box (riders included):
 * <ul>
 *   <li>collides with no block - so nothing arrives inside a wall and suffocates;</li>
 *   <li>overlaps no box already claimed by an earlier arrival, the caster, or anyone staying behind nearby -
 *       which is what rules out entity cramming, since cramming needs overlapping boxes;</li>
 *   <li>holds no lava or fire, has something to stand on within a few blocks (flyers excepted), and does not
 *       put a land mob's head under water;</li>
 *   <li>is in line of sight of the caster, so the hive gathers around them rather than in the next cave;</li>
 *   <li>for fish and other {@link WaterAnimal}s, is in water - they would otherwise dry out on land.</li>
 * </ul>
 * A mob with no such spot inside the spread radius is left where it is rather than forced somewhere unsafe.
 *
 * <h2>Riders, mounts and leashes</h2>
 * A mob moves together with every mob riding it ({@code teleportTo} carries passengers), so a chicken jockey
 * arrives as a chicken jockey. A mob riding something that is not coming (a boat, a pet) is dismounted, and
 * something riding a called mob that is not coming is dropped off first. A stack with a player anywhere in it
 * is left alone entirely. A leash survives only when the caster holds it and the mob lands within vanilla's
 * slack range; any other leash is released, dropping the lead where the mob was tied.
 */
public final class CallOfTheHiveEffect {
    private CallOfTheHiveEffect() {}

    /** How many mobs qualified, and how many of them were actually moved. */
    public record Outcome(int eligible, int moved) {}

    /** Ground must be within this many blocks under a non-flyer's feet, so nothing is dropped off a cliff. */
    private static final int MAX_DROP = 3;
    /** Heights tried at each column, relative to the caster's feet: level first, then alternating up/down. */
    private static final int[] DY_ORDER = {0, 1, -1, 2, -2, 3, -3};
    /** Gap kept between claimed boxes, so neighbours don't touch. */
    private static final double CLAIM_GAP = 0.05D;
    /** Vanilla starts tugging a leash past 6 blocks and snaps it past 10; keep caster leashes in the slack. */
    private static final double LEASH_SLACK = 6.0D;

    private record Unit(Mob root, List<Entity> travellers, AABB box) {}

    private record Planned(Unit unit, Vec3 target) {}

    public static Outcome call(ServerLevel level, Player caster, int chunkRadius, int spreadRadius) {
        AABB sweep = sweepArea(level, caster, chunkRadius);
        List<Mob> eligible = level.getEntitiesOfClass(Mob.class, sweep, CallOfTheHiveEffect::isEligible);
        if (eligible.isEmpty()) {
            return new Outcome(0, 0);
        }
        Set<Entity> eligibleSet = new HashSet<>(eligible);

        // A unit is an eligible mob together with every eligible mob riding it. Its root is the lowest one
        // whose own vehicle is NOT eligible - nothing, a boat, a pet.
        List<Unit> units = new ArrayList<>();
        for (Mob mob : eligible) {
            if (mob.getVehicle() == null || !eligibleSet.contains(mob.getVehicle())) {
                List<Entity> travellers = travellersOf(mob, eligibleSet);
                units.add(new Unit(mob, travellers, unionBox(travellers)));
            }
        }
        // Caster-leashed mobs first so they get the nearest spots, then the biggest, which need the room.
        units.sort(Comparator.comparing((Unit unit) -> !leashedTo(unit, caster))
                .thenComparing(unit -> -footprint(unit.box())));

        Claims claims = new Claims();
        claims.add(caster.getBoundingBox().inflate(0.5D, 0.0D, 0.5D));
        for (Entity stays : level.getEntities(caster, caster.getBoundingBox().inflate(spreadRadius + 3.0D),
                entity -> entity instanceof LivingEntity && !eligibleSet.contains(entity))) {
            claims.add(stays.getBoundingBox());
        }

        List<int[]> columns = columnsAround(spreadRadius, caster.getRandom());
        List<Planned> plan = new ArrayList<>();
        for (Unit unit : units) {
            Vec3 target = findSpot(level, caster, columns, unit, claims);
            if (target != null) {
                plan.add(new Planned(unit, target));
            }
        }
        if (plan.isEmpty()) {
            return new Outcome(eligible.size(), 0);
        }

        Set<Entity> moving = new HashSet<>();
        for (Planned planned : plan) {
            moving.addAll(planned.unit().travellers());
        }
        releaseLeashes(level, caster, sweep, plan, moving);

        int moved = 0;
        for (Planned planned : plan) {
            moved += commit(caster, planned, moving);
        }
        return new Outcome(eligible.size(), moved);
    }

    // ---------------------------------------------------------------- who is called

    /** The swept volume: {@code chunkRadius} chunks around the caster's chunk, full world height. */
    public static AABB sweepArea(ServerLevel level, Player caster, int chunkRadius) {
        ChunkPos chunk = caster.chunkPosition();
        return new AABB((chunk.x - chunkRadius) << 4, level.getMinBuildHeight(), (chunk.z - chunkRadius) << 4,
                (chunk.x + chunkRadius + 1) << 4, level.getMaxBuildHeight(), (chunk.z + chunkRadius + 1) << 4);
    }

    /** Any living mob that is not a tamed pet and has no player anywhere in its riding stack. */
    public static boolean isEligible(Mob mob) {
        return mob.isAlive()
                && !isTamedPet(mob)
                && mob.getRootVehicle().getSelfAndPassengers().noneMatch(entity -> entity instanceof Player);
    }

    /** Tamed by anyone: a tamed wolf/cat/parrot, or an owned horse, llama, camel and the like. */
    public static boolean isTamedPet(Mob mob) {
        return (mob instanceof TamableAnimal tamable && tamable.isTame())
                || (mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null);
    }

    private static List<Entity> travellersOf(Mob root, Set<Entity> eligibleSet) {
        List<Entity> travellers = new ArrayList<>();
        Deque<Entity> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Entity entity = pending.pop();
            travellers.add(entity);
            for (Entity passenger : entity.getPassengers()) {
                if (eligibleSet.contains(passenger)) {
                    pending.push(passenger);
                }
            }
        }
        return travellers;
    }

    // ---------------------------------------------------------------- where they land

    /**
     * Every column offset within {@code radius}, nearest ring first and shuffled within each ring, so arrivals
     * fan out around the caster instead of filling one side.
     */
    private static List<int[]> columnsAround(int radius, RandomSource random) {
        TreeMap<Integer, List<int[]>> rings = new TreeMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq == 0 || distanceSq > radius * radius) {
                    continue;
                }
                rings.computeIfAbsent((int) Math.sqrt(distanceSq), ring -> new ArrayList<>()).add(new int[] {dx, dz});
            }
        }
        Random shuffler = new Random(random.nextLong());
        List<int[]> columns = new ArrayList<>();
        for (List<int[]> ring : rings.values()) {
            Collections.shuffle(ring, shuffler);
            columns.addAll(ring);
        }
        return columns;
    }

    @Nullable
    private static Vec3 findSpot(ServerLevel level, Player caster, List<int[]> columns, Unit unit, Claims claims) {
        BlockPos origin = caster.blockPosition();
        for (int[] column : columns) {
            for (int dy : DY_ORDER) {
                Vec3 target = new Vec3(origin.getX() + column[0] + 0.5D, origin.getY() + dy, origin.getZ() + column[1] + 0.5D);
                AABB box = unit.box().move(target.subtract(unit.root().position()));
                if (claims.intersects(box)
                        || !level.noCollision(unit.root(), box)
                        || containsHazard(level, box)
                        || !suitableMedium(level, unit.root(), target, box)
                        || !inSight(level, caster, target, box)) {
                    continue;
                }
                claims.add(box);
                return target;
            }
        }
        return null;
    }

    private static boolean containsHazard(ServerLevel level, AABB box) {
        return level.getBlockStates(box).anyMatch(CallOfTheHiveEffect::isHazard);
    }

    private static boolean isHazard(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE);
    }

    /** Water for fish; footing and a head above water for everything that walks; anything for flyers. */
    private static boolean suitableMedium(ServerLevel level, Mob root, Vec3 target, AABB box) {
        BlockPos feet = BlockPos.containing(target);
        if (root instanceof WaterAnimal) {
            return level.getFluidState(feet).is(FluidTags.WATER);
        }
        if (isFlyer(root)) {
            return true;
        }
        if (level.getFluidState(BlockPos.containing(target.x, box.maxY - 0.1D, target.z)).is(FluidTags.WATER)) {
            return false; // would arrive fully submerged
        }
        for (int depth = 1; depth <= MAX_DROP; depth++) {
            BlockPos below = feet.below(depth);
            BlockState state = level.getBlockState(below);
            if (isHazard(state)) {
                return false;
            }
            if (state.getFluidState().is(FluidTags.WATER) || !state.getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFlyer(Mob mob) {
        return mob.isNoGravity() || mob instanceof FlyingMob || mob instanceof FlyingAnimal
                || mob instanceof EnderDragon || mob instanceof WitherBoss;
    }

    private static boolean inSight(ServerLevel level, Player caster, Vec3 target, AABB box) {
        Vec3 aim = new Vec3(target.x, Math.min(target.y + 0.5D, box.maxY - 0.1D), target.z);
        return level.clip(new ClipContext(caster.getEyePosition(), aim, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getType() == HitResult.Type.MISS;
    }

    // ---------------------------------------------------------------- moving them

    private static void releaseLeashes(ServerLevel level, Player caster, AABB sweep, List<Planned> plan,
                                       Set<Entity> moving) {
        for (Planned planned : plan) {
            for (Entity traveller : planned.unit().travellers()) {
                if (traveller instanceof Leashable leashable && leashable.isLeashed()) {
                    boolean keep = leashable.getLeashHolder() == caster
                            && horizontalDistance(planned.target(), caster.position()) <= LEASH_SLACK;
                    if (!keep) {
                        leashable.dropLeash(true, true); // the lead drops where the mob was tied
                    }
                }
            }
        }
        // Anything left behind that a called mob was holding - a trader's llamas outside the sweep.
        for (Entity entity : level.getEntities((Entity) null, sweep.inflate(10.0D),
                entity -> entity instanceof Leashable leashable && leashable.isLeashed() && !moving.contains(entity))) {
            Leashable leashable = (Leashable) entity;
            if (moving.contains(leashable.getLeashHolder())) {
                leashable.dropLeash(true, true);
            }
        }
    }

    private static int commit(Player caster, Planned planned, Set<Entity> moving) {
        Unit unit = planned.unit();
        Mob root = unit.root();
        Vec3 target = planned.target();
        if (root.isPassenger()) {
            root.stopRiding(); // off whatever is not coming along - a boat, a minecart, a pet
        }
        for (Entity traveller : unit.travellers()) {
            for (Entity passenger : List.copyOf(traveller.getPassengers())) {
                if (!moving.contains(passenger)) {
                    passenger.stopRiding(); // a pet riding a called mob stays behind
                }
            }
            if (traveller instanceof LivingEntity living && living.isSleeping()) {
                living.stopSleeping(); // a villager in bed would otherwise keep its sleeping pose at the new spot
            }
        }
        float yaw = (float) (Mth.atan2(caster.getZ() - target.z, caster.getX() - target.x) * Mth.RAD_TO_DEG) - 90.0F;
        root.setYRot(yaw);
        root.setYHeadRot(yaw);
        root.setYBodyRot(yaw);
        root.teleportTo(target.x, target.y, target.z); // carries the riders with it
        for (Entity traveller : unit.travellers()) {
            traveller.resetFallDistance();
            if (traveller instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
        return unit.travellers().size();
    }

    // ---------------------------------------------------------------- helpers

    private static boolean leashedTo(Unit unit, Player caster) {
        return unit.travellers().stream()
                .anyMatch(entity -> entity instanceof Leashable leashable && leashable.getLeashHolder() == caster);
    }

    private static AABB unionBox(List<Entity> entities) {
        AABB box = entities.get(0).getBoundingBox();
        for (int i = 1; i < entities.size(); i++) {
            box = box.minmax(entities.get(i).getBoundingBox());
        }
        return box;
    }

    private static double footprint(AABB box) {
        return box.getXsize() * box.getZsize();
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt(Mth.square(a.x - b.x) + Mth.square(a.z - b.z));
    }

    /**
     * Claimed boxes, indexed by every block column they cover, so the overlap test only looks at boxes in the
     * columns a candidate touches. Keeps placement cheap even when a whole mob farm is called at once.
     */
    private static final class Claims {
        private final Map<Long, List<AABB>> byColumn = new HashMap<>();

        void add(AABB box) {
            AABB padded = box.inflate(CLAIM_GAP);
            forEachColumn(padded, key -> byColumn.computeIfAbsent(key, k -> new ArrayList<>()).add(padded));
        }

        boolean intersects(AABB box) {
            boolean[] hit = {false};
            forEachColumn(box, key -> {
                if (!hit[0]) {
                    for (AABB claimed : byColumn.getOrDefault(key, List.of())) {
                        if (claimed.intersects(box)) {
                            hit[0] = true;
                            return;
                        }
                    }
                }
            });
            return hit[0];
        }

        private static void forEachColumn(AABB box, java.util.function.LongConsumer action) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    action.accept(ChunkPos.asLong(x, z));
                }
            }
        }
    }
}
