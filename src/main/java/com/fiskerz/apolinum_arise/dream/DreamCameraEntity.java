package com.fiskerz.apolinum_arise.dream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * The invisible fly-through camera a dream attaches the player's view to.
 *
 * <p><b>Why not a vanilla {@code Marker}?</b> Marker is the obvious "invisible non-colliding attachment
 * point", but it is deliberately server-only: it registers with {@code clientTrackingRange(0)} and its
 * {@code getAddEntityPacket} throws {@code "Markers should never be sent"}. The client would therefore
 * never have the entity, and {@code ClientboundSetCameraPacket} is a no-op when the client cannot resolve
 * the id - so a Marker camera silently does nothing. This entity is the same idea but actually tracked:
 * no physics, no collision, no gravity, never saved, and drawn with vanilla's {@code NoopRenderer}.
 */
public class DreamCameraEntity extends Entity {
    public DreamCameraEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        // Driven entirely by DreamManager; no self-movement, no physics.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Never persisted - a crash mid-dream must not leave an orphan camera in the world. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
