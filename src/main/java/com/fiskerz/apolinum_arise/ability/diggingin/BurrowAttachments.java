package com.fiskerz.apolinum_arise.ability.diggingin;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BurrowAttachments {
    private BurrowAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // BROADCAST sync (not self-only): other clients need it to render the burrowed player's swimming pose,
    // the same reasoning as the downed system's pose data. serialize() so a burrow survives a relog long
    // enough to be re-validated and ejected on login - a crash must never strand someone in a wall.
    // NOT copyOnDeath: death force-ejects, so a fresh life always starts NORMAL.
    public static final Supplier<AttachmentType<BurrowData>> BURROW = ATTACHMENT_TYPES.register("burrow",
            () -> AttachmentType.builder(() -> BurrowData.NORMAL)
                    .serialize(BurrowData.CODEC)
                    .sync((holder, receiver) -> true, BurrowData.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
