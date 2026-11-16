package net.kenji.icarus_additions.enchantment;

import dev.cammiescorner.icarus.item.WingItem;
import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.enchantment.custom.UpdraftEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class IcarusAdditionsEnchantments {
    public static final EnchantmentCategory ICARUS_WINGS =
            EnchantmentCategory.create("ICARUS_ADDITIONS_WINGS", item -> item instanceof WingItem);

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, IcarusAdditions.MOD_ID);

    public static final RegistryObject<Enchantment> UPDRAFT = ENCHANTMENTS.register("updraft", UpdraftEnchantment::new);

    private IcarusAdditionsEnchantments() {}

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}
