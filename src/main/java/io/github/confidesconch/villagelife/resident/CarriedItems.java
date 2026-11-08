package io.github.confidesconch.villagelife.resident;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Reading and taking from a resident's own inventory (M9a; moved out of {@link ResidentEntity} in M21). */
final class CarriedItems {
    private CarriedItems() {}

    /** Copies of all non-empty stacks. */
    static List<ItemStack> copies(Container inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                items.add(inventory.getItem(i).copy());
            }
        }
        return items;
    }

    /** Number of items matching the filter. */
    static int count(Container inventory, Predicate<ItemStack> filter) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes up to {@code max} items matching the filter (one item type per call). */
    static ItemStack take(Container inventory, Predicate<ItemStack> filter, int max) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return inventory.removeItem(i, Math.min(max, stack.getCount()));
            }
        }
        return ItemStack.EMPTY;
    }
}
