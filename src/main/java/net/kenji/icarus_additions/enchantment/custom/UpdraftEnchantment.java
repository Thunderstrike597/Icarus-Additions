package net.kenji.icarus_additions.enchantment.custom;

import dev.cammiescorner.icarus.item.WingItem;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Updraft (I-III), for Icarus wings: crouch still to charge, jump to launch into the air and straight into flight.
 * Icarus wings have 0 enchantability, so this is applied with books on an anvil (like Mending is for them).
 */
public class UpdraftEnchantment extends Enchantment {

    public UpdraftEnchantment() {
        // Wings usually live in a Curios slot; the slot list only matters for vanilla's equipment lookups,
        // which we don't use (the level is read straight off the equipped wings stack).
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
