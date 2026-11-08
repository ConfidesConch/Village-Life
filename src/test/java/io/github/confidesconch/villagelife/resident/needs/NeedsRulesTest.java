package io.github.confidesconch.villagelife.resident.needs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NeedsRulesTest {

    @Test
    void hungerGrowsOverTheDayAndSlowerWhileSleeping() {
        assertEquals(10.0, NeedsRules.drain(20.0, 24000, 10, false), 1e-9);
        assertEquals(15.0, NeedsRules.drain(20.0, 24000, 10, true), 1e-9);
        assertEquals(0.0, NeedsRules.drain(1.0, 240000, 10, false), 1e-9);
        assertEquals(20.0, NeedsRules.drain(20.0, 24000, 0, false), 1e-9);
    }

    @Test
    void eatingIsCappedAtTwenty() {
        assertEquals(17.0, NeedsRules.eat(12.0, 5), 1e-9);
        assertEquals(20.0, NeedsRules.eat(18.0, 5), 1e-9);
    }

    @Test
    void sleepingLastNightMeansRestedToday() {
        long sleptAt = 16000; // 22:00 on day 0
        long night = NeedsRules.nightIndex(sleptAt);
        assertTrue(NeedsRules.isRested(night, 23000)); // 5:00
        assertTrue(NeedsRules.isRested(night, 30000)); // 12:00 the next day
        assertTrue(NeedsRules.isRested(night, 36000)); // 18:00 the next day: free evening, still rested (M19b)
        assertFalse(NeedsRules.isRested(night, 40000)); // 22:00 the next day: a new night begins
    }

    @Test
    void satisfactionAndMood() {
        assertEquals(100, NeedsRules.satisfaction(20, true, true));
        assertEquals(NeedsRules.Mood.HAPPY, NeedsRules.mood(100));
        assertEquals(NeedsRules.Mood.CONTENT, NeedsRules.mood(NeedsRules.satisfaction(12, true, false)));
        assertEquals(NeedsRules.Mood.UNHAPPY, NeedsRules.mood(NeedsRules.satisfaction(4, false, false)));
    }

    @Test
    void hungryResidentsWorkSlowerAndStarvingOnesAtHalfSpeed() {
        assertEquals(1.1, NeedsRules.workSpeed(20, 100), 1e-9);
        assertEquals(0.8, NeedsRules.workSpeed(10, 100), 1e-9);
        assertEquals(0.5, NeedsRules.workSpeed(3, 100), 1e-9);
        assertEquals(0.5, NeedsRules.workSpeed(0, 0), 1e-9);
        assertTrue(NeedsRules.isStarving(2));
        assertFalse(NeedsRules.isStarving(3.5));
        assertTrue(NeedsRules.wantsToEat(12));
        assertFalse(NeedsRules.wantsToEat(12.5));
    }

    @Test
    void happyVillagesGrowFasterUnhappyOnesNot() {
        assertEquals(1200, NeedsRules.immigrationInterval(2400, 80));
        assertEquals(2400, NeedsRules.immigrationInterval(2400, 50));
        assertEquals(-1, NeedsRules.immigrationInterval(2400, 20));
    }
}
