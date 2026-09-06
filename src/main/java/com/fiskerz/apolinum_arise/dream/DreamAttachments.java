package com.fiskerz.apolinum_arise.dream;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class DreamAttachments {
    private DreamAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // Server-only (no sync): playback is driven entirely server-side, so the client never needs the queue.
    // serialize + copyOnDeath so a queued dream survives relog AND death - it is owed to the player.
    public static final Supplier<AttachmentType<DreamData>> DREAMS = ATTACHMENT_TYPES.register("dreams",
            () -> AttachmentType.builder(() -> DreamData.EMPTY)
                    .serialize(DreamData.CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
