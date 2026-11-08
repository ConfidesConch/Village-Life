package io.github.confidesconch.villagelife.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildingRequirementTest {

    private final BuildingRequirement house = new BuildingRequirement(12, 1, 1, 0, 0);

    @Test
    void validHousePasses() {
        assertTrue(house.check(new BuildingRequirement.Counts(20, 1, 2, 0, 0)).isEmpty());
    }

    @Test
    void reportsFirstMissingRequirement() {
        assertEquals("villagelife.building.problem.too_small",
                house.check(new BuildingRequirement.Counts(5, 0, 0, 0, 0)).orElseThrow().reasonKey());
        assertEquals("villagelife.building.problem.no_door",
                house.check(new BuildingRequirement.Counts(20, 0, 1, 0, 0)).orElseThrow().reasonKey());
        BuildingRequirement.Problem noBed = house.check(new BuildingRequirement.Counts(20, 1, 0, 0, 0)).orElseThrow();
        assertEquals("villagelife.building.problem.no_bed", noBed.reasonKey());
        assertEquals(1, noBed.required());
    }

    @Test
    void readsDataPackJsonWithDefaultsForMissingFields() {
        var json = com.google.gson.JsonParser.parseString("{\"min_volume\": 20, \"min_beds\": 2}");
        BuildingRequirement parsed = BuildingRequirement.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(new BuildingRequirement(20, 0, 2, 0, 0), parsed);
    }

    @Test
    void rejectsNegativeValuesInDataPack() {
        var json = com.google.gson.JsonParser.parseString("{\"min_doors\": -1}");
        assertTrue(BuildingRequirement.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).isError());
    }

    @Test
    void storageNeedsContainerAndWorkplaceNeedsWorkstation() {
        BuildingRequirement storage = new BuildingRequirement(8, 1, 0, 1, 0);
        BuildingRequirement workplace = new BuildingRequirement(8, 1, 0, 0, 1);
        assertEquals("villagelife.building.problem.no_container",
                storage.check(new BuildingRequirement.Counts(10, 1, 0, 0, 0)).orElseThrow().reasonKey());
        assertEquals("villagelife.building.problem.no_workstation",
                workplace.check(new BuildingRequirement.Counts(10, 1, 0, 1, 0)).orElseThrow().reasonKey());
    }
}
