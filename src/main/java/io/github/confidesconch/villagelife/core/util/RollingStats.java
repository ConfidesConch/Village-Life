package io.github.confidesconch.villagelife.core.util;

/**
 * Rolling statistics over the last N server ticks (Minecraft-free, unit tested).
 * Each tick stores the total time spent and how many units (e.g. residents) were processed.
 */
public final class RollingStats {
    private final long[] nanos;
    private final int[] counts;
    private int next;
    private int filled;

    public RollingStats(int ticks) {
        nanos = new long[ticks];
        counts = new int[ticks];
    }

    public void addTick(long totalNanos, int count) {
        nanos[next] = totalNanos;
        counts[next] = count;
        next = (next + 1) % nanos.length;
        filled = Math.min(filled + 1, nanos.length);
    }

    public int ticks() {
        return filled;
    }

    /** Average time per tick, in microseconds. */
    public double averageMicrosPerTick() {
        if (filled == 0) return 0;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += nanos[i];
        return sum / 1000.0 / filled;
    }

    /** Highest time of a single tick, in microseconds. */
    public double maxMicrosPerTick() {
        long max = 0;
        for (int i = 0; i < filled; i++) max = Math.max(max, nanos[i]);
        return max / 1000.0;
    }

    /** Average time per unit (resident), in microseconds. */
    public double averageMicrosPerUnit() {
        long sum = 0;
        long units = 0;
        for (int i = 0; i < filled; i++) {
            sum += nanos[i];
            units += counts[i];
        }
        return units == 0 ? 0 : sum / 1000.0 / units;
    }

    /** Average units (e.g. path searches) per tick. */
    public double averageUnitsPerTick() {
        if (filled == 0) return 0;
        long units = 0;
        for (int i = 0; i < filled; i++) units += counts[i];
        return (double) units / filled;
    }

    /** Units processed in the most recent tick. */
    public int lastCount() {
        return filled == 0 ? 0 : counts[Math.floorMod(next - 1, counts.length)];
    }
}
