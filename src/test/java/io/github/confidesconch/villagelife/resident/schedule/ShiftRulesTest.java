package io.github.confidesconch.villagelife.resident.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShiftRulesTest {

    @Test
    void nightShiftWatchesAtNightAndSleepsByDay() {
        assertEquals(Activity.WORK, ShiftRules.nightShift(22 * 60, 6, 18));
        assertEquals(Activity.WORK, ShiftRules.nightShift(3 * 60, 6, 18));
        assertEquals(Activity.WORK, ShiftRules.nightShift(18 * 60, 6, 18));
        assertEquals(Activity.LEISURE, ShiftRules.nightShift(6 * 60, 6, 18)); // an hour off after the shift
        assertEquals(Activity.SLEEP, ShiftRules.nightShift(7 * 60, 6, 18));
        assertEquals(Activity.SLEEP, ShiftRules.nightShift(14 * 60 + 59, 6, 18));
        assertEquals(Activity.LEISURE, ShiftRules.nightShift(15 * 60, 6, 18));
    }

    @Test
    void defendersTakeTurns() {
        assertFalse(ShiftRules.isNightShift(0));
        assertTrue(ShiftRules.isNightShift(1));
        assertFalse(ShiftRules.isNightShift(2));
    }
}
