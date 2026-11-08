package io.github.confidesconch.villagelife.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RollingStatsTest {

    @Test
    void averagesOverFilledTicksOnly() {
        RollingStats stats = new RollingStats(4);
        stats.addTick(2_000, 2);   // 2 µs, 2 residents
        stats.addTick(4_000, 2);   // 4 µs
        assertEquals(2, stats.ticks());
        assertEquals(3.0, stats.averageMicrosPerTick(), 1e-9);
        assertEquals(1.5, stats.averageMicrosPerUnit(), 1e-9);
        assertEquals(4.0, stats.maxMicrosPerTick(), 1e-9);
        assertEquals(2, stats.lastCount());
    }

    @Test
    void oldTicksDropOutOfTheWindow() {
        RollingStats stats = new RollingStats(2);
        stats.addTick(100_000, 1);
        stats.addTick(1_000, 1);
        stats.addTick(1_000, 3);
        assertEquals(1.0, stats.averageMicrosPerTick(), 1e-9);
        assertEquals(3, stats.lastCount());
    }

    @Test
    void emptyStatsAreZero() {
        RollingStats stats = new RollingStats(5);
        assertEquals(0.0, stats.averageMicrosPerTick(), 1e-9);
        assertEquals(0.0, stats.averageMicrosPerUnit(), 1e-9);
        assertEquals(0, stats.lastCount());
    }
}
