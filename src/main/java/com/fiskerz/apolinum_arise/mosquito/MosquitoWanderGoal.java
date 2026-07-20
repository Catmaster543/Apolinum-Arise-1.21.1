package com.fiskerz.apolinum_arise.mosquito;

import java.util.EnumSet;

import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

// Default state whenever the mosquito has no target: fly to a random point within the wander
// radius at the configured height band above the local floor, hover for the configured interval,
// repeat. Deliberately not a Phantom-style swoop/circle - just unhurried ambient drifting.
public class MosquitoWanderGoal extends Goal {
    private final MosquitoEntity mosquito;
    private Vec3 wanderTarget;
    private int hoverTicks;

    public MosquitoWanderGoal(MosquitoEntity mosquito) {
        this.mosquito = mosquito;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mosquito.getTarget() != null) {
            return false;
        }
        if (hoverTicks > 0) {
            hoverTicks--;
            return false;
        }

        wanderTarget = MosquitoEntity.pickHoverPoint(mosquito.level(), mosquito.getRandom(),
                mosquito.position(), Config.MOSQUITO_WANDER_RADIUS.get(), true);
        return wanderTarget != null;
    }

    @Override
    public void start() {
        mosquito.getNavigation().moveTo(wanderTarget.x, wanderTarget.y, wanderTarget.z, 1.0D);
    }

    @Override
    public boolean canContinueToUse() {
        return mosquito.getTarget() == null && !mosquito.getNavigation().isDone();
    }

    @Override
    public void stop() {
        // Arrived (or was interrupted): hover in place until the next wander pick.
        hoverTicks = Config.MOSQUITO_WANDER_INTERVAL_TICKS.get();
        mosquito.getNavigation().stop();
    }
}
