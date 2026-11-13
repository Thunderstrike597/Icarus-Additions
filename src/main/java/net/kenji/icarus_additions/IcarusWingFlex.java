package net.kenji.icarus_additions;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

@Mod(IcarusWingFlex.MOD_ID)
public class IcarusWingFlex {
    public static final String MOD_ID = "icarus_additions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IcarusWingFlex() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, WingFlexConfig.SPEC, "IcarusAdditions-Client.toml");

        // Purely visual, client-side mod: don't require it on servers / don't flag version mismatch.
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
