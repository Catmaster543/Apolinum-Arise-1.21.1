package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server, sent once per bite key PRESS (Phase 8) when an infected local player with a full bite
 * bar is aiming at a valid downed healthy target ({@code targetEntityId} = that player's entity id). Unlike
 * the hold-based revive input, this is a single discrete press; the server re-validates everything.
 */
public record BiteAttemptPayload(int targetEntityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BiteAttemptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bite_attempt"));

    public static final StreamCodec<ByteBuf, BiteAttemptPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BiteAttemptPayload::targetEntityId,
            BiteAttemptPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
