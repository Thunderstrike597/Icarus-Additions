package net.kenji.icarus_additions.mixin;

import net.kenji.icarus_additions.compat.epicfight.IcarusAdditionsAnimations;
import net.kenji.icarus_additions.compat.epicfight.IcarusAdditionsLivingMotions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

@Mixin(value = PlayerPatch.class, remap = false)
public class PlayerPatchMixin {

    @Inject(method = "initAnimator", at = @At("HEAD"))
    private void addLivingMotions(Animator animator, CallbackInfo ci){
        animator.addLivingAnimation(IcarusAdditionsLivingMotions.AIR_BREAK, IcarusAdditionsAnimations.AIR_BREAK);
        animator.addLivingAnimation(IcarusAdditionsLivingMotions.UPDRAFT, IcarusAdditionsAnimations.UPDRAFT);
        animator.addLivingAnimation(IcarusAdditionsLivingMotions.UPDRAFT_CHARGED, IcarusAdditionsAnimations.UPDRAFT_CHARGED);
        animator.addLivingAnimation(IcarusAdditionsLivingMotions.ZEPHYR_FORWARD, IcarusAdditionsAnimations.ZEPHYR_FORWARD);
        animator.addLivingAnimation(IcarusAdditionsLivingMotions.ZEPHYR_BACKWARD, Animations.BIPED_CREATIVE_FLYING);
        animator.addLivingAnimation(IcarusAdditionsLivingMotions.ZEPHYR_DOWN, IcarusAdditionsAnimations.ZEPHYR_DOWNWARD);



    }
}
