package net.kenji.icarus_additions.enchantment.custom;

import dev.cammiescorner.icarus.item.WingItem;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Zephyr (I-III), for Icarus wings: turns the hover into a controllable one. Press crouch mid-flight to brake into a
 * hover, then move around freely: jump rises, crouch sinks, WASD moves (vanilla air movement). Double-tap crouch or
 * land to leave the hover. Higher levels move faster.
 * Like Updraft/Tempest, applied with books on an anvil (Icarus wings have 0 enchantability).
 */
public class ZephyrEnchantment extends Enchantment {

    public ZephyrEnchantment() {
        super(Rarity.RARE, IcarusAdditionsEnchantments.ICARUS_WINGS, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof WingItem;
    }
}
