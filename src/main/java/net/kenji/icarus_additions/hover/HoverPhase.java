package net.kenji.icarus_additions.hover;

public enum HoverPhase {
    /** Not hovering. */
    NONE,
    /** Just crouched out of Icarus flight: upright, wings spread, bleeding off speed. */
    BRAKING,
    /** Horizontal speed is ~0: hidden Slow Falling keeps the player hanging in the air. */
    HOVERING;

    private static final HoverPhase[] VALUES = values();

    public static HoverPhase byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
