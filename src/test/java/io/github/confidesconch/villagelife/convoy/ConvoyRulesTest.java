package io.github.confidesconch.villagelife.convoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConvoyRulesTest {

    @Test
    void longRoadsAreMoreDangerousGuardsMakeThemSafer() {
        double near = ConvoyRules.raidChance(200, 0, 0);
        double far = ConvoyRules.raidChance(1000, 0, 0);
        assertTrue(far > near);
        assertTrue(ConvoyRules.raidChance(1000, 4, 0) < far);
        assertTrue(ConvoyRules.raidChance(1000, 4, 2) < ConvoyRules.raidChance(1000, 4, 0));
        assertEquals(0.02, ConvoyRules.raidChance(100, 10, 3), 1e-9); // never completely safe
        assertEquals(0.5, ConvoyRules.raidChance(1000, 0, 0), 1e-9);
    }

    @Test
    void safetyInTheBook() {
        assertEquals(98, ConvoyRules.safetyPercent(0.02));
        assertEquals("safe", ConvoyRules.safetyLevel(85));
        assertEquals("medium", ConvoyRules.safetyLevel(60));
        assertEquals("dangerous", ConvoyRules.safetyLevel(59));
    }

    @Test
    void raidsTakeAQuarterToThreeQuarters() {
        assertEquals(0.75, ConvoyRules.lossShare(0, 0), 1e-9);
        assertEquals(0.25, ConvoyRules.lossShare(3, 10), 1e-9);
        assertEquals(48, ConvoyRules.lost(64, 0.75));
        assertEquals(1, ConvoyRules.lost(1, 0.25));
        assertEquals(0, ConvoyRules.lost(0, 0.5));
    }

    @Test
    void escortsHelp() {
        assertEquals(0.0, ConvoyRules.defendChance(0), 1e-9);
        assertEquals(0.75, ConvoyRules.defendChance(3), 1e-9);
        assertEquals(5, ConvoyRules.bandits(3));
        assertEquals(0.2, ConvoyRules.raidPoint(0), 1e-9);
        assertEquals(0.8, ConvoyRules.raidPoint(1), 1e-9);
    }
}
