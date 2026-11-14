package net.kenji.icarus_additions.mixin;

import net.kenji.icarus_additions.WingFlexConfig;
import net.kenji.icarus_additions.client.WingFlexAnimator;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only used when cameraFollowsDip = true. The dip is render-only, so by default the camera already stays where it
 * would be without the dip; this moves the third person camera down along with the player instead.
 * Vanilla target, so this one is remapped normally through the refmap.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void icarusWingFlex$followDip(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                          float partialTick, CallbackInfo ci) {
        if (!detached) return;
        float offset = 0.0F;
        if (WingFlexConfig.CAMERA_FOLLOWS_DIP.get() && WingFlexConfig.DIP_ENABLED.get()) {
            offset += WingFlexAnimator.getDip(entity, partialTick);
        }
        if (WingFlexConfig.CAMERA_FOLLOWS_HOVER_POP.get() && WingFlexConfig.HOVER_POP_ENABLED.get()) {
            offset += WingFlexAnimator.getPop(entity, partialTick);
        }
        if (offset != 0.0F) {
            setPosition(getPosition().add(0.0D, offset, 0.0D));
        }
    }
}
