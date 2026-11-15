package net.kenji.icarus_additions;

import com.mojang.logging.LogUtils;
import net.kenji.icarus_additions.compat.epicfight.IcarusAdditionsAnimations;
import net.kenji.icarus_additions.compat.epicfight.IcarusAdditionsLivingMotions;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.event.EpicFightHooks;

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
        ctx.registerConfig(ModConfig.Type.CLIENT, WingFlexConfig.SPEC);
        IcarusAdditionsNetwork.register();

        // Optional on either side: visuals are client-only, and the hover falls back to client-side if the server
        // doesn't have the mod (the network channel is acceptMissingOr as well).
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        if (EFM_LOADED) {
            LivingMotion.ENUM_MANAGER.registerEnumCls(IcarusAdditions.MOD_ID, IcarusAdditionsLivingMotions.class);

            modEventBus.addListener(this::registerEpicFightAnimations);
        }

    }

    public void registerEpicFightAnimations(AnimationManager.AnimationRegistryEvent event){
        event.newBuilder(MOD_ID, IcarusAdditionsAnimations::buildAnimations);
    }
}

