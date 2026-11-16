package net.kenji.icarus_additions.mechanisms.updraft;

public enum UpdraftPhase {
    /** Nothing going on (may still be counting the crouch timer on the server). */
    NONE,
    /** Crouched still past the threshold: wings spread, draft particles, jump to launch. */
    CHARGED,
    /** One heavy flap and rising to the target height, then Icarus flight starts. */
    LAUNCHING;

    private static final UpdraftPhase[] VALUES = values();

    public static UpdraftPhase byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
