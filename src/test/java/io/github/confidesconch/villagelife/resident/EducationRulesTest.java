package io.github.confidesconch.villagelife.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EducationRulesTest {

    @Test
    void educatedAdultsWorkUpToTwentyPercentFaster() {
        assertEquals(1.0, EducationRules.speedFactor(0), 1e-9);
        assertEquals(1.1, EducationRules.speedFactor(50), 1e-9);
        assertEquals(1.2, EducationRules.speedFactor(100), 1e-9);
        assertEquals(1.2, EducationRules.speedFactor(500), 1e-9); // capped
    }

    @Test
    void schoolIsInTheMorningOfTheWorkTime() {
        assertTrue(EducationRules.isSchoolTime(true, true, 9 * 60));
        assertFalse(EducationRules.isSchoolTime(true, true, 14 * 60)); // afternoon: play
        assertFalse(EducationRules.isSchoolTime(true, false, 9 * 60)); // not a work day hour
        assertFalse(EducationRules.isSchoolTime(false, true, 9 * 60)); // adults work
    }

    @Test
    void fiveMorningsMakeFullEducation() {
        int education = 0;
        for (int hour = 0; hour < 20; hour++) { // 5 mornings × 4 hours
            education = EducationRules.afterLesson(education);
        }
        assertEquals(100, education);
        assertEquals(100, EducationRules.afterLesson(100));
    }
}
