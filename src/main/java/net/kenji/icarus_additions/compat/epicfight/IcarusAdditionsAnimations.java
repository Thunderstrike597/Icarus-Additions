package net.kenji.icarus_additions.compat.epicfight;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.gameasset.Armatures;

public class IcarusAdditionsAnimations {

    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> AIR_BREAK;
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> UPDRAFT;
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> UPDRAFT_CHARGED;

    public static void buildAnimations(AnimationManager.AnimationBuilder event){
        AIR_BREAK = event.nextAccessor("biped/air_break", animationAccessor -> new StaticAnimation(0.1F, true, animationAccessor, Armatures.BIPED));
        // Composite layer, HIGHEST priority: a one-shot that sits above the base layer, so living-motion changes
        // (charged -> airborne on the very next tick) don't replace it. No JOINT_MASK = drives every joint.
        UPDRAFT = event.nextAccessor("biped/updraft", animationAccessor -> new StaticAnimation(0.1F, false, animationAccessor, Armatures.BIPED)
                .addProperty(ClientAnimationProperties.LAYER_TYPE, Layer.LayerType.COMPOSITE_LAYER)
                .addProperty(ClientAnimationProperties.PRIORITY, Layer.Priority.HIGHEST));
        UPDRAFT_CHARGED = event.nextAccessor("biped/updraft_charged", animationAccessor -> new StaticAnimation(0.25F, false, animationAccessor, Armatures.BIPED));

    }
}
