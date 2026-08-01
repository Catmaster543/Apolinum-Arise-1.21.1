package com.fiskerz.apolinum_arise.network;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.infection.MoleData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client broadcast of one player's full mole list. Unlike the private infection state, moles
 * are visible to everyone, so this is sent to all players tracking the entity (and the owner). The
 * player is identified by entity id; the receiver resolves it in its own level.
 */
public record MoleSyncPayload(int entityId, List<MoleData> moles) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MoleSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "mole_sync"));

    public static final StreamCodec<ByteBuf, MoleSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MoleSyncPayload::entityId,
            MoleData.STREAM_CODEC.apply(ByteBufCodecs.list()), MoleSyncPayload::moles,
            MoleSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
