package net.kenji.icarus_additions;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * SERVER config (serverconfig/icarus_additions-server.toml in each world). The server's values are synced to every
 * client on join, so charge time, lift height and ascent speed are identical for everyone and can't be changed
 * client-side.
 */
public final class ConfigServer {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue BASE_CHARGE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue BASE_LIFT_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue LIFT_LEVEL_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue CHARGE_SECONDS_LEVEL_MULTIPLIER;

    public static final ForgeConfigSpec.DoubleValue ASCENT_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue LAUNCH_DELAY_TICKS;
    public static final ForgeConfigSpec.BooleanValue PARTICLES;

    public static final ForgeConfigSpec.BooleanValue TEMPEST_ENABLED;
    public static final ForgeConfigSpec.DoubleValue BASE_TEMPEST_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TEMPEST_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TEMPEST_COOLDOWN_MULTIPLIER;

    public static final ForgeConfigSpec.BooleanValue ZEPHYR_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ZEPHYR_BASE_MOVE_SPEED;
    public static final ForgeConfigSpec.DoubleValue ZEPHYR_BASE_VERTICAL_SPEED;
    public static final ForgeConfigSpec.DoubleValue ZEPHYR_SPEED_LEVEL_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("Updraft");

        ENABLED = b.comment("Enables the Updraft enchantment's ability (crouch still to charge, jump to launch).")
                .define("enabled", true);

        BASE_CHARGE_SECONDS = b.comment("How long the player has to crouch without moving before the wings spread and the launch is ready.")
                .defineInRange("chargeSeconds", 4.5, 0.0, 60.0);

        BASE_LIFT_HEIGHT = b.comment("How many blocks Updraft I lifts the player.")
                .defineInRange("baseLiftHeight", 8.0, 1.0, 128.0);

        LIFT_LEVEL_MULTIPLIER = b.comment(
                        "Each level above I multiplies the lift height by this.",
                        "With the defaults (8 blocks, x1.5): I = 8, II = 12, III = 18 blocks.")
                .defineInRange("liftLevelMultiplier", 1.5, 1.0, 10.0);
        CHARGE_SECONDS_LEVEL_MULTIPLIER = b.comment(
                        "Each level above I divides the updraft charge time by this",
                        "With the defaults (4.5 blocks / 1.35): I = 3.3s, II = 2.4s, III = 1.82 seconds.")
                .defineInRange("chargeSecondsLevelMultiplier", 1.75, 1.0, 10.0);

        ASCENT_SPEED_MULTIPLIER = b.comment(
                        "Launch speed: how fast the player leaves the ground (1.0 = 1.4 blocks/tick, 2.0 = twice that).",
                        "The lift then slows down the whole way up, like a jump against gravity, and always stops at the",
                        "enchantment's lift height. Higher = a punchier launch and a shorter climb.")
                .defineInRange("ascentSpeedMultiplier", 1.0, 0.1, 5.0);

        LAUNCH_DELAY_TICKS = b.comment(
                        "Ticks between pressing jump and actually leaving the ground, so the lift lines up with the wings'",
                        "downstroke (the heavy flap lasts 10 ticks; the downstroke is roughly ticks 2-5). 0 = instant.")
                .defineInRange("launchDelayTicks", 4, 0, 10);

        PARTICLES = b.comment("Upward draft particles around the player while charged, and a burst on launch.")
                .define("particles", true);

        b.pop();

        b.push("Tempest");

        TEMPEST_ENABLED = b.comment("Enables the Tempest enchantment's ability (press sprint while flying to spin dash, like Riptide).")
                .define("enabled", true);

        BASE_TEMPEST_COOLDOWN_SECONDS = b.comment(
                        "Base Seconds before another dash can be used. Shown as a cooldown on the wings item.",
                        "0 = no cooldown (dashes can be chained back to back).")
                .defineInRange("cooldownSeconds", 8.0, 0.0, 60.0);

        TEMPEST_SPEED_MULTIPLIER = b.comment(
                        "Multiplies the dash strength. 1.0 = exactly Riptide's push for the same level",
                        "(I = 1.5, II = 2.25, III = 3.0 blocks/tick).")
                .defineInRange("speedMultiplier", 1.0, 0.1, 5.0);
        TEMPEST_COOLDOWN_MULTIPLIER = b.comment(
                        "Divides the base tempest dash cooldown for ever level after I",
                        "(I = 8s, II = 5.3s, III = 3.5s cooldown).")
                .defineInRange("tempestCooldownDivideAmount", 1.5, 0.1, 5.0);

        b.pop();

        b.push("Zephyr");

        ZEPHYR_ENABLED = b.comment(
                        "Enables the Zephyr enchantment's controllable hover: press crouch mid-flight to brake into a hover,",
                        "then jump to rise, crouch to sink and move around with the normal movement keys.",
                        "Double-tap crouch (or land) to leave the hover.")
                .define("enabled", true);

        ZEPHYR_BASE_MOVE_SPEED = b.comment(
                        "Top horizontal speed while hovering with Zephyr I, in blocks per second (walking is ~4.3).")
                .defineInRange("baseMoveSpeed", 2.5, 0.1, 40.0);

        ZEPHYR_BASE_VERTICAL_SPEED = b.comment(
                        "Rise/sink speed while hovering with Zephyr I (holding jump / crouch), in blocks per second.")
                .defineInRange("baseVerticalSpeed", 2.0, 0.1, 40.0);

        ZEPHYR_SPEED_LEVEL_MULTIPLIER = b.comment(
                        "Each level above I multiplies both hover speeds by this.",
                        "With the defaults (2.5 b/s, x1.25): I = 2.5, II = 3.1, III = 3.9 blocks per second.")
                .defineInRange("speedLevelMultiplier", 1.25, 1.0, 10.0);

        b.pop();
        SPEC = b.build();
    }

    private ConfigServer() {}
}
