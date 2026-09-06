package com.fiskerz.apolinum_arise.skill;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SkillAttachments {
    private SkillAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // serialize(CODEC) persists across relog; copyOnDeath() keeps access through death/respawn (it is an
    // account-level unlock, not tied to a life). sync(self-only) mirrors it to the owning client so the
    // hidden inventory button/keybind can read "do I have access".
    public static final Supplier<AttachmentType<SkillAccessData>> SKILL_ACCESS = ATTACHMENT_TYPES.register("skill_access",
            () -> AttachmentType.builder(() -> SkillAccessData.NONE)
                    .serialize(SkillAccessData.CODEC)
                    .sync((holder, receiver) -> holder == receiver, SkillAccessData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    // The permanent Phase 11 choices (infected variant, healthy stats, healthy branch). Same three
    // properties as the access flags and for the same reasons - persisted, kept through death, and mirrored
    // to the owning client, which is what lets the skill button route to the branch-choice screen or
    // straight to the quest book without a round trip. Separate from SKILL_ACCESS because access is
    // granted and revoked as players change sides, while none of this is ever unwritten.
    public static final Supplier<AttachmentType<SkillProfileData>> SKILL_PROFILE = ATTACHMENT_TYPES.register("skill_profile",
            () -> AttachmentType.builder(() -> SkillProfileData.NONE)
                    .serialize(SkillProfileData.CODEC)
                    .sync((holder, receiver) -> holder == receiver, SkillProfileData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
