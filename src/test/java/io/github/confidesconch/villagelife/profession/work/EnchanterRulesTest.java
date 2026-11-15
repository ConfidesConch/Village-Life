package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnchanterRulesTest {

    @Test
    void bookshelvesDecideTheStrength() {
        assertEquals(1, EnchanterRules.tier(0));
        assertEquals(1, EnchanterRules.tier(4));
        assertEquals(2, EnchanterRules.tier(5));
        assertEquals(2, EnchanterRules.tier(11));
        assertEquals(3, EnchanterRules.tier(12));
        assertEquals(3, EnchanterRules.tier(15));
    }

    @Test
    void efficiencyFirstThenUnbreaking() {
        assertEquals(EnchanterRules.Step.EFFICIENCY, EnchanterRules.next(0, 0, 2, true));
        assertEquals(EnchanterRules.Step.UNBREAKING, EnchanterRules.next(2, 0, 2, true));
        assertEquals(EnchanterRules.Step.UNBREAKING, EnchanterRules.next(0, 1, 2, false)); // no efficiency for tools that do not dig
        assertEquals(EnchanterRules.Step.NONE, EnchanterRules.next(2, 2, 2, true));
        assertEquals(EnchanterRules.Step.NONE, EnchanterRules.next(3, 3, 1, true)); // stronger already
    }

    @Test
    void pricesAndCosts() {
        assertEquals(4, EnchanterRules.price(1));
        assertEquals(12, EnchanterRules.price(3));
        assertEquals(10, EnchanterRules.costLevel(1));
        assertEquals(30, EnchanterRules.costLevel(3));
    }

    @Test
    void efficiencyMakesToolsFaster() {
        assertEquals(1.5, ToolTiers.withEfficiency(1.5, 0), 1e-9);
        assertEquals(1.5 * 1.45, ToolTiers.withEfficiency(1.5, 3), 1e-9);
    }
}
