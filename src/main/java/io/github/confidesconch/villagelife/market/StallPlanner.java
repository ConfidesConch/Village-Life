package io.github.confidesconch.villagelife.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rules of the daily market trader (M28c, owner's choice "a stranger every day, at the village center,
 * rare goods"), free of Minecraft so they can be unit tested: when he comes and goes and which offers
 * he brings (the same for the whole day, different from day to day and village to village).
 */
public final class StallPlanner {
    private StallPlanner() {}

    public static final int TICKS_PER_DAY = 24000;
    /** He arrives in the morning ... */
    public static final int ARRIVAL_FROM = 1000;
    public static final int ARRIVAL_UNTIL = 8000;
    /** ... and leaves at dusk. */
    public static final int LEAVE_AT = 12000;
    /** Offers he brings each day. */
    public static final int OFFERS_PER_DAY = 5;

    /** The Minecraft day number of a day time. */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /** True in the morning, if he has not been here today yet. */
    public static boolean mayArrive(long dayTime, long lastDay) {
        long time = Math.floorMod(dayTime, (long) TICKS_PER_DAY);
        return day(dayTime) != lastDay && time >= ARRIVAL_FROM && time < ARRIVAL_UNTIL;
    }

    /** Ticks until dusk (at least one). */
    public static int stayTicks(long dayTime) {
        long time = Math.floorMod(dayTime, (long) TICKS_PER_DAY);
        return (int) Math.max(1, LEAVE_AT - time);
    }

    /**
     * Picks up to {@code count} different offers by weight; {@code seed} (village and day) makes the pick
     * repeatable. Returns indexes into {@code weights}, in the order picked.
     */
    public static List<Integer> pick(List<Integer> weights, int count, long seed) {
        Random random = new Random(seed);
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i) > 0) {
                left.add(i);
            }
        }
        List<Integer> picked = new ArrayList<>();
        while (picked.size() < count && !left.isEmpty()) {
            int total = 0;
            for (int index : left) {
                total += weights.get(index);
            }
            int roll = random.nextInt(total);
            for (int i = 0; i < left.size(); i++) {
                roll -= weights.get(left.get(i));
                if (roll < 0) {
                    picked.add(left.remove(i));
                    break;
                }
            }
        }
        return picked;
    }

    /** Seed for one village on one day. */
    public static long seed(long villageHash, long day) {
        return villageHash * 31L + day * 1_000_003L;
    }
}
