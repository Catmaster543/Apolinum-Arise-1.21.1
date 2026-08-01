package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: the infected player pressed the inventory key. The client cancels the vanilla
 * inventory screen and sends this so the server opens the real, server-enforced restricted menu.
 */
public record OpenRestrictedInventoryPayload() implements CustomPacketPayload {
    public static final OpenRestrictedInventoryPayload INSTANCE = new OpenRestrictedInventoryPayload();

    public static final CustomPacketPayload.Type<OpenRestrictedInventoryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "open_restricted_inventory"));

    public static final StreamCodec<ByteBuf, OpenRestrictedInventoryPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
