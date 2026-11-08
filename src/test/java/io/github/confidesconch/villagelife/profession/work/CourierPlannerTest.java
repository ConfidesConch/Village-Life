package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CourierPlannerTest {

    @Test
    void emptiesTheFullestWorkplaceFirst() {
        assertEquals(2, CourierPlanner.fullest(List.of(10, 3, 40, 12)));
    }

    @Test
    void waitsUntilEnoughHasPiledUp() {
        assertEquals(-1, CourierPlanner.fullest(List.of(0, 7, 5)));
        assertEquals(-1, CourierPlanner.fullest(List.of()));
    }

    @Test
    void suppliesUpToSixteenWhenLow() {
        assertEquals(16, CourierPlanner.supplyAmount(0));
        assertEquals(9, CourierPlanner.supplyAmount(7));
        assertEquals(0, CourierPlanner.supplyAmount(8));
    }
}
