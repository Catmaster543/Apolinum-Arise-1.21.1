package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server, sent once when a healthy-side player clicks one of the four branch buttons on their
 * first-ever skill screen (Phase 11). The choice is permanent, so the server re-validates the index, the
 * player's access, and that they have not already chosen - the client is only asking.
 */
public record ChooseBranchPayload(int branch) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChooseBranchPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "choose_branch"));

    public static final StreamCodec<ByteBuf, ChooseBranchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseBranchPayload::branch,
            ChooseBranchPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
