package net.kenji.icarus_additions.mechanisms.updraft;

import dev.cammiescorner.icarus.item.WingItem;
import dev.cammiescorner.icarus.util.IcarusHelper;
import net.kenji.icarus_additions.ConfigServer;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class UpdraftHelper {
    private UpdraftHelper() {}

    /** Updraft level on the equipped Icarus wings (Curios or chest, whatever Icarus uses), 0 if none/unusable. */
    public static int getLevel(Player player) {
        if (!IcarusHelper.hasWings(player)) return 0;
        ItemStack wings = IcarusHelper.getEquippedWings(player);
        if (wings.isEmpty() || !(wings.getItem() instanceof WingItem wingItem) || !wingItem.isUsable(player, wings)) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(IcarusAdditionsEnchantments.UPDRAFT.get(), wings);
    }

    /** Blocks lifted for this level: base height x multiplier^(level - 1). */
    public static double getLiftHeight(int level) {
        if (level <= 0) return 0.0D;
        return ConfigServer.BASE_LIFT_HEIGHT.get() * Math.pow(ConfigServer.LIFT_LEVEL_MULTIPLIER.get(), level - 1);
    }

    public static double getChargeTimeFromLevel(int level){
        if (level <= 0) return -1;
        return ConfigServer.BASE_CHARGE_SECONDS.get() / Math.pow(ConfigServer.CHARGE_SECONDS_LEVEL_MULTIPLIER.get(), level - 1);
    }

    /**
     * Same checks Caelus does before starting wing flight (Icarus' wings fly through Caelus, which is what grants the
     * permission while wings are equipped). Used at the top of an Updraft launch on both sides.
     */
    public static boolean canStartWingFlight(Player player) {
        return !player.onGround()
                && !player.isFallFlying()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.LEVITATION)
                && IcarusHelper.hasWings(player);
    }

    /** Situations where charging/launching makes no sense. */
    public static boolean canUse(Player player) {
        return !player.isPassenger()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isInWater()
                && !player.isInLava()
                && !player.isFallFlying();
    }
}
