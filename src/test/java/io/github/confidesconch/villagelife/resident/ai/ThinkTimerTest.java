package io.github.confidesconch.villagelife.resident.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThinkTimerTest {

    @Test
    void eachResidentThinksExactlyOncePerInterval() {
        for (int id = 0; id < 50; id++) {
            int thoughts = 0;
            for (long tick = 1000; tick < 1020; tick++) {
                if (ThinkTimer.shouldThink(tick, id, 20)) thoughts++;
            }
            assertEquals(1, thoughts, "resident " + id);
        }
    }

    @Test
    void hundredResidentsAreSpreadOverTheInterval() {
        int[] perTick = new int[20];
        for (int id = 0; id < 100; id++) {
            for (int t = 0; t < 20; t++) {
                if (ThinkTimer.shouldThink(t, id, 20)) perTick[t]++;
            }
        }
        for (int count : perTick) {
            // perfectly even would be 5 per tick; no tick may be overloaded
            assertTrue(count <= 10, "too many residents think in the same tick: " + count);
        }
    }

    @Test
    void intervalOfOneMeansEveryTick() {
        assertTrue(ThinkTimer.shouldThink(123, 7, 1));
    }
}
