package io.github.confidesconch.villagelife.market.treasury;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.market.treasury.TreasuryHistory.DayCount;

class TreasuryHistoryTest {

    @Test
    void theLatestCountOfADayWins() {
        List<DayCount> days = TreasuryHistory.record(List.of(), 10, 5);
        days = TreasuryHistory.record(days, 10, 8);
        assertEquals(List.of(new DayCount(10, 8)), days);
    }

    @Test
    void keepsOnlyTheLastSevenDays() {
        List<DayCount> days = new ArrayList<>();
        for (int day = 0; day < 10; day++) {
            days = TreasuryHistory.record(days, day, day);
        }
        assertEquals(7, days.size());
        assertEquals(3, days.getFirst().day());
    }

    @Test
    void daysWithoutCountKeepThePreviousValue() {
        List<DayCount> days = List.of(new DayCount(12, 4), new DayCount(15, 9));
        int u = TreasuryHistory.UNKNOWN;
        assertArrayEquals(new int[]{u, u, 4, 4, 4, 9, 9}, TreasuryHistory.series(days, 16));
    }

    @Test
    void newestEntriesFirstAtMostTen() {
        List<Integer> entries = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            entries = TreasuryHistory.append(entries, i);
        }
        assertEquals(10, entries.size());
        assertEquals(14, entries.getFirst());
        assertEquals(5, entries.getLast());
    }
}
