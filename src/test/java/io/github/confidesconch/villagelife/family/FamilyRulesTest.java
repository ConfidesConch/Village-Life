package io.github.confidesconch.villagelife.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FamilyRulesTest {

    @Test
    void aChildNeedsABedAndRoom() {
        assertTrue(FamilyRules.hasChild(0, true, true, 0.0));
        assertFalse(FamilyRules.hasChild(0, false, true, 0.0)); // no free bed at home
        assertFalse(FamilyRules.hasChild(0, true, false, 0.0)); // village full for its stage
        assertFalse(FamilyRules.hasChild(3, true, true, 0.0));  // three children already
        assertFalse(FamilyRules.hasChild(0, true, true, 0.5));  // not this time
    }

    @Test
    void childrenGrowUpInFiveDays() {
        long born = 1000;
        long grownUp = born + FamilyRules.GROW_UP_TICKS;
        assertEquals(5, FamilyRules.daysUntilGrownUp(grownUp, born));
        assertEquals(1, FamilyRules.daysUntilGrownUp(grownUp, grownUp - 10));
        assertEquals(0, FamilyRules.daysUntilGrownUp(grownUp, grownUp));
    }
}
