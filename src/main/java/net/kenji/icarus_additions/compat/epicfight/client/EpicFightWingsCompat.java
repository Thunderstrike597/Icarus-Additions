package net.kenji.icarus_additions.compat.epicfight.client;

import dev.cammiescorner.icarus.client.renderers.WingsLayer;
import net.kenji.icarus_additions.IcarusAdditions;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.renderer.FirstPersonRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.EmptyLayer;

/**
 * Client-only, Epic Fight-only. Never referenced directly: IcarusAdditions only reaches it through
 * DistExecutor when Epic Fight is loaded, so without Epic Fight this class (and its imports) never loads.
 */
public final class EpicFightWingsCompat {
    private EpicFightWingsCompat() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EpicFightWingsCompat::onModifyRenderers);
    }

    /** Fired after Epic Fight (re)builds its patched renderers, e.g. on every resource reload. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void onModifyRenderers(PatchedRenderersEvent.Modify event) {
        PatchedEntityRenderer playerRenderer = event.get(EntityType.PLAYER);
        if (playerRenderer instanceof PatchedLivingEntityRenderer living) {
            // "Always" = replace, since Epic Fight already cached its generic fallback for this layer class
            living.addPatchedLayerAlways(WingsLayer.class, new PatchedWingsLayer());
        } else {
            IcarusAdditions.LOGGER.warn("[Icarus Additions] Epic Fight player renderer not found, wings won't follow the chest bone");
        }

        // First person: hide the wings like vanilla first person does (they'd otherwise sit around the camera)
        FirstPersonRenderer firstPerson = ClientEngine.getInstance().renderEngine.getFirstPersonRenderer();
        if (firstPerson != null) {
            ((PatchedLivingEntityRenderer) firstPerson).addPatchedLayerAlways(WingsLayer.class, new EmptyLayer());
        }
    }
}
