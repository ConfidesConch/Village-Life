package io.github.confidesconch.villagelife.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class VillageZoneTest {
    private static final VillageArea AREA = new VillageArea(100, 200, 50);

    @Test
    void normalisesCornersInAnyOrder() {
        VillageZone zone = VillageZone.add(List.of(), AREA, "Market", 4, 120, 210, 110, 190, 10).orElseThrow().getFirst();
        assertEquals(110, zone.minX());
        assertEquals(120, zone.maxX());
        assertEquals(190, zone.minZ());
        assertEquals(210, zone.maxZ());
        assertEquals("Market", zone.name());
        assertEquals(1, zone.id());
    }

    @Test
    void cutsToTheVillageSquareAndRejectsOutside() {
        VillageZone zone = VillageZone.add(List.of(), AREA, "Big", 0, 0, 0, 1000, 1000, 10).orElseThrow().getFirst();
        assertEquals(50, zone.minX());
        assertEquals(150, zone.maxX());
        assertEquals(150, zone.minZ());
        assertEquals(250, zone.maxZ());
        assertTrue(VillageZone.add(List.of(), AREA, "Far", 0, 500, 500, 600, 600, 10).isEmpty());
    }

    @Test
    void respectsLimitEditsAndRemoves() {
        List<VillageZone> zones = VillageZone.add(List.of(), AREA, "", 2, 100, 200, 101, 201, 1).orElseThrow();
        assertEquals("#1", zones.getFirst().name());
        assertTrue(VillageZone.add(zones, AREA, "x", 0, 100, 200, 101, 201, 1).isEmpty());
        List<VillageZone> edited = VillageZone.edit(zones, 1, "Fields", 20).orElseThrow();
        assertEquals("Fields", edited.getFirst().name());
        assertEquals(0, edited.getFirst().color());
        assertTrue(VillageZone.remove(edited, 1).orElseThrow().isEmpty());
        assertTrue(VillageZone.edit(zones, 9, "x", 0).isEmpty());
    }
}
