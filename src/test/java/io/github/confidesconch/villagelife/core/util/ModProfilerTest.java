package io.github.confidesconch.villagelife.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModProfilerTest {
    @BeforeEach
    void clear() {
        ModProfiler.reset();
    }

    @Test
    void addsUpPerSystemAndTick() {
        ModProfiler.add(ModProfiler.RESIDENTS, 1_000_000, 1); // 1 ms
        ModProfiler.add(ModProfiler.RESIDENTS, 1_000_000, 1);
        ModProfiler.add(ModProfiler.JOBS, 500_000, 0);
        ModProfiler.endTick();
        assertEquals(2000.0, ModProfiler.stats().get(ModProfiler.RESIDENTS).averageMicrosPerTick(), 0.001);
        assertEquals(2, ModProfiler.stats().get(ModProfiler.RESIDENTS).lastCount());
        assertEquals(1000.0, ModProfiler.stats().get(ModProfiler.RESIDENTS).averageMicrosPerUnit(), 0.001);
        assertEquals(2500.0, ModProfiler.totalMicrosPerTick(), 0.001);
    }

    @Test
    void ticksStartFromZeroAndUnknownSystemsAreAdded() {
        ModProfiler.add("other", 4_000, 0);
        ModProfiler.endTick();
        ModProfiler.endTick(); // nothing in the second tick
        assertEquals(2.0, ModProfiler.stats().get("other").averageMicrosPerTick(), 0.001);
    }

    @Test
    void reportOrderStartsWithTheResidents() {
        assertEquals(ModProfiler.RESIDENTS, List.copyOf(ModProfiler.stats().keySet()).getFirst());
        long[] ran = new long[1];
        ModProfiler.time(ModProfiler.BOOK, () -> ran[0]++);
        ModProfiler.endTick();
        assertEquals(1, ran[0]);
        assertTrue(ModProfiler.stats().get(ModProfiler.BOOK).averageMicrosPerTick() >= 0);
    }

    @Test
    void detailsAreShownButNotAddedToTheTotal() {
        ModProfiler.add(ModProfiler.RESIDENTS, 3_000_000, 1);
        ModProfiler.addDetail(ModProfiler.PATHS, 1_000_000, 2);
        ModProfiler.endTick();
        assertEquals(3000.0, ModProfiler.totalMicrosPerTick(), 0.001);
        assertEquals(1000.0, ModProfiler.details().get(ModProfiler.PATHS).averageMicrosPerTick(), 0.001);
        assertEquals(2.0, ModProfiler.details().get(ModProfiler.PATHS).averageUnitsPerTick(), 0.001);
    }
}
