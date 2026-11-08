package io.github.confidesconch.villagelife.market.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContractRulesTest {

    private static ContractTerms terms(String good, int percent, int max, int days, String payment, int count) {
        return new ContractTerms(good, percent, max, days, payment, count);
    }

    @Test
    void acceptsSensibleTerms() {
        assertTrue(ContractRules.isValid(terms("minecraft:wheat", 30, 64, 3, "minecraft:emerald", 4)));
    }

    @Test
    void rejectsTermsOutsideTheLimits() {
        assertFalse(ContractRules.isValid(terms("minecraft:wheat", 35, 64, 3, "minecraft:emerald", 4))); // not in steps of 10
        assertFalse(ContractRules.isValid(terms("minecraft:wheat", 30, 300, 3, "minecraft:emerald", 4)));
        assertFalse(ContractRules.isValid(terms("minecraft:wheat", 30, 64, 0, "minecraft:emerald", 4)));
        assertFalse(ContractRules.isValid(terms("minecraft:wheat", 30, 64, 3, "minecraft:emerald", 0)));
        assertFalse(ContractRules.isValid(terms("minecraft:wheat", 30, 64, 3, "minecraft:wheat", 4))); // paid with itself
        assertFalse(ContractRules.isValid(terms("", 30, 64, 3, "minecraft:emerald", 4)));
    }

    @Test
    void deliversAShareOfTheSurplusUpToTheMaximum() {
        assertEquals(30, ContractRules.delivery(100, 30, 64));
        assertEquals(64, ContractRules.delivery(1000, 30, 64));
        assertEquals(0, ContractRules.delivery(0, 30, 64));
        assertEquals(0, ContractRules.delivery(-5, 30, 64));
    }

    @Test
    void travelsOneHourPerHundredBlocks() {
        assertEquals(3000, ContractRules.travelTicks(300));
        assertEquals(1000, ContractRules.travelTicks(20)); // at least one hour
        assertEquals(24000, ContractRules.travelTicks(10000)); // at most one day
    }

    @Test
    void debtIsPaidWithTheNextPayment() {
        assertEquals(7, ContractRules.dueWithDebt(4, 3));
        assertEquals(3, ContractRules.dueWithDebt(0, 3)); // a skipped delivery still lets the debt be paid
    }

    @Test
    void threeFailuresInARowEndTheContract() {
        assertFalse(ContractRules.cancelsAfter(2));
        assertTrue(ContractRules.cancelsAfter(3));
    }

    @Test
    void paysProportionally() {
        assertEquals(4, ContractRules.payment(64, 64, 4));
        assertEquals(2, ContractRules.payment(32, 64, 4));
        assertEquals(1, ContractRules.payment(30, 64, 4)); // rounded down
        assertEquals(0, ContractRules.payment(0, 64, 4));
    }
}
