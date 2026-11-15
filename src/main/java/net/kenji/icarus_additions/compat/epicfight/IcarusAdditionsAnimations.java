package net.kenji.icarus_additions.compat.epicfight;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

public class IcarusAdditionsAnimations {

    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> AIR_BREAK;

    public static void buildAnimations(AnimationManager.AnimationBuilder event){
        AIR_BREAK = event.nextAccessor("biped/air_break", animationAccessor -> new StaticAnimation(0.1F, true, animationAccessor, Armatures.BIPED));
    }
}
