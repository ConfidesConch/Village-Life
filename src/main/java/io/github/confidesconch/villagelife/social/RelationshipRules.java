package io.github.confidesconch.villagelife.social;

/**
 * Pure rules of relationships between residents (M17, owner's choice "1a 2a 3a"): residents who meet
 * talk and grow closer; unhappy residents sometimes quarrel instead. Points from -100 to 100 give a
 * stage; friends nearby lift the mood, a recent quarrel lowers it.
 */
public final class RelationshipRules {
    private RelationshipRules() {}

    public static final int MIN = -100;
    public static final int MAX = 100;
    public static final int TALK_POINTS = 2;
    public static final int QUARREL_POINTS = -6;
    /** Below this satisfaction a resident is grumpy and may start a quarrel. */
    public static final int GRUMPY_BELOW = 40;
    /** Share of talks that turn into a quarrel when one of the two is grumpy. */
    public static final double QUARREL_CHANCE = 0.3;
    public static final int FRIEND_BONUS = 5;
    public static final int MAX_FRIEND_BONUS = 10;
    public static final int QUARREL_MALUS = -10;
    public static final long QUARREL_MEMORY_TICKS = 24000;
    /** An evening in the tavern (M17b): "sociable" until the next morning. */
    public static final int TAVERN_BONUS = 5;

    /** Stages from dislike to best friends. */
    public enum Stage {
        DISLIKE, STRANGER, ACQUAINTANCE, FRIEND, BEST_FRIEND;

        public boolean isFriend() {
            return this == FRIEND || this == BEST_FRIEND;
        }
    }

    public static Stage stage(int points) {
        if (points <= -30) {
            return Stage.DISLIKE;
        }
        if (points < 10) {
            return Stage.STRANGER;
        }
        if (points < 30) {
            return Stage.ACQUAINTANCE;
        }
        return points < 60 ? Stage.FRIEND : Stage.BEST_FRIEND;
    }

    /** Whether a talk becomes a quarrel: only if one of the two is grumpy, then with {@link #QUARREL_CHANCE}. */
    public static boolean isQuarrel(int satisfactionA, int satisfactionB, double roll) {
        return Math.min(satisfactionA, satisfactionB) < GRUMPY_BELOW && roll < QUARREL_CHANCE;
    }

    /** Points after a talk or a quarrel, within {@link #MIN}–{@link #MAX}. */
    public static int after(int points, boolean quarrel) {
        int next = points + (quarrel ? QUARREL_POINTS : TALK_POINTS);
        return Math.max(MIN, Math.min(MAX, next));
    }

    /**
     * Mood change from relationships: friends nearby lift it (at most +10), a quarrel in the last day
     * lowers it, an evening in the tavern lifts it until the next morning (M17b).
     */
    public static int socialMood(int friendsNearby, boolean recentQuarrel, boolean sociable) {
        int bonus = Math.min(MAX_FRIEND_BONUS, friendsNearby * FRIEND_BONUS);
        return bonus + (recentQuarrel ? QUARREL_MALUS : 0) + (sociable ? TAVERN_BONUS : 0);
    }

    /** Share of happy talks between single adult best friends that make them a couple (M18). */
    public static final double COUPLE_CHANCE = 0.1;

    /** Whether two best friends become a couple after a talk (M18, owner's choice "1a"). */
    public static boolean becomesCouple(int points, boolean bothSingleAdults, double roll) {
        return bothSingleAdults && stage(points) == Stage.BEST_FRIEND && roll < COUPLE_CHANCE;
    }

    /** Game time of the next morning (day time 0 = 6:00) after {@code gameTime} at {@code dayTime}. */
    public static long nextMorning(long gameTime, long dayTime) {
        return gameTime + (24000 - Math.floorMod(dayTime, 24000L));
    }

    /** Satisfaction with the social mood applied, within 0–100. */
    public static int withSocial(int satisfaction, int socialMood) {
        return Math.max(0, Math.min(100, satisfaction + socialMood));
    }
}
