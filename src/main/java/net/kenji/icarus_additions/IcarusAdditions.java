package net.kenji.icarus_additions;

import com.mojang.logging.LogUtils;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

@Mod(IcarusAdditions.MOD_ID)
public class IcarusAdditions {
    public static final String MOD_ID = "icarus_additions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IcarusAdditions() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, WingFlexConfig.SPEC);
        IcarusAdditionsNetwork.register();

        // Optional on either side: visuals are client-only, and the hover falls back to client-side if the server
        // doesn't have the mod (the network channel is acceptMissingOr as well).
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
