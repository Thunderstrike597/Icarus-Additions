package net.kenji.icarus_additions.compat.epicfight;

import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.compat.epicfight.client.EpicFightWingsCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;

/**
 * Everything that touches Epic Fight classes during mod construction. Only called when Epic Fight is loaded,
 * so without it this class (and every Epic Fight import) is never loaded.
 */
public final class EpicFightCompat {
    private EpicFightCompat() {}

    public static void init(IEventBus modEventBus) {
        LivingMotion.ENUM_MANAGER.registerEnumCls(IcarusAdditions.MOD_ID, IcarusAdditionsLivingMotions.class);
        modEventBus.addListener(EpicFightCompat::registerAnimations);

        // Wings attached to the Chest joint (client only)
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EpicFightWingsCompat.register(modEventBus));
    }

    private static void registerAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(IcarusAdditions.MOD_ID, IcarusAdditionsAnimations::buildAnimations);
    }
}
