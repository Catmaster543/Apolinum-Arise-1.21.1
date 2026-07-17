package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: "the awakening happened at {pos} in {dimension}". Each client plays the
 * awakening sound locally, scaling volume by its own player's distance to the position.
 */
public record AwakeningSoundPayload(BlockPos pos, ResourceLocation dimension) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AwakeningSoundPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "awakening_sound"));

    public static final StreamCodec<ByteBuf, AwakeningSoundPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AwakeningSoundPayload::pos,
            ResourceLocation.STREAM_CODEC, AwakeningSoundPayload::dimension,
            AwakeningSoundPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
