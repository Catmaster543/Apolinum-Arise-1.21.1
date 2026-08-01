package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server, sent every client tick while the reviver is holding the revive key AND looking at a
 * valid downed target ({@code targetEntityId} = that player's entity id), or {@code -1} the moment the
 * channel breaks (release / line-of-sight loss / out of range). The server accumulates continuity.
 */
public record DownedReviveInputPayload(int targetEntityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DownedReviveInputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "downed_revive_input"));

    public static final StreamCodec<ByteBuf, DownedReviveInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DownedReviveInputPayload::targetEntityId,
            DownedReviveInputPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
