package io.github.confidesconch.villagelife.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Where residents put things and get things (M9a): the containers of their own workplace first (if
 * any), then the containers of the village's valid storage buildings, nearest first. Never loads chunks.
 */
public final class StorageAccess {
    private StorageAccess() {}

    /** Container positions a resident may use, in the order it should try them ({@code workplace} may be null). */
    public static List<BlockPos> containers(Village village, Building workplace, BlockPos from) {
        List<BlockPos> result = new ArrayList<>(workplace == null ? List.of() : workplace.containers());
        if (village != null) {
            List<BlockPos> storage = new ArrayList<>();
            for (Building building : village.buildings()) {
                if (building.type() == BuildingType.STORAGE && building.isValid()) {
                    storage.addAll(building.containers());
                }
            }
            storage.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
            result.addAll(storage);
        }
        return result;
    }

    /** The container at this position, or null if there is none or it is not loaded. */
    public static Container containerAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? container : null;
    }

    /** True if at least one item of the stack fits into the container. */
    public static boolean canAccept(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() ? container.canPlaceItem(slot, stack)
                    : ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), container.getMaxStackSize(existing))) {
                return true;
            }
        }
        return false;
    }

    /** Puts as much of the stack as possible into the container; returns the rest. */
    public static ItemStack insert(Container container, ItemStack stack) {
        return HopperBlockEntity.addItem(null, container, stack, null);
    }

    public static boolean contains(Container container, Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && wanted.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Takes up to {@code max} matching items (possibly from several slots) out of the container. */
    public static List<ItemStack> take(Container container, Predicate<ItemStack> wanted, int max) {
        List<ItemStack> taken = new ArrayList<>();
        int left = max;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && wanted.test(stack)) {
                ItemStack part = container.removeItem(slot, Math.min(left, stack.getCount()));
                left -= part.getCount();
                taken.add(part);
            }
        }
        if (!taken.isEmpty()) {
            container.setChanged();
        }
        return taken;
    }
}
