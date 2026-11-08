package io.github.confidesconch.villagelife.resident.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathBudgetTest {
    @Test
    void limitsSearchesPerTickAndResetsNextTick() {
        PathBudget budget = new PathBudget();
        assertTrue(budget.tryStart(10, 2));
        assertTrue(budget.tryStart(10, 2));
        assertFalse(budget.tryStart(10, 2));
        assertTrue(budget.tryStart(11, 2)); // next tick: new budget
    }

    @Test
    void stopsWhenTheTimeIsUsedUp() {
        PathBudget budget = new PathBudget();
        assertTrue(budget.tryStart(5, 10));
        budget.finished(PathBudget.MAX_NANOS_PER_TICK);
        assertFalse(budget.tryStart(5, 10));
        assertTrue(budget.tryStart(6, 10));
    }
}
