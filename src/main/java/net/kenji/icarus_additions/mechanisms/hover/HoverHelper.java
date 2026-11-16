package net.kenji.icarus_additions.mechanisms.hover;

import dev.cammiescorner.icarus.util.IcarusHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Side-agnostic hover helpers, used by both the client state machine and the server. */
public final class HoverHelper {
    /** Short and refreshed every tick, so it disappears on its own if anything goes wrong. */
    public static final int EFFECT_DURATION = 10;

    private HoverHelper() {}

    /** Whether the player is somewhere a hover can exist at all. */
    public static boolean canHover(Player player) {
        return !player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && !player.isPassenger()
                && !player.getAbilities().flying
                && !player.isSpectator()
                && IcarusHelper.hasWings(player)
                && canStandUpright(player);
    }

    /** Hover forces the standing pose; don't do it if that would shove the player into blocks (tight tunnels). */
    public static boolean canStandUpright(Player player) {
        return player.level().noCollision(player,
                player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7D));
    }

    /** Keeps the player upright with wings out, even though crouch is held. */
    public static void forceUpright(Player player) {
        if (player.getPose() != Pose.STANDING) {
            player.setPose(Pose.STANDING);
        }
    }

    /** Hidden Slow Falling: no particles, no HUD/inventory icon. */
    public static void applySlowFall(Player player) {
        MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
        if (current != null && !isOurs(current)) return; // a real Slow Falling potion is active, leave it alone
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, EFFECT_DURATION, 0,
                false, /* visible (particles) */ false, /* showIcon */ false));
    }

    public static void removeSlowFall(Player player) {
        MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
        if (current != null && isOurs(current)) {
            player.removeEffect(MobEffects.SLOW_FALLING);
        }
    }

    private static boolean isOurs(MobEffectInstance instance) {
        return !instance.isVisible() && !instance.showIcon() && !instance.isAmbient()
                && instance.getAmplifier() == 0 && instance.getDuration() <= EFFECT_DURATION;
    }
}
