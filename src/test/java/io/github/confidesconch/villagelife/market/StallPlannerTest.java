package io.github.confidesconch.villagelife.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class StallPlannerTest {

    @Test
    void comesOncePerMorningAndLeavesAtDusk() {
        assertTrue(StallPlanner.mayArrive(24000 * 3 + 1000, 2));
        assertFalse(StallPlanner.mayArrive(24000 * 3 + 1000, 3)); // already here today
        assertFalse(StallPlanner.mayArrive(24000 * 3 + 500, 2));  // too early
        assertFalse(StallPlanner.mayArrive(24000 * 3 + 9000, 2)); // too late to set up
        assertEquals(11000, StallPlanner.stayTicks(24000 * 3 + 1000));
        assertEquals(1, StallPlanner.stayTicks(13000));
        assertEquals(3, StallPlanner.day(24000 * 3 + 23999));
    }

    @Test
    void picksDifferentOffersRepeatably() {
        List<Integer> weights = List.of(10, 10, 10, 10, 10, 10, 10, 10);
        List<Integer> first = StallPlanner.pick(weights, 5, StallPlanner.seed(42, 7));
        assertEquals(5, first.size());
        assertEquals(5, new HashSet<>(first).size()); // no offer twice
        assertEquals(first, StallPlanner.pick(weights, 5, StallPlanner.seed(42, 7))); // same day: same offers
        assertNotEquals(first, StallPlanner.pick(weights, 5, StallPlanner.seed(42, 8)));
    }

    @Test
    void fewerOffersThanWantedAndZeroWeights() {
        assertEquals(2, StallPlanner.pick(List.of(5, 0, 5), 5, 1).size()); // weight 0 never comes
        assertTrue(StallPlanner.pick(List.of(), 5, 1).isEmpty());
        assertEquals(List.of(1), StallPlanner.pick(List.of(0, 3), 5, 9));
    }
}
