package io.github.confidesconch.villagelife.profession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;

import org.junit.jupiter.api.Test;

class JobAllocatorTest {
    private static final String LUMBERJACK = "villagelife:lumberjack";
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);
    private final Workplace hut1 = new Workplace(new UUID(1, 1), LUMBERJACK);
    private final Workplace hut2 = new Workplace(new UUID(1, 2), LUMBERJACK);

    @Test
    void unemployedGetFreeWorkplacesInOrder() {
        JobAllocator.Result result = JobAllocator.allocate(List.of(hut1, hut2), List.of(a, b, c), Map.of());
        assertEquals(hut1, result.workplaceOf(a).orElseThrow());
        assertEquals(hut2, result.workplaceOf(b).orElseThrow());
        assertTrue(result.workplaceOf(c).isEmpty());
        assertEquals(0, result.freeWorkplaces());
    }

    @Test
    void residentsKeepTheirWorkplace() {
        JobAllocator.Result result = JobAllocator.allocate(List.of(hut1, hut2), List.of(a, b), Map.of(b, hut1));
        assertEquals(hut1, result.workplaceOf(b).orElseThrow());
        assertEquals(hut2, result.workplaceOf(a).orElseThrow());
    }

    @Test
    void jobIsLostWhenWorkplaceIsGoneOrChangedProfession() {
        Workplace farm = new Workplace(hut1.buildingId(), "villagelife:farmer");
        JobAllocator.Result result = JobAllocator.allocate(List.of(farm), List.of(a), Map.of(a, hut1));
        assertEquals(farm, result.workplaceOf(a).orElseThrow()); // same building, new profession
        result = JobAllocator.allocate(List.of(), List.of(a), Map.of(a, hut1));
        assertTrue(result.workplaceOf(a).isEmpty());
    }

    @Test
    void noWorkplaceIsGivenTwice() {
        JobAllocator.Result result = JobAllocator.allocate(List.of(hut1), List.of(a, b), Map.of(a, hut1, b, hut1));
        assertEquals(hut1, result.workplaceOf(a).orElseThrow());
        assertTrue(result.workplaceOf(b).isEmpty());
        assertEquals(1, result.assignment().size());
    }

    @Test
    void reportsFreeWorkplaces() {
        assertEquals(2, JobAllocator.allocate(List.of(hut1, hut2), List.of(), Map.of()).freeWorkplaces());
    }

    @Test
    void grownUpChildrenPreferTheirParentsJob() {
        UUID kid = UUID.randomUUID();
        JobAllocator.Workplace bakery = new JobAllocator.Workplace(UUID.randomUUID(), "villagelife:baker");
        JobAllocator.Workplace farm = new JobAllocator.Workplace(UUID.randomUUID(), "villagelife:farmer");
        JobAllocator.Result result = JobAllocator.allocate(List.of(farm, bakery), List.of(kid), Map.of(),
                Map.of(kid, java.util.Set.of("villagelife:baker")));
        assertEquals(bakery, result.workplaceOf(kid).orElseThrow());
    }
}
