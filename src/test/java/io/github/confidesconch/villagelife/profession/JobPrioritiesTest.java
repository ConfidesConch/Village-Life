package io.github.confidesconch.villagelife.profession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;

import org.junit.jupiter.api.Test;

class JobPrioritiesTest {
    private static final List<String> DEFAULTS = List.of("farmer", "lumberjack", "miner", "teacher");
    private final UUID anna = new UUID(0, 1);
    private final UUID ben = new UUID(0, 2);
    private final Workplace farm = new Workplace(new UUID(1, 1), "farmer");
    private final Workplace hut = new Workplace(new UUID(1, 2), "lumberjack");
    private final Workplace mine = new Workplace(new UUID(1, 3), "miner");
    private final Workplace school = new Workplace(new UUID(1, 4), "teacher");

    @Test
    void rankingKeepsStoredOrderAndAddsNewProfessions() {
        assertEquals(List.of("miner", "farmer", "lumberjack", "teacher"),
                JobPriorities.ranking(List.of("miner", "gone", "farmer", "miner"), DEFAULTS));
        assertEquals(DEFAULTS, JobPriorities.ranking(List.of(), DEFAULTS));
    }

    @Test
    void moveSwapsWithNeighbourAndStopsAtTheEnds() {
        assertEquals(List.of("lumberjack", "farmer", "miner", "teacher"), JobPriorities.move(DEFAULTS, "lumberjack", -1));
        assertEquals(DEFAULTS, JobPriorities.move(DEFAULTS, "farmer", -1));
        assertEquals(DEFAULTS, JobPriorities.move(DEFAULTS, "teacher", 1));
        assertEquals(DEFAULTS, JobPriorities.move(DEFAULTS, "unknown", 1));
    }

    @Test
    void sortPutsHigherProfessionsFirst() {
        assertEquals(List.of(farm, hut, mine, school), JobPriorities.sort(List.of(school, mine, farm, hut), DEFAULTS));
    }

    @Test
    void allocationWithSortedWorkplacesFillsTheMostImportantFirst() {
        JobAllocator.Result result = JobAllocator.allocate(JobPriorities.sort(List.of(school, farm), DEFAULTS),
                List.of(anna), Map.of());
        assertEquals(farm, result.workplaceOf(anna).orElseThrow());
    }

    @Test
    void lowestWorkerMovesUpToAFreeHigherWorkplace() {
        Map<UUID, Workplace> jobs = new LinkedHashMap<>();
        jobs.put(anna, mine);
        jobs.put(ben, school);
        Map<UUID, Workplace> moves = JobPriorities.rebalance(List.of(farm, mine, school), jobs, DEFAULTS);
        assertEquals(Map.of(ben, farm), moves); // the teacher is the lowest, the miner stays
    }

    @Test
    void nobodyMovesDownOrSideways() {
        Map<UUID, Workplace> jobs = Map.of(anna, farm);
        assertTrue(JobPriorities.rebalance(List.of(farm, mine), jobs, DEFAULTS).isEmpty());
        assertTrue(JobPriorities.rebalance(List.of(farm, new Workplace(new UUID(1, 9), "farmer")), jobs, DEFAULTS).isEmpty());
    }

    @Test
    void aWorkplaceWithTwoPlacesTakesTwoWorkers() {
        JobAllocator.Result result = JobAllocator.allocate(List.of(farm, farm, school), List.of(anna, ben), Map.of());
        assertEquals(farm, result.workplaceOf(anna).orElseThrow());
        assertEquals(farm, result.workplaceOf(ben).orElseThrow());
        assertEquals(1, result.freeWorkplaces());
        // keeping jobs: only as many as there are places
        UUID cara = new UUID(0, 3);
        JobAllocator.Result kept = JobAllocator.allocate(List.of(farm, farm), List.of(anna, ben, cara),
                Map.of(anna, farm, ben, farm, cara, farm));
        assertEquals(2, kept.assignment().size());
    }

    @Test
    void morningMovesFillEveryFreePlace() {
        Map<UUID, Workplace> jobs = new LinkedHashMap<>();
        jobs.put(anna, school);
        jobs.put(ben, school);
        Map<UUID, Workplace> moves = JobPriorities.rebalance(List.of(farm, farm, school, school), jobs, DEFAULTS);
        assertEquals(Map.of(anna, farm, ben, farm), moves);
    }

    @Test
    void aPlaceLeftBehindIsNotFilledTheSameMorning() {
        Map<UUID, Workplace> jobs = new LinkedHashMap<>();
        jobs.put(anna, mine);
        jobs.put(ben, school);
        // farm and hut free: ben (teacher) takes the farm, anna (miner) the hut; the school stays empty
        Map<UUID, Workplace> moves = JobPriorities.rebalance(List.of(farm, hut, mine, school), jobs, DEFAULTS);
        assertEquals(Map.of(ben, farm, anna, hut), moves);
    }
}
