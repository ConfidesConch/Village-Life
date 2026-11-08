package io.github.confidesconch.villagelife.resident.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;


import org.junit.jupiter.api.Test;

class ScheduleClockTest {

    @Test
    void convertsMinecraftTime() {
        assertEquals(6 * 60, ScheduleClock.minuteOfDay(0));
        assertEquals(12 * 60, ScheduleClock.minuteOfDay(6000));
        assertEquals(18 * 60, ScheduleClock.minuteOfDay(12000));
        assertEquals(0, ScheduleClock.minuteOfDay(18000));
        assertEquals(6 * 60, ScheduleClock.minuteOfDay(24000 * 5L)); // later days
        assertEquals("06:30", ScheduleClock.format(ScheduleClock.minuteOfDay(500)));
    }
}
