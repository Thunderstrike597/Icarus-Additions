package net.kenji.icarus_additions.compat.epicfight.client;

import net.kenji.icarus_additions.compat.epicfight.IcarusAdditionsAnimations;
import net.minecraft.world.entity.player.Player;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Client-only, Epic Fight-only. Only call this behind an IcarusAdditions.EFM_LOADED check, so the Epic Fight classes
 * it references never load without Epic Fight.
 */
public final class EpicFightUpdraftAnimations {
    private EpicFightUpdraftAnimations() {}

    /**
     * Plays the launch animation the moment the jump is pressed (local player) or the launch sync arrives (others).
     * UPDRAFT lives on a HIGHEST-priority composite layer (see IcarusAdditionsAnimations), so the living-motion change
     * to the airborne motion on the next tick can't replace it; when it ends, the layer turns off and blends back to
     * whatever the base layer is playing.
     */
    public static void playLaunch(Player player) {
        PlayerPatch<?> patch = EpicFightCapabilities.getPlayerPatch(player);
        if (patch != null && IcarusAdditionsAnimations.UPDRAFT != null) {
            // 0 modifier = just the animation's own transition time (0.1s). A positive value is ADDED to it.
            patch.getClientAnimator().playAnimation(IcarusAdditionsAnimations.UPDRAFT, 0.0F);
        }
    }
}
