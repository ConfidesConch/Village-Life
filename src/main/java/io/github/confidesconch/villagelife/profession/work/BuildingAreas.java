package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.building.Building;

import net.minecraft.core.BlockPos;

/** Checks whether a position lies inside a registered building (plus one block for the walls). */
final class BuildingAreas {
    private BuildingAreas() {}

    static boolean isInside(List<Building> buildings, int x, int y, int z) {
        for (Building building : buildings) {
            BlockPos min = building.min();
            BlockPos max = building.max();
            if (x >= min.getX() - 1 && x <= max.getX() + 1 && y >= min.getY() - 1 && y <= max.getY() + 1
                    && z >= min.getZ() - 1 && z <= max.getZ() + 1) {
                return true;
            }
        }
        return false;
    }
}
