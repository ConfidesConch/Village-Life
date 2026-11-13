package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure rules of the druid (M25, owner's choice "flowering azalea, free but gentle, no refilled pits"):
 * which columns of the village he looks at for plants, and how often he helps one grow a step.
 */
public final class DruidRules {
    private DruidRules() {}

    /** Columns of the village checked per tick for plants (a full sweep of a 64 radius takes ~10 s). */
    public static final int COLUMNS_PER_TICK = 64;
    /** Plants he remembers at most. */
    public static final int MAX_TARGETS = 32;
    /** Time he tends one plant before it grows a step (at normal work speed). */
    public static final int TEND_TICKS = 60;
    /** Walking to a plant and tending it may take this long (1 minute: villages are large). */
    public static final int GIVE_UP_TICKS = 1200;
    /** A plant he could not reach is left alone for this long (5 minutes). */
    public static final int SKIP_TICKS = 6000;
    /** How close he has to be to a plant. */
    public static final double REACH = 3.0;

    /**
     * All column offsets within {@code radius} of the village center, nearest first (packed as
     * {@code dx, dz} pairs), so plants near the center are found first.
     */
    public static int[] discOffsets(int radius) {
        List<int[]> offsets = new ArrayList<>();
        long limit = (long) radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((long) dx * dx + (long) dz * dz <= limit) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        int[] packed = new int[offsets.size() * 2];
        for (int i = 0; i < offsets.size(); i++) {
            packed[i * 2] = offsets.get(i)[0];
            packed[i * 2 + 1] = offsets.get(i)[1];
        }
        return packed;
    }

    /** The next age of a growing plant, or -1 if it is fully grown. */
    public static int nextAge(int age, int maxAge) {
        return age < maxAge ? age + 1 : -1;
    }
}
