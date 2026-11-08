package io.github.confidesconch.villagelife.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillageAreaTest {

    private final VillageArea area = new VillageArea(100, -50, 64);

    @Test
    void containsCenterAndBorder() {
        assertTrue(area.contains(100, -50));
        assertTrue(area.contains(164, -50));   // exactly on the border
        assertFalse(area.contains(165, -50));  // one block outside
        assertFalse(area.contains(150, 0));    // diagonal: 50² + 50² > 64²
    }

    @Test
    void overlapDependsOnBothRadii() {
        assertTrue(area.overlaps(new VillageArea(228, -50, 64)));   // borders touch
        assertFalse(area.overlaps(new VillageArea(229, -50, 64)));  // one block gap
        assertTrue(area.overlaps(new VillageArea(200, -50, 40)));
    }

    @Test
    void handlesLargeCoordinatesWithoutOverflow() {
        VillageArea far = new VillageArea(29_000_000, 29_000_000, 256);
        assertTrue(far.contains(29_000_100, 29_000_100));
        assertFalse(far.overlaps(new VillageArea(-29_000_000, -29_000_000, 256)));
    }

    @Test
    void rejectsNonPositiveRadius() {
        assertThrows(IllegalArgumentException.class, () -> new VillageArea(0, 0, 0));
    }
}
