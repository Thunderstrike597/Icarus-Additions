package net.kenji.icarus_additions.compat.epicfight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Renders Icarus' WingsLayer skinned to the armature's Chest joint, so the wings follow the chest through every
 * Epic Fight animation (instead of Epic Fight's generic fallback for unknown layers).
 *
 * poses[] holds each joint's animated model-space transform; multiplying by the joint's toOrigin (inverse rest
 * transform) gives the same skinning matrix Epic Fight uses for meshes. Applied to the vanilla model space, the wings
 * sit exactly where vanilla puts them when the chest is at rest, and move/rotate with the chest when it isn't.
 * Everything Icarus Additions does to the wings (bend, hover pose, updraft beat) still runs, since it hooks inside
 * WingsLayer.render.
 */
public class PatchedWingsLayer<E extends LivingEntity, T extends LivingEntityPatch<E>, M extends EntityModel<E>>
        extends PatchedLayer<E, T, M, RenderLayer<E, M>> {

    private static final String CHEST_JOINT = "Chest";

    @Override
    protected void renderLayer(T entitypatch, E entity, @Nullable RenderLayer<E, M> vanillaLayer, PoseStack poseStack,
                               MultiBufferSource buffer, int packedLight, OpenMatrix4f[] poses, float bob, float yRot,
                               float xRot, float partialTicks) {
        if (vanillaLayer == null) return;

        poseStack.pushPose();

        Joint chest = entitypatch.getArmature().searchJointByName(CHEST_JOINT);
        if (chest != null && chest.getId() < poses.length && poses[chest.getId()] != null) {
            OpenMatrix4f skinning = new OpenMatrix4f(poses[chest.getId()]).mulBack(chest.getToOrigin());
            MathUtils.mulStack(poseStack, skinning);
        }
        // Custom armature without a Chest joint: falls through to the plain (unskinned) vanilla placement below.

        // Vanilla model space, same as LivingEntityRenderer: scale(-1, -1, 1) then translate(0, -1.501, 0)
        poseStack.translate(0.0D, 1.501D, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        vanillaLayer.render(poseStack, buffer, packedLight, entity,
                entity.walkAnimation.position(), entity.walkAnimation.speed(), partialTicks, bob, yRot, xRot);

        poseStack.popPose();
    }
}
