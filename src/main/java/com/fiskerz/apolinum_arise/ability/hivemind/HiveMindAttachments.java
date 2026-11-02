package com.fiskerz.apolinum_arise.ability.hivemind;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class HiveMindAttachments {
    private HiveMindAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // serialize: the cooldown survives a relog. copyOnDeath: dying must not be a way to reset it. Not synced -
    // the client sees the cooldown through the vanilla item-cooldown packet, which is all it needs.
    public static final Supplier<AttachmentType<CallOfTheHiveData>> CALL_OF_THE_HIVE =
            ATTACHMENT_TYPES.register("call_of_the_hive",
                    () -> AttachmentType.builder(() -> CallOfTheHiveData.READY)
                            .serialize(CallOfTheHiveData.CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
