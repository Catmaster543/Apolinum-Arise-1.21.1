package com.fiskerz.apolinum_arise.sleep;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SleepAttachments {
    private SleepAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // serialize(CODEC) persists across relog - the whole point of the pass-out flag being here rather than
    // in session memory ("rejoining is of no use"). Broadcast sync (not self-only) because every tracking
    // client renders the passed-out pose. copyOnDeath keeps exhaustion across a death for the same reason
    // relogging does not clear it; a passed-out player refills while down, so they always recover.
    public static final Supplier<AttachmentType<SleepData>> SLEEP = ATTACHMENT_TYPES.register("sleep",
            () -> AttachmentType.builder(() -> SleepData.FULL)
                    .serialize(SleepData.CODEC)
                    .sync((holder, receiver) -> true, SleepData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
