package net.kenji.icarus_additions;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WingFlexConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue DOWN_BEND_AMOUNT;
    public static final ForgeConfigSpec.DoubleValue UP_BEND_AMOUNT;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_LAG;
    public static final ForgeConfigSpec.DoubleValue MAX_BEND_DEGREES;
    public static final ForgeConfigSpec.BooleanValue BEND_ZANZA;

    public static final ForgeConfigSpec.BooleanValue DIP_ENABLED;
    public static final ForgeConfigSpec.DoubleValue DIP_DEPTH;
    public static final ForgeConfigSpec.BooleanValue CAMERA_FOLLOWS_DIP;

    public static final ForgeConfigSpec.BooleanValue HOVER_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HOVER_SLOW_DOWN_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue HOVER_POP_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HOVER_WING_LIFT;
    public static final ForgeConfigSpec.DoubleValue HOVER_POP_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue CAMERA_FOLLOWS_HOVER_POP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("wing_flex");

        ENABLED = b.comment("Master toggle for the elbow bend.")
                .define("enabled", true);

        DOWN_BEND_AMOUNT = b.comment(
                        "How much extra the outer half of the wing rotates on the DOWN part of the flap,",
                        "relative to the flap of the inner half.",
                        "0.0 = vanilla Icarus (stiff), 0.5 = outer half flaps 50% further, 1.0 = double.")
                .defineInRange("downBendAmount", 1.8, 0.0, 3.0);

        UP_BEND_AMOUNT = b.comment(
                        "Same as downBendAmount, but for the UP part of the flap.",
                        "Icarus models have the outer half drooping down at rest, so an upward bend of the same size",
                        "only straightens the wing out. Set this higher than downBendAmount to see the tips actually curl up.")
                .defineInRange("upBendAmount", 1.65, 0.0, 3.0);

        FOLLOW_LAG = b.comment(
                        "How far (in ticks) the outer half trails behind the inner half. Gives a whip / follow-through feel.",
                        "0 = outer half moves in perfect sync with the inner half.")
                .defineInRange("followLag", 2.0, 0.0, 20.0);

        MAX_BEND_DEGREES = b.comment("Hard clamp on the elbow bend, in degrees, to stop clipping on extreme flaps.")
                .defineInRange("maxBendDegrees", 50.0, 0.0, 120.0);

        BEND_ZANZA = b.comment("Also bend Zanza's wings (two-piece wing with a ring at the end).")
                .define("bendZanzasWings", true);

        b.pop();

        b.push("flap_dip");

        DIP_ENABLED = b.comment(
                        "Visually dips the player down on each upstroke and back up on each downstroke while flapping",
                        "(fall flying + holding forward). Plain gliding doesn't dip. Purely visual: your real position,",
                        "hitbox and physics are unchanged.")
                .define("enabled", true);

        DIP_DEPTH = b.comment("How far below the normal position the player dips at the top of the wing stroke, in blocks.")
                .defineInRange("dipDepth", 0.25, 0.0, 2.0);

        CAMERA_FOLLOWS_DIP = b.comment(
                        "false = the third person camera stays where it would be without the dip, so you can see the",
                        "player bob up and down on screen. true = the camera moves down with the player.")
                .define("cameraFollowsDip", false);

        b.pop();

        b.push("hover");

        HOVER_ENABLED = b.comment(
                        "Crouch while flying with Icarus wings to stop and hover: the player turns upright with wings",
                        "spread, brakes quickly, then hangs in the air with hidden Slow Falling until crouch is released.",
                        "Crouching on the ground or while falling without having been flying does nothing.")
                .define("enabled", true);

        HOVER_SLOW_DOWN_INTENSITY = b.comment(
                        "How hard the player brakes after crouching mid-flight, before settling into the hover.",
                        "1.0 = about 2 seconds from fast flight, 2.0 = about 1 second, 4.0 = about half a second.",
                        "Lower values coast further.")
                .defineInRange("slowDownIntensity", 1.5, 0.1, 10.0);

        HOVER_WING_LIFT = b.comment(
                        "How far up the player's back the wings move while braking/hovering, in model pixels (1/16 block).",
                        "Icarus places the wings low for the horizontal flying pose; upright they look too low. 0 = unchanged.")
                .defineInRange("wingLift", 3.0, -8.0, 16.0);

        HOVER_POP_ENABLED = b.comment(
                        "While hovering, visually pops the player upward on each downstroke and back down to the normal",
                        "position on each upstroke (the mirror of the gliding flap dip). Purely visual, like the dip.")
                .define("popEnabled", true);

        HOVER_POP_HEIGHT = b.comment("How far above the normal position the player rises at the bottom of the downstroke, in blocks.")
                .defineInRange("popHeight", 0.25, 0.0, 2.0);

        CAMERA_FOLLOWS_HOVER_POP = b.comment(
                        "false = the third person camera stays put and you see the player bob upward on screen.",
                        "true = the camera moves up with the player.")
                .define("cameraFollowsPop", false);

        b.pop();
        SPEC = b.build();
    }

    private WingFlexConfig() {}
}
