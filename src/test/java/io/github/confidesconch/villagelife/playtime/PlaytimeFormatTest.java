package io.github.confidesconch.villagelife.playtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.playtime.PlaytimeFormat.Entry;

class PlaytimeFormatTest {

    @Test
    void ticksToHoursMinutesSeconds() {
        assertEquals(3723, PlaytimeFormat.seconds(3723 * 20 + 19)); // started seconds are not counted
        assertEquals(0, PlaytimeFormat.seconds(-5));
        long[] parts = PlaytimeFormat.split(3723);
        assertEquals(1, parts[0]);
        assertEquals(2, parts[1]);
        assertEquals(3, parts[2]);
        assertEquals(100, PlaytimeFormat.split(360000)[0]); // more than a day stays in hours
    }

    @Test
    void topTenMostFirst() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            entries.add(new Entry("p" + i, i * 100L));
        }
        entries.add(new Entry("a", 1100));
        List<Entry> top = PlaytimeFormat.top(entries);
        assertEquals(10, top.size());
        assertEquals("a", top.get(0).name()); // same time as p11 (1100 s): names break the tie
        assertEquals("p11", top.get(1).name());
        assertEquals("p3", top.get(9).name()); // p0–p2 are left out
    }
}
