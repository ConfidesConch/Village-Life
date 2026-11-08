package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuarryPlanTest {

    /** 10 × 10 area at 0..9, ground at y = 64, 16 deep. */
    private final QuarryPlan plan = new QuarryPlan(0, 0, 9, 9, 64, 16);

    @Test
    void depthIsLimitedByTheBorderLength() {
        assertEquals(16, plan.depth());
        assertEquals(12, new QuarryPlan(0, 0, 3, 3, 64, 16).depth()); // 4 × 4: 12 border columns
        assertEquals(0, new QuarryPlan(0, 0, 1, 9, 64, 16).depth()); // too narrow for stairs
    }

    @Test
    void borderIsNumberedClockwise() {
        assertEquals(0, plan.borderIndex(0, 0));
        assertEquals(9, plan.borderIndex(9, 0));
        assertEquals(18, plan.borderIndex(9, 9));
        assertEquals(27, plan.borderIndex(0, 9));
        assertEquals(35, plan.borderIndex(0, 1));
        assertEquals(-1, plan.borderIndex(5, 5));
    }

    @Test
    void everyStepIsOneBlockLower() {
        for (int step = 0; step < 9; step++) {
            assertTrue(plan.isStair(step, 64 - step, 0));
            assertFalse(plan.isStair(step, 65 - step, 0));
        }
    }

    @Test
    void minesAboveGroundEverywhereAndBelowExceptStairs() {
        assertTrue(plan.shouldMine(0, 70, 0)); // hill above the entrance
        assertFalse(plan.shouldMine(0, 64, 0)); // entrance step
        assertTrue(plan.shouldMine(5, 49, 5)); // lowest layer inside
        assertFalse(plan.shouldMine(5, 48, 5)); // floor
        assertFalse(plan.shouldMine(10, 60, 5)); // outside
        assertTrue(plan.shouldMine(9, 50, 9)); // border column 18 is past the last step (15)
    }

    @Test
    void stoneOnlyUpToTheReserve() {
        assertEquals(256, QuarryPlan.stoneToKeep(0, 256));
        assertEquals(6, QuarryPlan.stoneToKeep(250, 256));
        assertEquals(0, QuarryPlan.stoneToKeep(300, 256));
    }
}
