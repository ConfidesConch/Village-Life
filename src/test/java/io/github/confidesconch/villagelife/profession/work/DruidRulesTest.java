package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DruidRulesTest {

    @Test
    void discIsRoundAndNearestFirst() {
        int[] offsets = DruidRules.discOffsets(10);
        int columns = offsets.length / 2;
        assertTrue(columns > 300 && columns < 330, "about pi * 100 columns, got " + columns);
        assertEquals(0, offsets[0]);
        assertEquals(0, offsets[1]); // the center first
        int last = 0;
        for (int i = 0; i < columns; i++) {
            int distance = offsets[i * 2] * offsets[i * 2] + offsets[i * 2 + 1] * offsets[i * 2 + 1];
            assertTrue(distance <= 100, "inside the radius");
            assertTrue(distance >= last, "sorted by distance");
            last = distance;
        }
    }

    @Test
    void plantsGrowOneStepUntilRipe() {
        assertEquals(4, DruidRules.nextAge(3, 7));
        assertEquals(7, DruidRules.nextAge(6, 7));
        assertEquals(-1, DruidRules.nextAge(7, 7));
    }
}
