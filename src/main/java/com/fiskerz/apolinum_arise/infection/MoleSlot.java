package com.fiskerz.apolinum_arise.infection;

/**
 * Curated list of valid mole attachment points on the vanilla player model. True arbitrary surface
 * placement isn't achievable on the blocky model, so each slot is a hand-picked spot on an arm, leg,
 * the head, or the back/sides of the torso. The front-center torso (chest/pelvis) is deliberately
 * excluded. Offsets are in player-model pixels, in the local space of {@link #part} after its
 * {@code translateAndRotate}. Shared by the server (which picks a slot) and the client render layer
 * (which resolves {@link #part} to a {@code ModelPart}); it must not reference any client-only type.
 */
public enum MoleSlot {
    HEAD_TOP(Part.HEAD, -2.0F, -8.0F, -1.0F, false),
    HEAD_RIGHT(Part.HEAD, -4.0F, -4.0F, 1.0F, false),
    HEAD_LEFT(Part.HEAD, 4.0F, -4.0F, -1.0F, false),
    HEAD_BACK(Part.HEAD, 2.0F, -3.0F, 4.0F, false),

    TORSO_BACK_UPPER(Part.BODY, 2.0F, 3.0F, 2.0F, false),
    TORSO_BACK_LOWER(Part.BODY, -2.0F, 9.0F, 2.0F, false),
    TORSO_BACK_MID(Part.BODY, -1.0F, 6.0F, 2.0F, false),
    TORSO_RIGHT(Part.BODY, -4.0F, 6.0F, 0.0F, false),
    TORSO_LEFT(Part.BODY, 4.0F, 5.0F, 1.0F, false),

    // Outer arm face: x = +/-3 on the wide model, +/-2 on slim (1px thinner) - see armOuter nudge.
    RIGHT_ARM_OUTER(Part.RIGHT_ARM, -3.0F, 4.0F, 0.0F, true),
    LEFT_ARM_OUTER(Part.LEFT_ARM, 3.0F, 4.0F, 0.0F, true),
    // Arm back face (z=2) is the same depth on both models, so no width nudge.
    RIGHT_ARM_BACK(Part.RIGHT_ARM, -1.0F, 8.0F, 2.0F, false),
    LEFT_ARM_BACK(Part.LEFT_ARM, 1.0F, 8.0F, 2.0F, false),

    RIGHT_LEG_OUTER(Part.RIGHT_LEG, -2.0F, 6.0F, 0.0F, false),
    RIGHT_LEG_BACK(Part.RIGHT_LEG, 0.0F, 9.0F, 2.0F, false),
    LEFT_LEG_OUTER(Part.LEFT_LEG, 2.0F, 6.0F, 0.0F, false);

    /** Which player-model part a slot rides on. */
    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    private static final MoleSlot[] VALUES = values();

    private final Part part;
    private final float x;
    private final float y;
    private final float z;
    // Arm slots sit on the outer face, which is 0.5px thinner on the slim ("Alex") model; the render
    // layer nudges the outward offset in for slim so the mole stays flush on both variants.
    private final boolean armOuter;

    MoleSlot(Part part, float x, float y, float z, boolean armOuter) {
        this.part = part;
        this.x = x;
        this.y = y;
        this.z = z;
        this.armOuter = armOuter;
    }

    public Part part() { return part; }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public boolean armOuter() { return armOuter; }

    public static int count() { return VALUES.length; }

    public static MoleSlot byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
