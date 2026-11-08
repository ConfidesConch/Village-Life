package io.github.confidesconch.villagelife.resident.housing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.resident.housing.HousingAllocator.Bed;

class HousingAllocatorTest {

    private final UUID house = UUID.randomUUID();
    private final Bed bed1 = new Bed(house, 0, 64, 0);
    private final Bed bed2 = new Bed(house, 2, 64, 0);
    private final UUID anna = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID clara = UUID.randomUUID();

    @Test
    void newResidentsGetFreeBedsInOrder() {
        var result = HousingAllocator.allocate(List.of(bed1, bed2), List.of(anna, ben, clara), Map.of());
        assertEquals(bed1, result.bedOf(anna).orElseThrow());
        assertEquals(bed2, result.bedOf(ben).orElseThrow());
        assertTrue(result.bedOf(clara).isEmpty(), "only two beds exist");
        assertEquals(0, result.freeBeds());
    }

    @Test
    void residentsKeepTheirBed() {
        var result = HousingAllocator.allocate(List.of(bed1, bed2), List.of(anna, ben), Map.of(ben, bed1));
        assertEquals(bed1, result.bedOf(ben).orElseThrow());
        assertEquals(bed2, result.bedOf(anna).orElseThrow());
    }

    @Test
    void removedBedIsLostAndReassigned() {
        Bed removed = new Bed(house, 9, 64, 9);
        var result = HousingAllocator.allocate(List.of(bed1), List.of(anna), Map.of(anna, removed));
        assertEquals(bed1, result.bedOf(anna).orElseThrow());
    }

    @Test
    void noBedIsSharedEvenIfDataIsInconsistent() {
        var result = HousingAllocator.allocate(List.of(bed1, bed2), List.of(anna, ben), Map.of(anna, bed1, ben, bed1));
        assertEquals(2, new HashSet<>(result.assignment().values()).size());
    }

    @Test
    void reportsFreeBeds() {
        var result = HousingAllocator.allocate(List.of(bed1, bed2), List.of(anna), Map.of());
        assertEquals(1, result.freeBeds());
        assertEquals(2, HousingAllocator.allocate(List.of(bed1, bed2), List.of(), Map.of()).freeBeds());
    }

    @Test
    void couplesMoveIntoTheSameHouse() {
        UUID otherHouse = UUID.randomUUID();
        Bed far = new Bed(otherHouse, 9, 64, 9);
        // anna lives in "house", ben in the other house; a bed is free next to anna
        var result = HousingAllocator.allocate(List.of(bed1, bed2, far), List.of(anna, ben),
                Map.of(anna, bed1, ben, far), Map.of(anna, ben, ben, anna));
        assertEquals(house, result.bedOf(ben).orElseThrow().buildingId());
        assertEquals(1, result.freeBeds());
    }

    @Test
    void aNewcomerGetsABedNextToThePartner() {
        UUID otherHouse = UUID.randomUUID();
        Bed far = new Bed(otherHouse, 9, 64, 9);
        var result = HousingAllocator.allocate(List.of(far, bed1, bed2), List.of(anna, ben),
                Map.of(anna, bed1), Map.of(anna, ben, ben, anna));
        assertEquals(bed2, result.bedOf(ben).orElseThrow());
    }
}
