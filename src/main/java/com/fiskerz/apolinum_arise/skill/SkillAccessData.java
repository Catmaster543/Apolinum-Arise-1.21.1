package com.fiskerz.apolinum_arise.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player skill access, stored as a NeoForge data attachment. The two sides are mutually exclusive
 * by construction (a player can only ever hold one at a time in normal play). Synced to the OWNING
 * client only so the (hidden) inventory button/keybind can decide whether to open the GUI.
 *
 * <p>This is deliberately just the two access flags for now. Actual per-side skill PROGRESS (points,
 * unlocked nodes) will be added as fields here in a later content task; the healthy-side clear on
 * infection (mutual exclusivity) and the cure stub already reset the whole side, so adding progress
 * fields later needs no change to those call sites.
 */
public record SkillAccessData(boolean healthyAccess, boolean infectedAccess) {
    public static final SkillAccessData NONE = new SkillAccessData(false, false);

    public static final Codec<SkillAccessData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("healthyAccess", false).forGetter(SkillAccessData::healthyAccess),
            Codec.BOOL.optionalFieldOf("infectedAccess", false).forGetter(SkillAccessData::infectedAccess)
    ).apply(instance, SkillAccessData::new));

    public static final StreamCodec<ByteBuf, SkillAccessData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SkillAccessData::healthyAccess,
            ByteBufCodecs.BOOL, SkillAccessData::infectedAccess,
            SkillAccessData::new);

    public boolean hasAny() {
        return healthyAccess || infectedAccess;
    }

    /** Grants healthy access. Mutually exclusive, so infected access is cleared at the same time. */
    public SkillAccessData grantHealthy() {
        return new SkillAccessData(true, false);
    }

    /** Grants infected access. Mutually exclusive, so healthy access is cleared at the same time. */
    public SkillAccessData grantInfected() {
        return new SkillAccessData(false, true);
    }

    /** Clears the healthy side (access + future progress); infected side untouched. */
    public SkillAccessData clearedHealthy() {
        return new SkillAccessData(false, infectedAccess);
    }

    /** Clears the infected side (access + future progress); healthy side untouched. */
    public SkillAccessData clearedInfected() {
        return new SkillAccessData(healthyAccess, false);
    }
}
