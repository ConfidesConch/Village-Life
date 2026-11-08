package io.github.confidesconch.villagelife.market;

/**
 * Supply and demand at the village market (M15a, owner's choice "3a"; Minecraft-free, unit tested):
 * the base price of a good rises when the village has little of it and falls when it has plenty.
 * The village sells only what lies above its target stock and buys only up to it.
 */
public final class MarketPricing {
    private MarketPricing() {}

    /** Price factor by stock compared to the target: scarce 1.5 / short 1.2 / normal 1.0 / plenty 0.7. */
    public static double factor(int stock, int target) {
        if (target <= 0) {
            return 1.0;
        }
        double ratio = (double) stock / target;
        if (ratio < 0.5) {
            return 1.5;
        }
        if (ratio < 1.0) {
            return 1.2;
        }
        return ratio > 2.0 ? 0.7 : 1.0;
    }

    /** Emeralds for one bundle right now, at least 1. */
    public static int price(int baseEmeralds, int stock, int target) {
        return Math.max(1, (int) Math.round(baseEmeralds * factor(stock, target)));
    }

    /** Whole bundles the village can sell (its stock above the target). */
    public static int bundlesForSale(int stock, int target, int bundleSize) {
        return Math.max(0, (stock - target) / Math.max(1, bundleSize));
    }

    /** Bundles the village still wants to buy to reach its target (a started bundle counts). */
    public static int bundlesWanted(int stock, int target, int bundleSize) {
        int missing = target - stock;
        return missing <= 0 ? 0 : (missing + bundleSize - 1) / Math.max(1, bundleSize);
    }
}
