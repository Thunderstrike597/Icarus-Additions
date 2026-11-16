package net.kenji.icarus_additions.loot;

import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IcarusAdditionsLoot {
    private IcarusAdditionsLoot() {}

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();

        if (id.equals(BuiltInLootTables.END_CITY_TREASURE)) {
            event.getTable().addPool(bookPool("updraft_end_city", IcarusAdditionsEnchantments.UPDRAFT.get(), 0.12F, 1, 3).build());
            event.getTable().addPool(bookPool("tempest_end_city", IcarusAdditionsEnchantments.TEMPEST.get(), 0.12F, 1, 3).build());
        }

        if (id.equals(BuiltInLootTables.ANCIENT_CITY)) {
            event.getTable().addPool(bookPool("updraft_ancient_city", IcarusAdditionsEnchantments.UPDRAFT.get(), 0.05F, 1, 3).build());
            event.getTable().addPool(bookPool("tempest_ancient_city", IcarusAdditionsEnchantments.TEMPEST.get(), 0.08F, 1, 2).build());
        }
        if (id.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
            event.getTable().addPool(bookPool("updraft_stronghold_library", IcarusAdditionsEnchantments.UPDRAFT.get(), 0.05F, 1, 2).build());
            event.getTable().addPool(bookPool("tempest_stronghold_library", IcarusAdditionsEnchantments.TEMPEST.get(), 0.06F, 1, 2).build());
        }
        if (id.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)) {
            event.getTable().addPool(bookPool("tempest_stronghold_corridor", IcarusAdditionsEnchantments.TEMPEST.get(), 0.09F, 1, 2).build());
        }
        if (id.equals(BuiltInLootTables.STRONGHOLD_CROSSING)) {
            event.getTable().addPool(bookPool("updraft_stronghold_crossing", IcarusAdditionsEnchantments.UPDRAFT.get(), 0.085F, 1, 2).build());
        }
        if (id.equals(BuiltInLootTables.BASTION_TREASURE)) {
            event.getTable().addPool(bookPool("updraft_bastion_treasure", IcarusAdditionsEnchantments.UPDRAFT.get(), 0.06F, 1, 2).build());
        }
        // Mob drops work the same way: an entity's loot table id comes from the EntityType
        if (id.equals(EntityType.PHANTOM.getDefaultLootTable())) {
            event.getTable().addPool(bookPool("tempest_phantom", IcarusAdditionsEnchantments.TEMPEST.get(), 0.018F, 1, 2)
                    .when(LootItemKilledByPlayerCondition.killedByPlayer()) // no farming with traps
                    .build());
        }
    }

    /** One roll: a book with the given enchantment at a random level in [minLevel, maxLevel], with `chance` to appear at all. */
    private static LootPool.Builder bookPool(String name, Enchantment enchantment, float chance, int minLevel, int maxLevel) {
        return LootPool.lootPool()
                .name(IcarusAdditions.MOD_ID + ":" + name) // Forge requires unique pool names per table
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(Items.BOOK)
                        .apply(new SetEnchantmentsFunction.Builder()
                                .withEnchantment(enchantment, UniformGenerator.between(minLevel, maxLevel))));
    }
}