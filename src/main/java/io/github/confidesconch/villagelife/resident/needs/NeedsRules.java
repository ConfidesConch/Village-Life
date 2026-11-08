package io.github.confidesconch.villagelife.resident.needs;

/**
 * The rules of hunger, rest and satisfaction (M10, Minecraft-free, unit tested).
 * Food works like the player's hunger bar: 0–20. Residents never starve to death: very hungry
 * residents work at half speed until they get food (08.11.2026, owner's wish; before they stopped).
 */
public final class NeedsRules {
    private NeedsRules() {}

    public static final double MAX_FOOD = 20.0;
    /** At or below this, residents go and eat. */
    public static final double HUNGRY = 12.0;
    /** Residents eat until they reach this. */
    public static final double FULL = 18.0;
    /** At or below this, residents are starving and work at {@link #STARVING_SPEED}. */
    public static final double STARVING = 3.0;
    /** Work speed of starving residents (08.11.2026: they no longer stop completely). */
    public static final double STARVING_SPEED = 0.5;

    public static final int HAPPY_FROM = 70;
    public static final int UNHAPPY_BELOW = 40;

    private static final int TICKS_PER_DAY = 24000;

    public enum Mood { HAPPY, CONTENT, UNHAPPY }

    /** Food after {@code ticks} have passed; sleeping residents get hungry half as fast. */
    public static double drain(double food, long ticks, int foodPerDay, boolean sleeping) {
        double perTick = (double) foodPerDay / TICKS_PER_DAY * (sleeping ? 0.5 : 1.0);
        return Math.max(0.0, food - ticks * perTick);
    }

    public static double eat(double food, int nutrition) {
        return Math.min(MAX_FOOD, food + nutrition);
    }

    /**
     * Number of the night this day time belongs to: a night runs from 22:00 to 21:59 the next day,
     * so "slept last night" and "today" share the same number – also during the free evening until
     * bedtime at 21:00 (M19b; before it began at 18:00).
     */
    public static long nightIndex(long dayTime) {
        return Math.floorDiv(dayTime + 8000, TICKS_PER_DAY); // day time 16000 = 22:00
    }

    /** True if the resident slept in its bed during the night belonging to {@code dayTime}. */
    public static boolean isRested(long lastSleptNight, long dayTime) {
        return lastSleptNight == nightIndex(dayTime);
    }

    /** 0–100: half from food, a quarter each from being rested and having a home. */
    public static int satisfaction(double food, boolean rested, boolean hasHome) {
        double value = 50.0 * food / MAX_FOOD + (rested ? 25 : 0) + (hasHome ? 25 : 0);
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }

    public static Mood mood(int satisfaction) {
        if (satisfaction >= HAPPY_FROM) {
            return Mood.HAPPY;
        }
        return satisfaction < UNHAPPY_BELOW ? Mood.UNHAPPY : Mood.CONTENT;
    }

    public static boolean wantsToEat(double food) {
        return food <= HUNGRY;
    }

    public static boolean isStarving(double food) {
        return food <= STARVING;
    }

    /** Work speed factor: half when starving, slower when hungry or unhappy, a bit faster when happy. */
    public static double workSpeed(double food, int satisfaction) {
        if (isStarving(food)) {
            return STARVING_SPEED;
        }
        double speed = switch (mood(satisfaction)) {
            case HAPPY -> 1.1;
            case CONTENT -> 1.0;
            case UNHAPPY -> 0.8;
        };
        return wantsToEat(food) ? Math.min(speed, 0.8) : speed;
    }

    /**
     * Immigration interval depending on the village's average satisfaction: happy villages grow twice
     * as fast; unhappy villages get no newcomers (-1).
     */
    public static int immigrationInterval(int baseTicks, int averageSatisfaction) {
        return switch (mood(averageSatisfaction)) {
            case HAPPY -> Math.max(1, baseTicks / 2);
            case CONTENT -> baseTicks;
            case UNHAPPY -> -1;
        };
    }
}
