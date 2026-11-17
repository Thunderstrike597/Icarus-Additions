package net.kenji.icarus_additions.loot;

import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IcarusAdditionsTrades {
    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) return;
        // Level 5 = Master. One listing that picks Updraft, Tempest or Zephyr at random.
        event.getTrades().get(5).add((trader, random) -> {
            Enchantment ench = switch (random.nextInt(3)) {
                case 0 -> IcarusAdditionsEnchantments.UPDRAFT.get();
                case 1 -> IcarusAdditionsEnchantments.TEMPEST.get();
                default -> IcarusAdditionsEnchantments.ZEPHYR.get();
            };
            int level = 1 + random.nextInt(3);
            return new MerchantOffer(
                    new ItemStack(Items.EMERALD, 20 + level * 12 + random.nextInt(10)), // ~32–66 emeralds
                    new ItemStack(Items.BOOK),
                    EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, level)),
                    2,     // max uses before restock
                    30,    // villager XP
                    0.2F); // price multiplier
        });
    }
}