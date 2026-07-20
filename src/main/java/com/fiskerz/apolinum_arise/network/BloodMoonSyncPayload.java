package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BloodMoonSyncPayload(boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BloodMoonSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bloodmoon_sync"));

    public static final StreamCodec<ByteBuf, BloodMoonSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BloodMoonSyncPayload::active,
            BloodMoonSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}