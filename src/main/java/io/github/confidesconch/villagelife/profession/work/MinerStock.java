package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the miner keeps (M13e rule 2a): junk he leaves, and the cobblestone already in storage (split off MinerGoal, M28d). */
final class MinerStock {
    private static final int STONE_COUNT_INTERVAL_TICKS = 600;

    private int stoneInStorage;
    private long stoneCountedGameTime = -STONE_COUNT_INTERVAL_TICKS;

    static boolean isJunk(ItemStack stack) {
        return stack.is(ItemTags.DIRT) || stack.is(ItemTags.SAND) || stack.is(Items.GRAVEL)
                || stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE)
                || stack.is(Items.TUFF) || stack.is(Items.CALCITE) || stack.is(Items.NETHERRACK);
    }

    /** Cobblestone (and the like) in the village's storage buildings, counted every 30 seconds. */
    int stoneInStorage(ServerLevel level, Village village) {
        if (level.getGameTime() - stoneCountedGameTime < STONE_COUNT_INTERVAL_TICKS) {
            return stoneInStorage;
        }
        stoneCountedGameTime = level.getGameTime();
        int count = 0;
        for (Building building : village == null ? List.<Building>of() : village.buildings()) {
            if (building.type() != BuildingType.STORAGE || !building.isValid()) {
                continue;
            }
            for (BlockPos pos : building.containers()) {
                Container container = StorageAccess.containerAt(level, pos);
                for (int slot = 0; container != null && slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    count += stack.is(ItemTags.STONE_CRAFTING_MATERIALS) ? stack.getCount() : 0;
                }
            }
        }
        stoneInStorage = count;
        return count;
    }
}
