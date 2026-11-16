package net.kenji.icarus_additions;

import com.mojang.logging.LogUtils;
import net.kenji.icarus_additions.compat.epicfight.EpicFightCompat;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

@Mod(IcarusAdditions.MOD_ID)
public class IcarusAdditions {
    public static final String MOD_ID = "icarus_additions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isLoaded(String modid) {
        return FMLLoader.getLoadingModList().getModFileById(modid) != null;
    }

    public static boolean EFM_LOADED = IcarusAdditions.isLoaded("epicfight");


    public IcarusAdditions() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, ConfigClient.SPEC);
        // Gameplay settings for Updraft: SERVER config, synced from the server to every client
        ctx.registerConfig(ModConfig.Type.SERVER, ConfigServer.SPEC);
        IcarusAdditionsEnchantments.register(modEventBus);
        IcarusAdditionsNetwork.register();

        // Optional on either side: visuals are client-only, and the hover falls back to client-side if the server
        // doesn't have the mod (the network channel is acceptMissingOr as well).
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        if (EFM_LOADED) {
            // All Epic Fight references live in the compat package, so nothing here loads them without Epic Fight
            EpicFightCompat.init(modEventBus);
        }

    }

}

