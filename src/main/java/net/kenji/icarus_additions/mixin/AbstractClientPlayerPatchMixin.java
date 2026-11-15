package net.kenji.icarus_additions.mixin;

import net.kenji.icarus_additions.client.ClientHoverHandler;
import net.kenji.icarus_additions.compat.epicfight.IcarusAdditionsLivingMotions;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;

@Mixin(value = AbstractClientPlayerPatch.class, remap = false)
public class AbstractClientPlayerPatchMixin {


    @Inject(method = "updateMotion", at = @At("TAIL"),cancellable = true)
    public void replaceUpdateMotion(boolean considerInaction, CallbackInfo ci){
        AbstractClientPlayerPatch self = (AbstractClientPlayerPatch)(Object)this;
        if(self.getOriginal() instanceof Player player) {
            boolean isBreaking = ClientHoverHandler.isBreaking(player);
            boolean isHovering = ClientHoverHandler.isHovering(player);
            LivingMotion breakingMotion = IcarusAdditionsLivingMotions.AIR_BREAK;

            LivingMotion hoveringMotion = LivingMotions.CREATIVE_IDLE;


            if(!isHovering && !isBreaking)return;


            self.currentLivingMotion = isBreaking ? breakingMotion : hoveringMotion;



            UpdatePlayerMotionEvent.BaseLayer baseLayerEvent = new UpdatePlayerMotionEvent.BaseLayer(self, self.currentLivingMotion, !self.getEntityState().updateLivingMotion() && considerInaction);
            self.getEventListener().triggerEvents(PlayerEventListener.EventType.UPDATE_BASE_LIVING_MOTION_EVENT, baseLayerEvent);
            MinecraftForge.EVENT_BUS.post(baseLayerEvent);
            self.currentLivingMotion = baseLayerEvent.getMotion();
            if (!self.getEntityState().updateLivingMotion() && considerInaction) {
                self.currentCompositeMotion = LivingMotions.NONE;
            } else {
                CapabilityItem mainhandItemCap = self.getHoldingItemCapability(InteractionHand.MAIN_HAND);
                CapabilityItem offhandItemCap = self.getHoldingItemCapability(InteractionHand.OFF_HAND);
                LivingMotion customLivingMotion = mainhandItemCap.getLivingMotion(self, InteractionHand.MAIN_HAND);
                if (customLivingMotion == null) {
                    customLivingMotion = offhandItemCap.getLivingMotion(self, InteractionHand.OFF_HAND);
                }

                if (customLivingMotion != null) {
                    self.currentCompositeMotion = customLivingMotion;
                } else if (((AbstractClientPlayer)self.getOriginal()).isUsingItem()) {
                    UseAnim useAnim = ((AbstractClientPlayer)self.getOriginal()).getUseItem().getUseAnimation();
                    if (useAnim == UseAnim.BLOCK) {
                        self.currentCompositeMotion = LivingMotions.BLOCK_SHIELD;
                    } else if (useAnim == UseAnim.CROSSBOW) {
                        self.currentCompositeMotion = LivingMotions.RELOAD;
                    } else if (useAnim == UseAnim.DRINK) {
                        self.currentCompositeMotion = LivingMotions.DRINK;
                    } else if (useAnim == UseAnim.EAT) {
                        self.currentCompositeMotion = LivingMotions.EAT;
                    } else if (useAnim == UseAnim.SPYGLASS) {
                        self.currentCompositeMotion = LivingMotions.SPECTATE;
                    } else {
                        self.currentCompositeMotion = self.currentLivingMotion;
                    }
                } else if (((StaticAnimation)self.getClientAnimator().getCompositeLayer(Layer.Priority.MIDDLE).animationPlayer.getRealAnimation().get()).isReboundAnimation()) {
                    self.currentCompositeMotion = LivingMotions.SHOT;
                } else if (((AbstractClientPlayer)self.getOriginal()).swinging && ((AbstractClientPlayer)self.getOriginal()).getSleepingPos().isEmpty()) {
                    self.currentCompositeMotion = LivingMotions.DIGGING;
                } else {
                    self.currentCompositeMotion = self.currentLivingMotion;
                }

                UpdatePlayerMotionEvent.CompositeLayer compositeLayerEvent = new UpdatePlayerMotionEvent.CompositeLayer(self, self.currentCompositeMotion);
                self.getEventListener().triggerEvents(PlayerEventListener.EventType.UPDATE_COMPOSITE_LIVING_MOTION_EVENT, compositeLayerEvent);
                MinecraftForge.EVENT_BUS.post(compositeLayerEvent);
                self.currentCompositeMotion = compositeLayerEvent.getMotion();
            }

        }
    }
}
