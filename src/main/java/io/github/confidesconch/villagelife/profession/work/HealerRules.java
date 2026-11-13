package io.github.confidesconch.villagelife.profession.work;

/**
 * Pure rules of the healer (M24, owner's choice "cauldron, goes to the injured, heals for free"):
 * how much one treatment heals, when the morning blessing is due and how long it lasts.
 */
public final class HealerRules {
    private HealerRules() {}

    /** Health points (2 = one heart) one treatment gives back. */
    public static final float HEAL_PER_TREATMENT = 4.0F;
    /** Time of one treatment. */
    public static final int TREATMENT_TICKS = 40;
    /** Blessed residents work this much faster until the evening. */
    public static final double BLESSING_SPEED = 1.1;
    /**
     * The blessing is given once a day, from 6:00 until this minute (17:00) – normally first thing in the
     * morning; later only if the village starts work late (bug review 13.11.2026: such villages got none).
     */
    public static final int BLESSING_UNTIL_MINUTE = 17 * 60;
    /** It lasts until this minute of the day (18:00, end of the default work day). */
    public static final int BLESSING_ENDS_MINUTE = 18 * 60;

    private static final int TICKS_PER_DAY = 24000;
    /** Day time 0 is 6:00. */
    private static final int MINUTES_AT_TICK_ZERO = 6 * 60;

    /** Number of the Minecraft day (counting from 0) for an overworld day time. */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /** True if the healer should bless the village now: during the day and not yet today. */
    public static boolean blessingDue(int minuteOfDay, long day, long lastBlessedDay) {
        return day != lastBlessedDay && minuteOfDay >= MINUTES_AT_TICK_ZERO && minuteOfDay < BLESSING_UNTIL_MINUTE;
    }

    /** Game time when a blessing given now ends (18:00 today; at once if that is already over). */
    public static long blessingEnds(long gameTime, long dayTime) {
        long ticksIntoDay = Math.floorMod(dayTime, (long) TICKS_PER_DAY); // 0 = 6:00
        long endTick = (long) (BLESSING_ENDS_MINUTE - MINUTES_AT_TICK_ZERO) * TICKS_PER_DAY / (24 * 60);
        return gameTime + Math.max(0, endTick - ticksIntoDay);
    }

    /** Health after one treatment, at most the maximum. */
    public static float healed(float health, float maxHealth) {
        return Math.min(maxHealth, health + HEAL_PER_TREATMENT);
    }
}
