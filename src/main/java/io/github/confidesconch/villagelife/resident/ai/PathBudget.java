package io.github.confidesconch.villagelife.resident.ai;

/**
 * Limits how many new path searches all residents together may start per server tick (M12c).
 * Measured in 0.0.28: at sunrise up to 30 searches landed in one tick (55 ms). Searches over the
 * limit are refused for this tick; the goals simply try again a moment later. Free of Minecraft
 * classes (unit tested).
 */
public final class PathBudget {
    /** Safety net in addition to the count: stop for this tick once searches took this long. */
    public static final long MAX_NANOS_PER_TICK = 1_000_000; // M21: 1 ms (3 ms caused lag spikes at shift changes)

    private long tick = Long.MIN_VALUE;
    private int searches;
    private long nanos;

    /** True if another search may start in tick {@code gameTime}. */
    public boolean tryStart(long gameTime, int maxSearches) {
        if (gameTime != tick) {
            tick = gameTime;
            searches = 0;
            nanos = 0;
        }
        if (searches >= maxSearches || nanos >= MAX_NANOS_PER_TICK) {
            return false;
        }
        searches++;
        return true;
    }

    /** Records how long the search that was allowed took. */
    public void finished(long searchNanos) {
        nanos += searchNanos;
    }
}
