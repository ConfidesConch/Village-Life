package io.github.confidesconch.villagelife.village;

/**
 * Horizontal, circular area of a village (Minecraft-free, unit tested).
 * Villages reach from bedrock to build limit; only X/Z are considered.
 */
public record VillageArea(int centerX, int centerZ, int radius) {

    public VillageArea {
        if (radius <= 0) {
            throw new IllegalArgumentException("Village radius must be positive: " + radius);
        }
    }

    /** True if the block column at x/z lies inside (or on the border of) this area. */
    public boolean contains(int x, int z) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /** True if both areas share at least one block column. Touching borders count as overlap. */
    public boolean overlaps(VillageArea other) {
        long dx = (long) other.centerX - centerX;
        long dz = (long) other.centerZ - centerZ;
        long reach = (long) radius + other.radius;
        return dx * dx + dz * dz <= reach * reach;
    }
}
