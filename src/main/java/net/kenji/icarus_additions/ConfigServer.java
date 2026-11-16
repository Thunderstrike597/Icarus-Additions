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
    public static final ForgeConfigSpec.BooleanValue PARTICLES;

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

        ASCENT_SPEED_MULTIPLIER = b.comment("How fast the player is lifted. 1.0 = default, 2.0 = twice as fast.")
                .defineInRange("ascentSpeedMultiplier", 1.0, 0.1, 5.0);

        PARTICLES = b.comment("Upward draft particles around the player while charged, and a burst on launch.")
                .define("particles", true);

        b.pop();
        SPEC = b.build();
    }

    private ConfigServer() {}
}
