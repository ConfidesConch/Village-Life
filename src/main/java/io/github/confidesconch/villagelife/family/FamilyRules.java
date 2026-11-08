package io.github.confidesconch.villagelife.family;

/**
 * Pure rules of families (M18b, owner's choice "2a 3a"): a couple sometimes has a child if a bed is free
 * in its own house and the village has room; at most {@link #MAX_CHILDREN} per couple. Children grow up
 * in {@link #GROW_UP_TICKS} (5 Minecraft days).
 */
public final class FamilyRules {
    private FamilyRules() {}

    public static final int MAX_CHILDREN = 3;
    public static final long GROW_UP_TICKS = 5 * 24000L;
    /** Chance per check (once a minute) – on average a child about every two and a half days. */
    public static final double BIRTH_CHANCE = 1.0 / 60;
    /** Children are half as tall. */
    public static final double CHILD_SCALE = 0.5;

    /** Whether a couple gets a child now. */
    public static boolean hasChild(int children, boolean freeBedAtHome, boolean villageHasRoom, double roll) {
        return children < MAX_CHILDREN && freeBedAtHome && villageHasRoom && roll < BIRTH_CHANCE;
    }

    /** Whole days until a child born at {@code grownUpGameTime - GROW_UP_TICKS} grows up (at least 1 while a child). */
    public static int daysUntilGrownUp(long grownUpGameTime, long gameTime) {
        long left = grownUpGameTime - gameTime;
        return left <= 0 ? 0 : (int) ((left + 23999) / 24000);
    }
}
