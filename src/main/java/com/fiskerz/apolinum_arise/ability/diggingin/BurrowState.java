package com.fiskerz.apolinum_arise.ability.diggingin;

/** The two Digging In states. See {@link BurrowData} for why this is stored as a boolean. */
public enum BurrowState {
    /** Above ground, ordinary collision and gravity. */
    NORMAL,
    /** Underground: block collision off, no gravity, free 3D movement, all interaction locked out. */
    BURROWED
}
