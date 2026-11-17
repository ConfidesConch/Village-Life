package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class OreLuckTest {

    @Test
    void offAndNothingOnHighRolls() {
        assertNull(OreLuck.roll(-50, 0.0, 0));
        assertNull(OreLuck.roll(-50, 0.99, 100));
    }

    @Test
    void deepFindsDiamondsHighDoesNot() {
        assertEquals("diamond", OreLuck.roll(-50, 0.0, 100));
        assertEquals("coal", OreLuck.roll(100, 0.0, 100)); // up high only coal
        assertNull(OreLuck.roll(100, 0.03, 100));
        assertEquals("raw_copper", OreLuck.roll(80, 0.0, 100)); // copper from Y 96 down
    }

    @Test
    void doubledSettingRoughlyDoubles() {
        Random random = new Random(7);
        Map<String, Integer> normal = new HashMap<>();
        Map<String, Integer> doubled = new HashMap<>();
        for (int i = 0; i < 200_000; i++) {
            double roll = random.nextDouble();
            String a = OreLuck.roll(-30, roll, 100);
            String b = OreLuck.roll(-30, roll, 200);
            if (a != null) normal.merge(a, 1, Integer::sum);
            if (b != null) doubled.merge(b, 1, Integer::sum);
        }
        int n = normal.values().stream().mapToInt(Integer::intValue).sum();
        int d = doubled.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(d > n * 1.9 && d < n * 2.1, n + " vs " + d);
        assertTrue(normal.getOrDefault("diamond", 0) > 450 && normal.getOrDefault("diamond", 0) < 750); // ~0.3 %
        assertNull(normal.get("coal")); // no coal below Y 0
    }
}
