package io.github.confidesconch.villagelife.resident.schedule;

import io.github.confidesconch.villagelife.village.LawSet;

/**
 * What the village laws do to a resident's day (M19b, Minecraft-free, unit tested). Every law has an
 * advantage and a drawback (owner's choice "2a") and changes the mood a little (choice "4a"):
 * <ul>
 *   <li>rest day: every n-th day nobody works; +3 mood, +10 on the rest day itself</li>
 *   <li>curfew: free time ends at the curfew hour, residents go home (safe at night); -5 mood</li>
 *   <li>long evenings: work ends some hours earlier; +5 mood</li>
 * </ul>
 */
public final class LawRules {
    private LawRules() {}

    public static final int REST_DAY_LAW_MOOD = 3;
    public static final int REST_DAY_MOOD = 10;
    public static final int CURFEW_MOOD = -5;
    public static final int LONG_EVENINGS_MOOD = 5;

    /** Number of the day for a day time of the overworld clock; days start at midnight (day time 18000). */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime + 6000, (long) ScheduleClock.TICKS_PER_DAY);
    }

    /** True if {@code day} is a rest day: the last day of every interval ("every 7th day"). */
    public static boolean isRestDay(LawSet laws, long day) {
        return laws.restDay() && Math.floorMod(day + 1, laws.restInterval()) == 0;
    }

    /** Days until the next rest day (0 = today), or -1 without the law. */
    public static int daysUntilRest(LawSet laws, long day) {
        if (!laws.restDay()) {
            return -1;
        }
        int interval = laws.restInterval();
        return Math.floorMod(interval - 1 - Math.floorMod(day, interval), interval);
    }

    /** The activity after the laws: no work on rest days, earlier evenings, home after the curfew. */
    public static Activity adjust(Activity activity, int minuteOfDay, LawSet laws, int workEndHour, boolean restDay) {
        Activity result = activity;
        if (result == Activity.WORK && restDay) {
            result = Activity.LEISURE;
        }
        if (result == Activity.WORK && laws.longEvenings() && minuteOfDay >= (workEndHour - laws.longEveningHours()) * 60) {
            result = Activity.LEISURE;
        }
        if (result == Activity.LEISURE && laws.curfew() && minuteOfDay >= laws.curfewHour() * 60) {
            result = Activity.HOME;
        }
        return result;
    }

    /** Mood from the laws in force, added to the satisfaction (shown on the resident page). */
    public static int mood(LawSet laws, boolean restDay) {
        int mood = 0;
        if (laws.restDay()) {
            mood += restDay ? REST_DAY_MOOD : REST_DAY_LAW_MOOD;
        }
        if (laws.curfew()) {
            mood += CURFEW_MOOD;
        }
        if (laws.longEvenings()) {
            mood += LONG_EVENINGS_MOOD;
        }
        return mood;
    }
}
