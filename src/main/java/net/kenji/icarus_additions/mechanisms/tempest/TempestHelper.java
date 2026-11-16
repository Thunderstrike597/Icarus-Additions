package net.kenji.icarus_additions.mechanisms.tempest;

import dev.cammiescorner.icarus.item.WingItem;
import dev.cammiescorner.icarus.util.IcarusHelper;
import net.kenji.icarus_additions.ConfigServer;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public final class TempestHelper {
    /** Same as vanilla Riptide (TridentItem.releaseUsing). */
    public static final int SPIN_TICKS = 10;

    private TempestHelper() {}

    /** Tempest level on the equipped Icarus wings, 0 if none/unusable. */
    public static int getLevel(Player player) {
        if (!IcarusHelper.hasWings(player)) return 0;
        ItemStack wings = IcarusHelper.getEquippedWings(player);
        if (wings == null || wings.isEmpty() || !(wings.getItem() instanceof WingItem wingItem) || !wingItem.isUsable(player, wings)) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(IcarusAdditionsEnchantments.TEMPEST.get(), wings);
    }

    public static double getCooldown(int level){
        if (level <= 0) return -1;
        return ConfigServer.BASE_TEMPEST_COOLDOWN_SECONDS.get() / Math.pow(ConfigServer.TEMPEST_COOLDOWN_MULTIPLIER.get(), level - 1);
    }

    /** The equipped wings stack, for the item cooldown (shows the cooldown sweep on the wings in the inventory). */
    public static ItemStack getWings(Player player) {
        ItemStack wings = IcarusHelper.getEquippedWings(player);
        return wings == null ? ItemStack.EMPTY : wings;
    }

    public static boolean isOnCooldown(Player player) {
        ItemStack wings = getWings(player);
        return !wings.isEmpty() && player.getCooldowns().isOnCooldown(wings.getItem());
    }

    public static void startCooldown(Player player) {
        ItemStack wings = getWings(player);
        int level = getLevel(player);
        int ticks = (int) Math.round(getCooldown(level) * 20.0D);
        if (!wings.isEmpty() && ticks > 0) {
            player.getCooldowns().addCooldown(wings.getItem(), ticks);
        }
    }

    /** Can a dash start right now: flying with wings that have Tempest, not already spinning, not on cooldown. */
    public static boolean canDash(Player player) {
        return ConfigServer.TEMPEST_ENABLED.get()
                && player.isFallFlying()
                && !player.isAutoSpinAttack()
                && getLevel(player) > 0
                && !isOnCooldown(player);
    }

    /**
     * The Riptide push, exactly like TridentItem.releaseUsing: 3 * (1 + level) / 4 blocks/tick along the look direction
     * (I = 1.5, II = 2.25, III = 3.0), times the config multiplier.
     */
    public static Vec3 getDashPush(Player player, int level) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float x = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        float y = -Mth.sin(pitch * Mth.DEG_TO_RAD);
        float z = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        float length = Mth.sqrt(x * x + y * y + z * z);
        double strength = 3.0D * ((1.0D + level) / 4.0D) * ConfigServer.TEMPEST_SPEED_MULTIPLIER.get();
        return new Vec3(x, y, z).scale(strength / length);
    }

    public static SoundEvent getSound(int level) {
        if (level >= 3) return SoundEvents.TRIDENT_RIPTIDE_3;
        if (level == 2) return SoundEvents.TRIDENT_RIPTIDE_2;
        return SoundEvents.TRIDENT_RIPTIDE_1;
    }
}
