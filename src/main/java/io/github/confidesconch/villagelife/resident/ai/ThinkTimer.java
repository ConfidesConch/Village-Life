package io.github.confidesconch.villagelife.resident.ai;

/**
 * Decides when a resident re-plans its activity (Minecraft-free, unit tested).
 * Each resident thinks once per {@code interval} ticks, spread out by its entity id, so that
 * 100 residents do not all think in the same tick.
 */
public final class ThinkTimer {
    private ThinkTimer() {}

    /** Large prime to spread consecutive entity ids over the interval. */
    private static final long SPREAD = 7919L;

    public static boolean shouldThink(long gameTime, int entityId, int interval) {
        if (interval <= 1) {
            return true;
        }
        return Math.floorMod(gameTime + entityId * SPREAD, interval) == 0;
    }
}
