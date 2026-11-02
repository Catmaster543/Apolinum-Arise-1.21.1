package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.Apolinumarise;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: one of the three shared ability keybind slots was pressed (Phase 14).
 *
 * <p>Carries the slot number and nothing else - not which ability, not whether it should succeed. The
 * server looks up what the player actually has in that slot and decides everything from there, so a forged
 * packet can only ever claim "I pressed a key I am able to press".
 */
public record AbilityKeyPressPayload(int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AbilityKeyPressPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "ability_key_press"));

    public static final StreamCodec<ByteBuf, AbilityKeyPressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityKeyPressPayload::slot,
            AbilityKeyPressPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
