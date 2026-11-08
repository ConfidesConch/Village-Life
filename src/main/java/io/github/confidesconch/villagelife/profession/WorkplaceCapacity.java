package io.github.confidesconch.villagelife.profession;

/**
 * How many residents can work in one workplace building (M20b2, owner's choice "1a": by size, so that
 * nothing extra has to be placed): up to 25 floor blocks one, then one more per further 25 blocks, at
 * most four. Minecraft-free (unit tested).
 */
public final class WorkplaceCapacity {
    private WorkplaceCapacity() {}

    public static final int FLOOR_PER_WORKER = 25;
    public static final int MAX_WORKERS = 4;

    /** Workers for a room of {@code interiorVolume} air blocks that is {@code height} blocks high. */
    public static int workers(int interiorVolume, int height) {
        int floor = interiorVolume / Math.max(1, height);
        return Math.max(1, Math.min(MAX_WORKERS, 1 + (floor - 1) / FLOOR_PER_WORKER));
    }
}
