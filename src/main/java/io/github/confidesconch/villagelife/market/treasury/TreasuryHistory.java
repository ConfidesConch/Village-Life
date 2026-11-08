package io.github.confidesconch.villagelife.market.treasury;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure rules of the village treasury (M15c): the emeralds per day for the last {@link #DAYS} days and
 * the last {@link #ENTRIES} business events.
 */
public final class TreasuryHistory {
    private TreasuryHistory() {}

    public static final int DAYS = 7;
    public static final int ENTRIES = 10;
    /** A day without any known value yet. */
    public static final int UNKNOWN = -1;

    /** Emeralds counted on Minecraft day {@code day}. */
    public record DayCount(long day, int emeralds) {}

    /** The list with today's count set (the latest count of a day wins); keeps only the last {@link #DAYS} days. */
    public static List<DayCount> record(List<DayCount> days, long day, int emeralds) {
        List<DayCount> result = new ArrayList<>();
        for (DayCount d : days) {
            if (d.day() != day && d.day() > day - DAYS) {
                result.add(d);
            }
        }
        result.add(new DayCount(day, emeralds));
        result.sort(Comparator.comparingLong(DayCount::day));
        return result;
    }

    /**
     * Values for the days {@code today - 6} … {@code today}: a day without a count keeps the value of the
     * day before (nothing changed); days before the first count are {@link #UNKNOWN}.
     */
    public static int[] series(List<DayCount> days, long today) {
        int[] values = new int[DAYS];
        int last = UNKNOWN;
        for (DayCount d : days) {
            if (d.day() <= today - DAYS) {
                last = d.emeralds();
            }
        }
        for (int i = 0; i < DAYS; i++) {
            long day = today - (DAYS - 1) + i;
            for (DayCount d : days) {
                if (d.day() == day) {
                    last = d.emeralds();
                }
            }
            values[i] = last;
        }
        return values;
    }

    /** The newest entries first, at most {@link #ENTRIES}. */
    public static <T> List<T> append(List<T> entries, T entry) {
        List<T> result = new ArrayList<>();
        result.add(entry);
        for (int i = 0; i < entries.size() && result.size() < ENTRIES; i++) {
            result.add(entries.get(i));
        }
        return result;
    }
}
