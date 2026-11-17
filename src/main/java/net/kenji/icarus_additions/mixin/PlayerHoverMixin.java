package net.kenji.icarus_additions.mixin;

import net.kenji.icarus_additions.mechanisms.hover.HoverControlHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zephyr hover hooks. Vanilla target, remapped through the refmap. Runs on both sides. */
@Mixin(Player.class)
public abstract class PlayerHoverMixin {

    /**
     * Airborne movement acceleration. Vanilla uses this in moveRelative for WASD input in the air, so swapping it is
     * the least invasive way to set the hover's movement speed: no velocity writes, input/rotation untouched.
     */
    @Inject(method = "getFlyingSpeed", at = @At("HEAD"), cancellable = true)
    private void icarusAdditions$zephyrMoveSpeed(CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (HoverControlHelper.isControlling(self)) {
            cir.setReturnValue(HoverControlHelper.getAirAcceleration(HoverControlHelper.getLevel(self)));
        }
    }

    /**
     * Jump means "rise" during a Zephyr hover, so jump-to-fly (vanilla elytra, Caelus/Icarus wings) must not start
     * flight. Every flight start, on either side, goes through startFallFlying, so block it here. Leaving the hover
     * (double-tap crouch) makes jump-to-fly work again.
     */
    @Inject(method = "startFallFlying", at = @At("HEAD"), cancellable = true)
    private void icarusAdditions$noFlightDuringZephyrHover(CallbackInfo ci) {
        if (HoverControlHelper.isActive((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
