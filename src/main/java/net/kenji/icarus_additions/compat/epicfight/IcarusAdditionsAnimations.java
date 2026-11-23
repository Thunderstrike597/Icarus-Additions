package net.kenji.icarus_additions.compat.epicfight;

import net.kenji.icarus_additions.ConfigServer;
import net.kenji.icarus_additions.client.ClientUpdraftHandler;
import net.kenji.icarus_additions.mechanisms.updraft.UpdraftHelper;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

public class IcarusAdditionsAnimations {

    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> AIR_BREAK;
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> UPDRAFT;
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> UPDRAFT_CHARGED;
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> ZEPHYR_FORWARD;
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> ZEPHYR_DOWNWARD;

    public static void buildAnimations(AnimationManager.AnimationBuilder event){
        AIR_BREAK = event.nextAccessor("biped/air_break", animationAccessor -> new StaticAnimation(0.1F, true, animationAccessor, Armatures.BIPED));
        // Composite layer, HIGHEST priority: a one-shot that sits above the base layer, so living-motion changes
        // (charged -> airborne on the very next tick) don't replace it. No JOINT_MASK = drives every joint.
        UPDRAFT = event.nextAccessor("biped/updraft", animationAccessor -> new StaticAnimation(0.1F, false, animationAccessor, Armatures.BIPED)
                .addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER,
                        (dynamicAnimation, livingEntityPatch, speed, prevElapsedTime, elapsedTime) -> {
                            // The 0.1s blend into this animation also goes through here (as a LinkAnimation, whose length is
                            // 0 while it's being built and ~0.1s while playing). Leave it at normal speed, like Epic Fight's
                            // own AimAnimation does.
                            if (dynamicAnimation.isLinkAnimation()) {
                                return speed;
                            }
                            if (livingEntityPatch instanceof PlayerPatch<?> patch) {
                                int level = UpdraftHelper.getLevel(patch.getOriginal());
                                float lengthSeconds = dynamicAnimation.getTotalTime();
                                if (level > 0 && lengthSeconds > 0.0F) {
                                    double height = UpdraftHelper.getLiftHeight(level);
                                    double speedMult = ConfigServer.ASCENT_SPEED_MULTIPLIER.get();
                                    float launchSeconds = ClientUpdraftHandler.estimateLaunchTicks(height, speedMult) / 20.0F; // wind-up + ascent

                                    // The blend-in above plays at normal speed for its 0.1s, so the animation itself gets the rest
                                    float targetSeconds = Math.max(0.1F, launchSeconds - 0.1F);
                                    return speed * (lengthSeconds / targetSeconds);
                                }
                            }
                            return speed;
                        })
                .addProperty(ClientAnimationProperties.LAYER_TYPE, Layer.LayerType.COMPOSITE_LAYER)
                .addProperty(ClientAnimationProperties.PRIORITY, Layer.Priority.HIGHEST));
        UPDRAFT_CHARGED = event.nextAccessor("biped/updraft_charged", animationAccessor -> new StaticAnimation(0.25F, false, animationAccessor, Armatures.BIPED));
        ZEPHYR_FORWARD = event.nextAccessor("biped/zephyr_forward", animationAccessor -> new StaticAnimation(0.15F, false, animationAccessor, Armatures.BIPED));
        ZEPHYR_DOWNWARD = event.nextAccessor("biped/zephyr_downward", animationAccessor -> new StaticAnimation(0.15F, false, animationAccessor, Armatures.BIPED));

    }
}
