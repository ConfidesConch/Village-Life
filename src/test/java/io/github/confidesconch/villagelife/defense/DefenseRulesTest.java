package io.github.confidesconch.villagelife.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DefenseRulesTest {

    @Test
    void betterSwordsHitHarder() {
        // defenders hit twice as hard as normal (owner's wish)
        assertEquals(8.0F, DefenseRules.meleeDamage(null, false), 1e-6);
        assertEquals(8.0F, DefenseRules.meleeDamage("wooden_sword", false), 1e-6);
        assertEquals(12.0F, DefenseRules.meleeDamage("iron_sword", false), 1e-6);
        assertEquals(16.0F, DefenseRules.meleeDamage("netherite_sword", false), 1e-6);
        assertEquals(15.0F, DefenseRules.meleeDamage("iron_sword", true), 1e-6); // captain nearby
        assertEquals(2.0F, DefenseRules.arrowPower(false), 1e-6);
        assertEquals(2.5F, DefenseRules.arrowPower(true), 1e-6);
    }

    @Test
    void attacksGrowWithTheDefense() {
        double start = DefenseRules.defenderPower(2, 0, 0);
        assertEquals(2, DefenseRules.banditCount(start));             // very easy at first
        assertEquals(0, DefenseRules.banditLevel(start));
        assertEquals(0, DefenseRules.crossbowBandits(2, 0));
        double strong = DefenseRules.defenderPower(4, 3, 1);           // 8.5
        assertEquals(9, DefenseRules.banditCount(strong));
        assertEquals(3, DefenseRules.banditLevel(strong));
        assertEquals(3, DefenseRules.crossbowBandits(9, 3));
        assertEquals(DefenseRules.MAX_BANDITS, DefenseRules.banditCount(100));
        assertEquals(DefenseRules.MAX_BANDIT_LEVEL, DefenseRules.banditLevel(100));
        assertEquals(2, DefenseRules.banditCount(0));                 // never fewer than two
    }

    @Test
    void knockedOutForOneToThreeHours() {
        // knocked out at 22:00, work starts at 6:00: at most three hours = 3000 ticks
        assertEquals(1000 + 3000, DefenseRules.backOnDuty(1000, 22 * 60, 6));
        // at 4:00: back at the start of work, two hours later
        assertEquals(1000 + 2000, DefenseRules.backOnDuty(1000, 4 * 60, 6));
        // at 5:30: at least one hour
        assertEquals(1000 + 1000, DefenseRules.backOnDuty(1000, 5 * 60 + 30, 6));
    }
}
