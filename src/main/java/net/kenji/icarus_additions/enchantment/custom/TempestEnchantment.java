package net.kenji.icarus_additions.enchantment.custom;

import dev.cammiescorner.icarus.item.WingItem;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Tempest (I-III), for Icarus wings: press sprint while flying to do a Riptide-style spin dash, then keep flying.
 * Like Updraft, applied with books on an anvil (Icarus wings have 0 enchantability).
 */
public class TempestEnchantment extends Enchantment {

    public TempestEnchantment() {
        super(Rarity.RARE, IcarusAdditionsEnchantments.ICARUS_WINGS, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + level * 7; // same curve as vanilla Riptide
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof WingItem;
    }
}
