package io.github.confidesconch.villagelife.resident.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;


import org.junit.jupiter.api.Test;

class DailyScheduleTest {
    private static final DailySchedule PLAN = DailySchedule.DEFAULT;

    @Test
    void defaultPlanWorksByDayHasFreeEveningsAndSleepsAtNight() {
        assertEquals(Activity.SLEEP, PLAN.activityAt(3 * 60));
        assertEquals(Activity.SLEEP, PLAN.activityAt(5 * 60 + 59));
        assertEquals(Activity.WORK, PLAN.activityAt(6 * 60));
        assertEquals(Activity.WORK, PLAN.activityAt(12 * 60));
        assertEquals(Activity.WORK, PLAN.activityAt(17 * 60 + 59));
        assertEquals(Activity.LEISURE, PLAN.activityAt(18 * 60)); // M19b: free evening
        assertEquals(Activity.LEISURE, PLAN.activityAt(20 * 60 + 59));
        assertEquals(Activity.SLEEP, PLAN.activityAt(21 * 60));
        assertEquals(Activity.SLEEP, PLAN.activityAt(23 * 60 + 59));
    }

    @Test
    void villageHoursKeepTheFreeEvening() {
        DailySchedule plan = DailySchedule.workAndSleep(7, 16);
        assertEquals(Activity.WORK, plan.activityAt(15 * 60));
        assertEquals(Activity.LEISURE, plan.activityAt(16 * 60));
        assertEquals(Activity.SLEEP, plan.activityAt(21 * 60));
        assertEquals(Activity.SLEEP, plan.activityAt(6 * 60 + 59));
    }

    @Test
    void lastEntryContinuesAfterMidnight() {
        DailySchedule plan = new DailySchedule(List.of(
                new DailySchedule.Entry(22, Activity.SLEEP), new DailySchedule.Entry(7, Activity.WORK)));
        assertEquals(Activity.SLEEP, plan.activityAt(0));
        assertEquals(Activity.WORK, plan.activityAt(7 * 60));
        assertEquals(Activity.SLEEP, plan.activityAt(-1)); // wraps to 23:59
    }

    @Test
    void readsJson() {
        String json = "{\"entries\":[{\"from\":9,\"activity\":\"work\"},{\"from\":18,\"activity\":\"sleep\"}]}";
        DailySchedule plan = DailySchedule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(Activity.WORK, plan.activityAt(10 * 60));
        assertEquals(Activity.SLEEP, plan.activityAt(2 * 60));
    }

    @Test
    void rejectsBrokenJson() {
        for (String json : List.of("{\"entries\":[]}",
                "{\"entries\":[{\"from\":9,\"activity\":\"work\"},{\"from\":9,\"activity\":\"sleep\"}]}",
                "{\"entries\":[{\"from\":25,\"activity\":\"work\"}]}",
                "{\"entries\":[{\"from\":5,\"activity\":\"dance\"}]}")) {
            assertTrue(DailySchedule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError(), json);
        }
        assertThrows(IllegalArgumentException.class, () -> new DailySchedule(List.of()));
    }
}
