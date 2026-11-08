package io.github.confidesconch.villagelife.profession.work;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Rules of the fisherman (M13a, Minecraft-free, unit tested): which water counts as a fishing spot,
 * which spot to pick and how long a catch takes.
 */
public final class FishingSpots {
    private FishingSpots() {}

    /** Water source blocks needed in the 5 × 5 area around a spot: no fishing in irrigation holes. */
    public static final int MIN_OPEN_WATER = 8;
    /** A catch takes this long with the basic rod at normal speed, plus up to {@link #CATCH_RANDOM_TICKS}. */
    public static final int CATCH_BASE_TICKS = 200;
    public static final int CATCH_RANDOM_TICKS = 400;
    /** The spot is picked at random among this many nearest ones, so fishermen spread out a little. */
    static final int CHOICES = 3;

    /** A water block to fish in and the bank position the fisherman stands on. */
    public record Spot(int waterX, int waterY, int waterZ, int standX, int standY, int standZ) {
        double distanceSqr(double x, double z) {
            double dx = standX + 0.5 - x;
            double dz = standZ + 0.5 - z;
            return dx * dx + dz * dz;
        }
    }

    public static boolean isOpenWater(int waterSourcesAround) {
        return waterSourcesAround >= MIN_OPEN_WATER;
    }

    /**
     * Picks a spot among the {@link #CHOICES} nearest to (x, z).
     *
     * @param random a number from 0 (inclusive) to 1 (exclusive)
     */
    public static Optional<Spot> choose(List<Spot> spots, double x, double z, double random) {
        if (spots.isEmpty()) {
            return Optional.empty();
        }
        List<Spot> nearest = spots.stream()
                .sorted(Comparator.comparingDouble(spot -> spot.distanceSqr(x, z)))
                .limit(CHOICES)
                .toList();
        int index = Math.min(nearest.size() - 1, (int) (random * nearest.size()));
        return Optional.of(nearest.get(Math.max(0, index)));
    }

    /**
     * Ticks until the next catch.
     *
     * @param random a number from 0 (inclusive) to 1 (exclusive)
     * @param speed  overall work speed (tool × needs), see {@code WorkGoal#workSpeed}
     */
    public static int catchTicks(double random, double speed) {
        int basic = CATCH_BASE_TICKS + (int) (random * CATCH_RANDOM_TICKS);
        return ToolTiers.ticks(basic, speed);
    }
}
