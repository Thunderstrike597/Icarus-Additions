package net.kenji.icarus_additions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kenji.icarus_additions.IcarusAdditions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pushes the rendered model down by the current flap dip, and eases it upright when entering a hover. Only the rendered model moves; the entity's real
 * position, hitbox and camera anchor are untouched, which is why the camera "stays put" by default.
 *
 * Pose stack balance: we push at HIGHEST priority so the translation also applies to mods that cancel the event
 * and render the entity themselves inside their own Pre handler (Epic Fight does this). If the event ends up
 * cancelled, Post never fires, so we pop at LOWEST priority instead; otherwise we pop in Post.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WingDipHandler {
    private static final Deque<LivingEntity> PUSHED = new ArrayDeque<>();

    private WingDipHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreHighest(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        float partialTick = event.getPartialTick();
        float dip = WingFlexAnimator.getDip(entity, partialTick);
        float lean = ClientHoverHandler.getUprightLean(entity, partialTick);
        if (dip == 0.0F && lean == 0.0F) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (dip != 0.0F) {
            pose.translate(0.0D, dip, 0.0D);
        }
        if (lean != 0.0F) {
            // Same rotation vanilla uses for the elytra lean (pivot at the feet, in the body-yaw frame), so the
            // hover starts exactly where the flying pose left off and eases up to standing.
            float bodyYaw = 180.0F - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            pose.mulPose(Axis.YP.rotationDegrees(bodyYaw));
            pose.mulPose(Axis.XP.rotationDegrees(lean));
            pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        }
        PUSHED.push(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPreLowest(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled()) popIfPushed(event);
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        popIfPushed(event);
    }

    private static void popIfPushed(RenderLivingEvent<?, ?> event) {
        if (!PUSHED.isEmpty() && PUSHED.peek() == event.getEntity()) {
            PUSHED.pop();
            event.getPoseStack().popPose();
        }
    }
}
