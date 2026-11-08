package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.profession.work.HerdRules.Task;

class HerdRulesTest {

    @Test
    void tooManyAdultsAreCulled() {
        assertEquals(Task.CULL, HerdRules.next(9, 0, 9, 8, true));
        assertEquals(Task.CULL, HerdRules.next(9, 0, 0, 8, false));
    }

    @Test
    void babiesAreNeverCulled() {
        assertEquals(Task.NONE, HerdRules.next(8, 5, 8, 8, true));
    }

    @Test
    void breedsBelowTheLimitWithFeedAndAPair() {
        assertEquals(Task.BREED, HerdRules.next(2, 0, 2, 8, true));
        assertEquals(Task.NONE, HerdRules.next(2, 0, 2, 8, false));
        assertEquals(Task.NONE, HerdRules.next(2, 0, 1, 8, true));
        assertEquals(Task.NONE, HerdRules.next(1, 0, 1, 8, true));
    }

    @Test
    void babiesCountTowardsTheLimitWhenBreeding() {
        assertEquals(Task.BREED, HerdRules.next(6, 1, 6, 8, true));
        assertEquals(Task.NONE, HerdRules.next(6, 2, 6, 8, true));
    }
}
