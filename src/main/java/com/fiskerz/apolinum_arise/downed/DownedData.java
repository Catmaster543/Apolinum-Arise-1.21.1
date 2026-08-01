package com.fiskerz.apolinum_arise.downed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player downed state (Phase 7), stored as a synced NeoForge attachment so every client that tracks
 * the player (and the player themselves) knows they are down - needed for the pose render on all clients
 * and the HUD/fade/camera on the owner's client. The server's authoritative timeout is derived from
 * {@code enteredGameTime + durationTicks} (no per-tick sync needed). {@code savedFood}/{@code savedSaturation}
 * freeze the downed player's hunger; they are server-only and left out of the network view. {@code bodyYaw}
 * is the body facing frozen the instant they went down, so the rendered corpse never tracks the look
 * direction (Patch B6).
 */
public record DownedData(boolean downed, boolean revivable, int poseVariant, int durationTicks,
                         long enteredGameTime, float bodyYaw, int savedFood, float savedSaturation) {
    public static final DownedData NONE = new DownedData(false, false, 0, 0, 0L, 0.0F, 0, 0.0F);

    public static final Codec<DownedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("downed", false).forGetter(DownedData::downed),
            Codec.BOOL.optionalFieldOf("revivable", false).forGetter(DownedData::revivable),
            Codec.INT.optionalFieldOf("poseVariant", 0).forGetter(DownedData::poseVariant),
            Codec.INT.optionalFieldOf("durationTicks", 0).forGetter(DownedData::durationTicks),
            Codec.LONG.optionalFieldOf("enteredGameTime", 0L).forGetter(DownedData::enteredGameTime),
            Codec.FLOAT.optionalFieldOf("bodyYaw", 0.0F).forGetter(DownedData::bodyYaw),
            Codec.INT.optionalFieldOf("savedFood", 0).forGetter(DownedData::savedFood),
            Codec.FLOAT.optionalFieldOf("savedSaturation", 0.0F).forGetter(DownedData::savedSaturation)
    ).apply(instance, DownedData::new));

    // Network view: everything a client needs to render + decide whether to show the revive indicator
    // (downed + revivable + pose + fade timing + frozen body facing). Saved hunger is server-only.
    // revivable is synced because infection state (which drives eligibility) is NOT broadcast to others.
    public static final StreamCodec<ByteBuf, DownedData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DownedData::downed,
            ByteBufCodecs.BOOL, DownedData::revivable,
            ByteBufCodecs.VAR_INT, DownedData::poseVariant,
            ByteBufCodecs.VAR_INT, DownedData::durationTicks,
            ByteBufCodecs.VAR_LONG, DownedData::enteredGameTime,
            ByteBufCodecs.FLOAT, DownedData::bodyYaw,
            (downed, revivable, poseVariant, durationTicks, enteredGameTime, bodyYaw) ->
                    new DownedData(downed, revivable, poseVariant, durationTicks, enteredGameTime, bodyYaw, 0, 0.0F));

    /** Fraction of the downed timer elapsed at the given game time, clamped to [0,1] (for the fade). */
    public float progress(long gameTime) {
        if (!downed || durationTicks <= 0) {
            return 0.0F;
        }
        float p = (float) (gameTime - enteredGameTime) / (float) durationTicks;
        return Math.max(0.0F, Math.min(1.0F, p));
    }
}
