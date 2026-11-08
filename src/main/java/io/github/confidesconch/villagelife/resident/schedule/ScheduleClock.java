package io.github.confidesconch.villagelife.resident.schedule;

/**
 * Converts Minecraft time into a time of day (Minecraft-free, unit tested). All residents use the
 * same clock, so the whole village starts work and goes to bed together.
 */
public final class ScheduleClock {
    private ScheduleClock() {}

    public static final int TICKS_PER_DAY = 24000;
    /** Minecraft day time 0 is 6:00 in the morning. */
    private static final int MINUTES_AT_TICK_ZERO = 6 * 60;

    /** Minute of the day (0–1439) for the given day time of the overworld clock. */
    public static int minuteOfDay(long dayTime) {
        long ticks = Math.floorMod(dayTime, (long) TICKS_PER_DAY);
        int minutes = (int) (ticks * 60 / 1000);
        return Math.floorMod(minutes + MINUTES_AT_TICK_ZERO, DailySchedule.MINUTES_PER_DAY);
    }

    /** Formats a minute of the day as {@code HH:MM}. */
    public static String format(int minuteOfDay) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }
}
