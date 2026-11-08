package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

/**
 * Rules of the storekeeper (M14c, Minecraft-free, unit tested): which workplace to empty first and how
 * much of a needed material to bring to a workplace.
 */
public final class CourierPlanner {
    private CourierPlanner() {}

    /** A workplace is emptied once this many products lie in its containers. */
    public static final int MIN_PRODUCTS = 8;
    /** A workplace gets more of a material once it has fewer than this … */
    public static final int SUPPLY_BELOW = 8;
    /** … and is filled up to this. */
    public static final int SUPPLY_UP_TO = 16;

    /** Index of the workplace with the most products (at least {@link #MIN_PRODUCTS}), or -1. */
    public static int fullest(List<Integer> productCounts) {
        int best = -1;
        for (int i = 0; i < productCounts.size(); i++) {
            int count = productCounts.get(i);
            if (count >= MIN_PRODUCTS && (best < 0 || count > productCounts.get(best))) {
                best = i;
            }
        }
        return best;
    }

    /** How many of a material to bring when the workplace holds {@code present}; 0 if it has enough. */
    public static int supplyAmount(int present) {
        return present < SUPPLY_BELOW ? SUPPLY_UP_TO - present : 0;
    }
}
