package io.github.confidesconch.villagelife.resident.schedule;

/**
 * Night shift of the defenders (M20b2, owner's choice "3a", Minecraft-free, unit tested): they keep
 * watch from the end of work until the start of work, have an hour off, sleep for eight hours and have
 * the rest of the afternoon off. The day shift keeps the normal daily plan.
 */
public final class ShiftRules {
    private ShiftRules() {}

    public static final int SLEEP_HOURS = 8;

    public static Activity nightShift(int minuteOfDay, int workStartHour, int workEndHour) {
        int start = workStartHour * 60;
        int end = workEndHour * 60;
        if (minuteOfDay >= end || minuteOfDay < start) {
            return Activity.WORK;
        }
        int sleepFrom = start + 60;
        int sleepUntil = sleepFrom + SLEEP_HOURS * 60;
        return minuteOfDay >= sleepFrom && minuteOfDay < sleepUntil ? Activity.SLEEP : Activity.LEISURE;
    }

    /** Defenders are split in turns: the 1st, 3rd, … (by a fixed order) keep the day, the others the night. */
    public static boolean isNightShift(int indexAmongDefenders) {
        return indexAmongDefenders % 2 == 1;
    }
}
