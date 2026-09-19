package com.fiskerz.apolinum_arise.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The one-time, permanent choices that shape a player's skill side (Phase 11).
 *
 * <ul>
 *   <li><b>Infected side:</b> {@code infectedVariant} 0-2, rolled once at the exact moment infected-side
 *       access is granted (the next Blood Moon after infection completes).</li>
 *   <li><b>Healthy side:</b> three stats rolled once when the book grants healthy-side access, then a
 *       {@code healthyBranch} 0-3 the player picks themselves the first time they open the skill screen.</li>
 * </ul>
 *
 * <p>{@link #UNASSIGNED} (-1) is the "not yet" value for both the variant and the branch, so the assignment
 * points can be strictly one-shot: once a field is set it is never rolled or overwritten again. Stats carry
 * a separate {@code statsAssigned} flag rather than a sentinel, because a configured stat range is allowed
 * to include 0 and a rolled 0 must not read as "unrolled".
 *
 * <p>Kept separate from {@link SkillAccessData} deliberately: access is granted, cleared and re-granted as
 * players move between the two sides, whereas everything here is permanent once written. The stream codec
 * is hand-written rather than {@code StreamCodec.composite} because that helper caps at six components and
 * this record is already at six - the next field added would have forced the rewrite anyway.
 */
public record SkillProfileData(int infectedVariant, boolean statsAssigned, int intelligence, int strength,
                               int creativity, int healthyBranch) {

    /** Neither side has been assigned anything yet. */
    public static final int UNASSIGNED = -1;

    public static final SkillProfileData NONE =
            new SkillProfileData(UNASSIGNED, false, 0, 0, 0, UNASSIGNED);

    public static final Codec<SkillProfileData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("infectedVariant", UNASSIGNED).forGetter(SkillProfileData::infectedVariant),
            Codec.BOOL.optionalFieldOf("statsAssigned", false).forGetter(SkillProfileData::statsAssigned),
            Codec.INT.optionalFieldOf("intelligence", 0).forGetter(SkillProfileData::intelligence),
            Codec.INT.optionalFieldOf("strength", 0).forGetter(SkillProfileData::strength),
            Codec.INT.optionalFieldOf("creativity", 0).forGetter(SkillProfileData::creativity),
            Codec.INT.optionalFieldOf("healthyBranch", UNASSIGNED).forGetter(SkillProfileData::healthyBranch)
    ).apply(instance, SkillProfileData::new));

    public static final StreamCodec<ByteBuf, SkillProfileData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillProfileData decode(ByteBuf buffer) {
            return new SkillProfileData(buffer.readInt(), buffer.readBoolean(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt());
        }

        @Override
        public void encode(ByteBuf buffer, SkillProfileData value) {
            buffer.writeInt(value.infectedVariant);
            buffer.writeBoolean(value.statsAssigned);
            buffer.writeInt(value.intelligence);
            buffer.writeInt(value.strength);
            buffer.writeInt(value.creativity);
            buffer.writeInt(value.healthyBranch);
        }
    };

    public boolean hasInfectedVariant() {
        return infectedVariant != UNASSIGNED;
    }

    public boolean hasHealthyBranch() {
        return healthyBranch != UNASSIGNED;
    }

    public SkillProfileData withInfectedVariant(int variant) {
        return new SkillProfileData(variant, statsAssigned, intelligence, strength, creativity, healthyBranch);
    }

    public SkillProfileData withStats(int intelligence, int strength, int creativity) {
        return new SkillProfileData(infectedVariant, true, intelligence, strength, creativity, healthyBranch);
    }

    public SkillProfileData withHealthyBranch(int branch) {
        return new SkillProfileData(infectedVariant, statsAssigned, intelligence, strength, creativity, branch);
    }

    /**
     * Drop the two one-shot assignments so they can be made again, keeping the rolled stats. Used by the
     * debug reset alongside un-completing that player's quest gates: clearing the gates without clearing
     * these would leave a player who is locked out of content but can never be re-assigned to any.
     */
    public SkillProfileData withoutAssignments() {
        return new SkillProfileData(UNASSIGNED, statsAssigned, intelligence, strength, creativity, UNASSIGNED);
    }

    /** Read one stat by its enum, so screens and tooltips can iterate instead of switching. */
    public int stat(HealthyStat stat) {
        return switch (stat) {
            case INTELLIGENCE -> intelligence;
            case STRENGTH -> strength;
            case CREATIVITY -> creativity;
        };
    }
}
