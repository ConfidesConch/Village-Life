package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.profession.work.FishingSpots.Spot;

class FishingSpotsTest {

    private static Spot at(int x) {
        return new Spot(x, 62, 0, x, 63, 1);
    }

    @Test
    void irrigationHolesAreNoFishingSpots() {
        assertFalse(FishingSpots.isOpenWater(1));
        assertTrue(FishingSpots.isOpenWater(FishingSpots.MIN_OPEN_WATER));
    }

    @Test
    void noSpotsNoChoice() {
        assertTrue(FishingSpots.choose(List.of(), 0, 0, 0.5).isEmpty());
    }

    @Test
    void choosesAmongTheNearestSpots() {
        List<Spot> spots = List.of(at(100), at(3), at(1), at(50), at(2));
        assertEquals(at(1), FishingSpots.choose(spots, 0, 0, 0.0).orElseThrow());
        assertEquals(at(3), FishingSpots.choose(spots, 0, 0, 0.99).orElseThrow());
        for (double r = 0; r < 1; r += 0.1) {
            Spot chosen = FishingSpots.choose(spots, 0, 0, r).orElseThrow();
            assertNotEquals(at(50), chosen);
            assertNotEquals(at(100), chosen);
        }
    }

    @Test
    void fasterWorkersCatchSooner() {
        assertEquals(FishingSpots.CATCH_BASE_TICKS, FishingSpots.catchTicks(0.0, 1.0));
        assertEquals(FishingSpots.CATCH_BASE_TICKS + FishingSpots.CATCH_RANDOM_TICKS / 2, FishingSpots.catchTicks(0.5, 1.0));
        assertTrue(FishingSpots.catchTicks(0.5, 1.25) < FishingSpots.catchTicks(0.5, 1.0));
        assertTrue(FishingSpots.catchTicks(0.5, 0.5) > FishingSpots.catchTicks(0.5, 1.0));
    }
}
