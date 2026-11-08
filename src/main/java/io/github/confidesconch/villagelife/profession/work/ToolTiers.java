package io.github.confidesconch.villagelife.profession.work;

/**
 * How much faster a better tool makes a worker (M9b, Minecraft-free, unit tested).
 * The material is read from the item name (e.g. {@code iron_axe}). The wooden basic tool every worker
 * always has counts as 1.0; unknown materials (e.g. from other mods) count as stone.
 */
public final class ToolTiers {
    private ToolTiers() {}

    public static final double BASIC = 1.0;

    public static double speed(String itemPath) {
        String path = itemPath.toLowerCase(java.util.Locale.ROOT);
        if (path.startsWith("wooden_")) {
            return BASIC;
        }
        if (path.startsWith("stone_")) {
            return 1.25;
        }
        if (path.startsWith("copper_") || path.startsWith("iron_") || path.startsWith("golden_")) {
            return 1.5;
        }
        if (path.startsWith("diamond_")) {
            return 1.75;
        }
        if (path.startsWith("netherite_")) {
            return 2.0;
        }
        return 1.25;
    }

    /** Ticks a work step takes with a tool of this speed (never less than 1). */
    public static int ticks(int basicTicks, double speed) {
        return Math.max(1, (int) Math.round(basicTicks / speed));
    }

    /**
     * True if less than a fifth of the tool's durability is left (M14d): workers then stop using it,
     * bring it back to storage and do not fetch it again, so the smith can repair it.
     */
    public static boolean isWorn(int damage, int maxDamage) {
        return maxDamage > 0 && (maxDamage - damage) * 5 < maxDamage;
    }
}
