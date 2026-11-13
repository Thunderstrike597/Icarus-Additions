package net.kenji.icarus_additions.mixin;

import dev.cammiescorner.icarus.client.models.WingEntityModel;
import dev.cammiescorner.icarus.client.renderers.WingsLayer;
import net.kenji.icarus_additions.client.WingFlexAnimator;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wraps Icarus' wingModel.setupAnim(...) call inside WingsLayer.render so we can bend the elbow right after
 * Icarus has posed the wing roots, and right before it renders.
 *
 * render() and setupAnim() override vanilla methods, so they're SRG-renamed in production (m_6494_ / m_6973_).
 * Instead of relying on the annotation processor to remap a non-vanilla class, there are two redirects with
 * remap = false: one for dev (Mojang names) and one for production (SRG names). Only one ever applies.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(value = WingsLayer.class, remap = false)
public abstract class WingsLayerMixin {

    // Dev environment (official mappings)
    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/cammiescorner/icarus/client/models/WingEntityModel;setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"),
            require = 0, remap = false)
    private void icarusWingFlex$setupAnimDev(WingEntityModel model, LivingEntity entity, float limbAngle, float limbDistance,
                                              float ageInTicks, float headYaw, float headPitch) {
        icarusWingFlex$setup(model, entity, limbAngle, limbDistance, ageInTicks, headYaw, headPitch);
    }

    // Production (SRG names): RenderLayer.render = m_6494_, EntityModel.setupAnim = m_6973_
    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/cammiescorner/icarus/client/models/WingEntityModel;setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"),
            require = 0, remap = false)
    private void icarusWingFlex$setupAnimProd(WingEntityModel model, LivingEntity entity, float limbAngle, float limbDistance,
                                               float ageInTicks, float headYaw, float headPitch) {
        icarusWingFlex$setup(model, entity, limbAngle, limbDistance, ageInTicks, headYaw, headPitch);
    }

    private static void icarusWingFlex$setup(WingEntityModel model, LivingEntity entity, float limbAngle, float limbDistance,
                                             float ageInTicks, float headYaw, float headPitch) {
        WingFlexAnimator.beforeSetup(model);
        // Called through the vanilla type so reobf maps it straight to m_6973_; dispatches to Icarus' override
        ((EntityModel) model).setupAnim(entity, limbAngle, limbDistance, ageInTicks, headYaw, headPitch);
        WingFlexAnimator.afterSetup(model, entity, ageInTicks);
    }
}
