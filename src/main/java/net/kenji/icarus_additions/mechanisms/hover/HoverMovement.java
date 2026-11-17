package net.kenji.icarus_additions.mechanisms.hover;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * How an entity moved this tick, split into components relative to the way it FACES.
 *
 * Why facing and not the camera/input: with camera-decouple mods (Shoulder Surfing, Epic Fight's TPS camera, ...)
 * pressing "back" turns the player around to face the camera and they move the way they face. Relative to the
 * camera that's backward, but relative to the player it's forward, which is what an animation wants. In vanilla
 * first/third person the player faces the camera's direction, so "back" still comes out as BACKWARD.
 *
 * It uses the actual position change (x - xo, ...), so it doesn't care HOW the movement was produced (input, a mod
 * remapping input, knockback) and works the same for the local player and for other players.
 *
 * This is the general form of the Phantom Ascent math: that skill turns the input into a world yaw
 * (camera yaw + 0/45/90 degrees) and then compares it to the model yaw it sets. Here the world direction comes
 * from the real motion and is projected onto the facing yaw: forward = motion . (-sin yaw, cos yaw),
 * left = motion . (cos yaw, sin yaw) (the same axes vanilla's moveRelative uses for zza/xxa).
 *
 * @param forward  blocks/tick along the facing direction (negative = backward)
 * @param strafe   blocks/tick to the facing's left (negative = right)
 * @param vertical blocks/tick up (negative = down)
 */
public record HoverMovement(double forward, double strafe, double vertical) {
    public static final HoverMovement ZERO = new HoverMovement(0.0D, 0.0D, 0.0D);

    /** Below this (blocks/tick, ~0.4 blocks/second) an axis counts as not moving. */
    public static final double THRESHOLD = 0.02D;

    /** Movement relative to the entity's own yaw (getYRot), which decouple mods turn toward the movement. */
    public static HoverMovement of(Entity entity) {
        return of(entity, entity.getYRot());
    }

    /**
     * Movement relative to any yaw you pass, e.g. Epic Fight's model yaw if you want that instead of the entity yaw,
     * or the camera yaw if you want camera-relative directions.
     */
    public static HoverMovement of(Entity entity, float facingYawDegrees) {
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        float yaw = facingYawDegrees * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        double forward = dx * -sin + dz * cos;
        double strafe = dx * cos + dz * sin;
        return new HoverMovement(forward, strafe, dy);
    }

    public double horizontalSpeed() {
        return Math.sqrt(forward * forward + strafe * strafe);
    }

    /** FORWARD / BACKWARD / LEFT / RIGHT by whichever horizontal axis is bigger, NONE if barely moving. */
    public HoverMoveDirection horizontalDirection() {
        if (Math.abs(forward) < THRESHOLD && Math.abs(strafe) < THRESHOLD) return HoverMoveDirection.NONE;
        if (Math.abs(forward) >= Math.abs(strafe)) {
            return forward > 0.0D ? HoverMoveDirection.FORWARD : HoverMoveDirection.BACKWARD;
        }
        return strafe > 0.0D ? HoverMoveDirection.LEFT : HoverMoveDirection.RIGHT;
    }

    /** UP / DOWN / NONE. */
    public HoverMoveDirection verticalDirection() {
        if (Math.abs(vertical) < THRESHOLD) return HoverMoveDirection.NONE;
        return vertical > 0.0D ? HoverMoveDirection.UP : HoverMoveDirection.DOWN;
    }

    /** The single main direction: vertical wins only if it's faster than the horizontal movement. */
    public HoverMoveDirection dominant() {
        HoverMoveDirection horizontal = horizontalDirection();
        HoverMoveDirection vert = verticalDirection();
        if (vert == HoverMoveDirection.NONE) return horizontal;
        if (horizontal == HoverMoveDirection.NONE) return vert;
        return Math.abs(vertical) > horizontalSpeed() ? vert : horizontal;
    }
}
