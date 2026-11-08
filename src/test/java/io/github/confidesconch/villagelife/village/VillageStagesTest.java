package io.github.confidesconch.villagelife.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.village.VillageStages.Status;

class VillageStagesTest {

    @Test
    void aSettlementBecomesAVillage() {
        assertEquals(1, VillageStages.next(0, new Status(10, 55, 3, true, false, 4)));
    }

    @Test
    void everyRequirementCounts() {
        assertEquals(0, VillageStages.next(0, new Status(9, 55, 3, true, false, 4)));   // too few residents
        assertEquals(0, VillageStages.next(0, new Status(10, 40, 3, true, false, 4)));  // unhappy
        assertEquals(0, VillageStages.next(0, new Status(10, -1, 3, true, false, 4)));  // nobody loaded: unknown
        assertEquals(0, VillageStages.next(0, new Status(10, 55, 2, true, false, 4)));  // too few professions
        assertEquals(0, VillageStages.next(0, new Status(10, 55, 3, false, false, 4))); // no storage
        assertEquals(0, VillageStages.next(0, new Status(10, 55, 3, true, false, 1)));  // M20a: only one guard
    }

    @Test
    void aTownNeedsAMarket() {
        assertEquals(1, VillageStages.next(1, new Status(40, 70, 9, true, false, 4)));
        assertEquals(2, VillageStages.next(1, new Status(40, 70, 9, true, true, 4)));
    }

    @Test
    void neverAboveTownAndNeverDown() {
        assertEquals(2, VillageStages.next(2, new Status(40, 70, 9, true, true, 4)));
        assertEquals(2, VillageStages.next(2, new Status(0, 0, 0, false, false, 0)));
    }

    @Test
    void residentLimitsLeaveRoomToGrow() {
        // each stage allows more residents than the next stage requires
        assertTrue(VillageStages.maxResidents(0) > VillageStages.requirementsFor(1).residents());
        assertTrue(VillageStages.maxResidents(1) > VillageStages.requirementsFor(2).residents());
        assertTrue(VillageStages.maxContracts(2) > VillageStages.maxContracts(0));
    }
}
