package io.github.confidesconch.villagelife.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.market.CaravanPlanner.Deal;
import io.github.confidesconch.villagelife.market.CaravanPlanner.Good;

class CaravanPlannerTest {

    @Test
    void comesEveryFewDaysInTheMorning() {
        assertEquals(72000, CaravanPlanner.nextVisit(0, 3));
        assertTrue(CaravanPlanner.mayArrive(72000, 2000, 72000));
        assertFalse(CaravanPlanner.mayArrive(71999, 2000, 72000)); // not due yet
        assertFalse(CaravanPlanner.mayArrive(80000, 13000, 72000)); // evening: wait for the next morning
        assertTrue(CaravanPlanner.mayArrive(100000, 24000 * 5 + 1500, 72000));
    }

    @Test
    void buysAtMostFiveBundlesSpreadOverTheGoods() {
        List<Deal> deals = CaravanPlanner.plan(List.of(new Good(10, 0, 1), new Good(2, 0, 1)), 0);
        assertEquals(5, deals.size());
        assertEquals(List.of(new Deal(0, true), new Deal(1, true), new Deal(0, true), new Deal(1, true), new Deal(0, true)), deals);
    }

    @Test
    void sellsOnlyWhatTheVillageCanPay() {
        List<Deal> deals = CaravanPlanner.plan(List.of(new Good(0, 4, 3)), 7);
        assertEquals(List.of(new Deal(0, false), new Deal(0, false)), deals); // 2 × 3 = 6 of 7 emeralds
    }

    @Test
    void earningsFromTheSurplusPayForMissingGoods() {
        List<Deal> deals = CaravanPlanner.plan(List.of(new Good(1, 0, 2), new Good(0, 1, 2)), 0);
        assertEquals(List.of(new Deal(0, true), new Deal(1, false)), deals);
    }

    @Test
    void nothingToTradeMeansNoDeals() {
        assertTrue(CaravanPlanner.plan(List.of(new Good(0, 0, 1)), 50).isEmpty());
    }
}
