package io.github.confidesconch.villagelife.profession;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkplaceCapacityTest {

    @Test
    void biggerBuildingsEmployMore() {
        assertEquals(1, WorkplaceCapacity.workers(5 * 5 * 4, 4));   // 7×7 hut: 25 floor blocks
        assertEquals(2, WorkplaceCapacity.workers(7 * 7 * 4, 4));   // 9×9: 49
        assertEquals(3, WorkplaceCapacity.workers(9 * 8 * 4, 4));   // 72
        assertEquals(4, WorkplaceCapacity.workers(20 * 20 * 4, 4)); // never more than four
        assertEquals(1, WorkplaceCapacity.workers(0, 0));
    }
}
