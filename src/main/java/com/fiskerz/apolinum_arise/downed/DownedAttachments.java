package com.fiskerz.apolinum_arise.downed;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class DownedAttachments {
    private DownedAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // Broadcast sync (to all clients tracking the player AND the owner): everyone who can see the player
    // must know they are downed to render the pose; the owner needs it for HUD/fade/camera. Serialized so
    // the state survives a relog mid-downed. copyOnDeath is irrelevant (real death clears it).
    public static final Supplier<AttachmentType<DownedData>> DOWNED = ATTACHMENT_TYPES.register("downed",
            () -> AttachmentType.builder(() -> DownedData.NONE)
                    .serialize(DownedData.CODEC)
                    .sync(DownedData.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
