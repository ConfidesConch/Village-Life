package io.github.confidesconch.villagelife.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class NameGeneratorTest {

    @Test
    void namesComeFromTheListsAndAreNotEmpty() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            NameGenerator.Name name = NameGenerator.random(random);
            assertTrue(NameGenerator.FIRST_NAMES.contains(name.first()));
            assertTrue(NameGenerator.LAST_NAMES.contains(name.last()));
            assertEquals(name.first() + " " + name.last(), name.full());
        }
    }

    @Test
    void producesVariety() {
        Random random = new Random(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(NameGenerator.random(random).full());
        }
        assertTrue(seen.size() > 30, "expected many different names, got " + seen.size());
    }

    @Test
    void listsHaveNoDuplicates() {
        assertEquals(NameGenerator.FIRST_NAMES.size(), new HashSet<>(NameGenerator.FIRST_NAMES).size());
        assertEquals(NameGenerator.LAST_NAMES.size(), new HashSet<>(NameGenerator.LAST_NAMES).size());
    }

    @Test
    void hiddenNamesTurnUpAtMostOncePerVillage() {
        Random random = new Random(3);
        List<String> village = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            village.add(NameGenerator.newcomer(random, village).full());
        }
        for (NameGenerator.Name special : NameGenerator.SPECIAL_NAMES) {
            long count = village.stream().filter(special.full()::equals).count();
            assertTrue(count == 1, special.full() + " " + count + " times");
        }
    }

    @Test
    void hiddenNamesAreRare() {
        Random random = new Random(11);
        int hits = 0;
        for (int i = 0; i < 1000; i++) {
            if (NameGenerator.SPECIAL_NAMES.contains(NameGenerator.newcomer(random, List.of()))) {
                hits++;
            }
        }
        assertTrue(hits > 20 && hits < 90, "about 5 % expected, got " + hits);
        assertFalse(NameGenerator.isFemale("Jeremy"));
    }
}
