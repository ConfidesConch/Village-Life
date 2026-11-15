package io.github.confidesconch.villagelife.playtime;

import java.util.Comparator;
import java.util.List;

/**
 * Pure rules of the playtime tracker (M29, ported from the owner's KubeJS script "playtimetracker.js"):
 * ticks to seconds, splitting into hours/minutes/seconds, and the top list. Free of Minecraft so it can
 * be unit tested.
 */
public final class PlaytimeFormat {
    private PlaytimeFormat() {}

    /** Entries shown by {@code /playtime top}. */
    public static final int TOP_SIZE = 10;

    /** One player in the top list. */
    public record Entry(String name, long seconds) {}

    /** Minecraft counts play time in ticks, 20 per second. */
    public static long seconds(long ticks) {
        return Math.max(0, ticks) / 20;
    }

    /** {hours, minutes, seconds} of a duration in seconds. */
    public static long[] split(long seconds) {
        return new long[] {seconds / 3600, (seconds % 3600) / 60, seconds % 60};
    }

    /** The {@link #TOP_SIZE} entries with the most play time, most first (names break ties). */
    public static List<Entry> top(List<Entry> entries) {
        return entries.stream()
                .sorted(Comparator.comparingLong(Entry::seconds).reversed().thenComparing(Entry::name))
                .limit(TOP_SIZE)
                .toList();
    }
}
