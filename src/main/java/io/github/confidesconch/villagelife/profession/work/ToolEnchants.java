package io.github.confidesconch.villagelife.profession.work;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Reads enchantment levels of a tool without needing the registries (M26). */
public final class ToolEnchants {
    private ToolEnchants() {}

    /** Level of that enchantment on the stack, 0 if it has none. */
    public static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getTagEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
