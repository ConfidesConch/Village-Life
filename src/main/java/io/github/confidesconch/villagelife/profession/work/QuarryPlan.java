package io.github.confidesconch.villagelife.profession.work;

/**
 * Shape of the miner's open pit (M13e, Minecraft-free, unit tested). Everything inside the work area
 * above the ground is cleared; below it the pit goes {@code depth} blocks down. A staircase stays
 * along the edge: going clockwise around the border from the north-west corner, the i-th border
 * column keeps its blocks up to {@code groundY - i}, so every step is one block lower than the one
 * before and the miner can always climb out. The depth is cut to the length of the border (the stairs
 * need one column per step); areas narrower than 3 blocks are only cleared down to the ground.
 */
public record QuarryPlan(int minX, int minZ, int maxX, int maxZ, int groundY, int depth) {

    public QuarryPlan {
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int border = width < 3 || length < 3 ? 0 : 2 * (width + length) - 4;
        depth = Math.max(0, Math.min(depth, border));
    }

    /** The lowest block that stays (the floor of the pit). */
    public int floorY() {
        return groundY - depth;
    }

    /** True if the block lies in the area, above the floor and is not part of the staircase. */
    public boolean shouldMine(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y > floorY() && !isStair(x, y, z);
    }

    public boolean isStair(int x, int y, int z) {
        int step = borderIndex(x, z);
        return step >= 0 && step < depth && y <= groundY - step;
    }

    /** Position on the border, clockwise from the north-west corner (0), or -1 inside the area. */
    int borderIndex(int x, int z) {
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        if (z == minZ) {
            return x - minX;
        }
        if (x == maxX) {
            return width - 1 + (z - minZ);
        }
        if (z == maxZ) {
            return width - 1 + length - 1 + (maxX - x);
        }
        if (x == minX) {
            return 2 * (width - 1) + length - 1 + (maxZ - z);
        }
        return -1;
    }

    /** How much cobblestone the miner still brings, given what the storage holds (rule 2a: up to a reserve). */
    public static int stoneToKeep(int inStorage, int reserve) {
        return Math.max(0, reserve - inStorage);
    }
}
