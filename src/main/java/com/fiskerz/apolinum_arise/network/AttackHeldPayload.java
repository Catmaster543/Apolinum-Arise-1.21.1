package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: "my attack key is down right now" (Phase 13).
 *
 * <p>The server has no native way to know this. {@code ServerboundPlayerInputPacket} carries only strafe,
 * forward, jump and sneak; attacks reach the server as discrete {@code ServerboundSwingPacket} /
 * {@code ServerboundInteractPacket} events, which say "a swing happened", never "the button is still
 * held". So continuous hold has to be reported, exactly as the Phase 7 revive channel does.
 *
 * <p><b>This payload carries the raw key state and nothing else</b> - no target, no charge progress, no
 * completion. Everything that decides whether a strong punch happens (what is being looked at, whether it
 * is in range, whether the hand is empty, how long it has been held, the cooldown) is computed on the
 * server, so a crafted packet can at most claim the player is holding a button they could hold anyway.
 * Sent every client tick while down, and once on release so the server reacts immediately.
 */
public record AttackHeldPayload(boolean held) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AttackHeldPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "attack_held"));

    public static final StreamCodec<ByteBuf, AttackHeldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AttackHeldPayload::held,
            AttackHeldPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
