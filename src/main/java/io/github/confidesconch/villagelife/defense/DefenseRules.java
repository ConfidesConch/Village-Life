package io.github.confidesconch.villagelife.defense;

import io.github.confidesconch.villagelife.profession.work.ToolTiers;

/**
 * Numbers of village defense (M20a, Minecraft-free, unit tested): how hard guards hit, the captain's
 * bonus and how long a knocked-out defender rests.
 */
public final class DefenseRules {
    private DefenseRules() {}

    public static final String GUARD = "villagelife:guard";
    public static final String ARCHER = "villagelife:archer";
    public static final String CAPTAIN = "villagelife:captain";

    /** A captain within this many blocks makes guards and archers stronger. */
    public static final double CAPTAIN_RANGE = 16.0;
    public static final double CAPTAIN_BONUS = 1.25;
    /** Defenders hit twice as hard and take half the damage, whatever armor they wear (owner's wish). */
    public static final double DEFENDER_STRENGTH = 2.0;
    public static final float DEFENDER_DAMAGE_TAKEN = 0.5F;
    /** Base damage factor of arrows (a skeleton shoots with about 1.0), times {@link #DEFENDER_STRENGTH}. */
    public static final float ARROW_POWER = (float) (1.0 * DEFENDER_STRENGTH);
    /** Knocked-out defenders rest at least one and at most three in-game hours. */
    private static final int MIN_REST_MINUTES = 60;
    private static final int MAX_REST_MINUTES = 3 * 60;

    /**
     * Damage of one sword hit: 4 with the wooden basic sword, 5 stone, 6 copper/iron/gold, 7 diamond, 8
     * netherite ({@link ToolTiers} speed × 4), times {@link #DEFENDER_STRENGTH}; times {@link #CAPTAIN_BONUS}
     * near a captain.
     */
    public static float meleeDamage(String swordPath, boolean captainNear) {
        double damage = 4.0 * (swordPath == null ? ToolTiers.BASIC : ToolTiers.speed(swordPath)) * DEFENDER_STRENGTH;
        return (float) (captainNear ? damage * CAPTAIN_BONUS : damage);
    }

    public static float arrowPower(boolean captainNear) {
        return captainNear ? (float) (ARROW_POWER * CAPTAIN_BONUS) : ARROW_POWER;
    }

    // ---- bandit attacks (M20b) -----------------------------------------------------------------

    /** Chance per night (checked at 22:00) that bandits attack a village of stage "village" or higher. */
    public static final double ATTACK_CHANCE = 0.2;
    public static final int ATTACK_FROM_MINUTE = 22 * 60;
    public static final int ATTACK_UNTIL_MINUTE = 23 * 60;
    /** Bandits still alive at this time take loot and leave. */
    public static final int RETREAT_FROM_MINUTE = 6 * 60;
    public static final int MAX_BANDITS = 16;
    public static final int MAX_BANDIT_LEVEL = 4;

    /** Strength of the village's defenders: guards and archers 1, captains 1.5 (not knocked out ones). */
    public static double defenderPower(int guards, int archers, int captains) {
        return guards + archers + captains * 1.5;
    }

    /** Number of bandits: as many as the defenders' strength, at least 2 ("very easy at first"). */
    public static int banditCount(double power) {
        return Math.max(2, Math.min(MAX_BANDITS, (int) Math.round(power)));
    }

    /** Equipment level 0–4 of the bandits (sword wood … diamond, more health): grows with the defense. */
    public static int banditLevel(double power) {
        return Math.max(0, Math.min(MAX_BANDIT_LEVEL, (int) Math.floor((power - 2) / 2)));
    }

    /** Bandits with crossbows: none at level 0, then every third bandit. */
    public static int crossbowBandits(int count, int level) {
        return level == 0 ? 0 : count / 3;
    }

    /** Health of a bandit: 10 at first (three hits with the basic sword), up to 22. */
    public static float banditHealth(int level) {
        return 10 + 3 * level;
    }

    /**
     * Game time when a defender knocked out at {@code gameTime} (minute of day {@code minuteOfDay}) is back:
     * the next start of work, but after one hour at the earliest and three hours at the latest (so that a
     * village is not without guards for a whole day).
     */
    public static long backOnDuty(long gameTime, int minuteOfDay, int workStartHour) {
        int minutes = Math.floorMod(workStartHour * 60 - minuteOfDay, 24 * 60);
        minutes = Math.max(MIN_REST_MINUTES, Math.min(MAX_REST_MINUTES, minutes));
        return gameTime + minutes * 1000L / 60; // 1000 ticks per in-game hour
    }
}
