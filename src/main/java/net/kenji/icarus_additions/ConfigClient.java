package net.kenji.icarus_additions;

import net.minecraftforge.common.ForgeConfigSpec;

// Client-side config, registered in the main class as ModConfig.Type.CLIENT
public class ConfigClient {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER.comment("Example toggle - replace with your own client options").define("enabled", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
