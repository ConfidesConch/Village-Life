package io.github.confidesconch.villagelife.profession.work;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Repairing tools at the smithy (M14d): iron tools (and shears) with iron ingots, copper tools with
 * copper ingots, a quarter of the durability per ingot like at an anvil ({@link SmithRules}).
 * Enchantments stay. Only the kinds of tools the smith also forges.
 */
final class ToolRepair {
    private ToolRepair() {}

    static boolean isRepairable(ItemStack stack) {
        String path = path(stack);
        return stack.isDamageableItem() && SmithRules.kindOf(path).isPresent() && SmithRules.materialOf(path).isPresent()
                && SmithRules.needsRepair(stack.getDamageValue(), stack.getMaxDamage());
    }

    static String material(ItemStack stack) {
        return SmithRules.materialOf(path(stack)).orElse("iron");
    }

    static Item ingot(String material) {
        return material.equals("copper") ? Items.COPPER_INGOT : Items.IRON_INGOT;
    }

    /** A carried tool that needs repair, or null. */
    static ItemStack carried(ResidentEntity worker) {
        for (int slot = 0; slot < worker.inventory().getContainerSize(); slot++) {
            ItemStack stack = worker.inventory().getItem(slot);
            if (isRepairable(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** Repairs the tool (in the worker's inventory) with as many carried ingots as it needs, up to 4. */
    static void repair(ResidentEntity worker, ItemStack tool) {
        Item ingot = ingot(material(tool));
        int wanted = SmithRules.ingotsToRepair(tool.getDamageValue(), tool.getMaxDamage());
        int used = worker.takeCarried(stack -> stack.is(ingot), wanted).getCount();
        tool.setDamageValue(SmithRules.damageAfterRepair(tool.getDamageValue(), tool.getMaxDamage(), used));
        worker.inventory().setChanged();
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
