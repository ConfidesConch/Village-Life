package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.profession.work.TunnelPlan.Cell;

class TunnelPlanTest {

    @Test
    void stairsGoDownThreeHighThenTheMainCorridor() {
        // into the rock towards +x, from y 10 down to y 7, then 3 blocks of corridor
        TunnelPlan plan = new TunnelPlan(0, 0, 1, 0, 10, 7, 3);
        assertEquals(3, plan.stairSteps());
        List<Cell> cells = plan.cells();
        assertEquals(new Cell(0, 11, 0, TunnelPlan.STAIRS, false, false), cells.get(0)); // entrance: 2 high, top first
        assertEquals(new Cell(0, 10, 0, TunnelPlan.STAIRS, true, false), cells.get(1));
        assertEquals(new Cell(1, 11, 0, TunnelPlan.STAIRS, false, false), cells.get(2)); // first step: 3 high
        assertEquals(new Cell(1, 9, 0, TunnelPlan.STAIRS, true, false), cells.get(4));
        assertTrue(cells.contains(new Cell(4, 7, 0, TunnelPlan.MAIN, true, false)));  // main at the bottom
        assertTrue(cells.contains(new Cell(6, 8, 0, TunnelPlan.MAIN, false, false)));
        assertEquals(2 + 3 * 3 + 3 * 2 + 2 * TunnelPlan.BRANCH_LENGTH * 2, cells.size()); // one pair of side corridors
    }

    @Test
    void sideCorridorsEveryThreeBlocksLeftAndRight() {
        TunnelPlan plan = new TunnelPlan(0, 0, 0, -1, 5, 5, 6); // north, no stairs
        assertEquals(0, plan.stairSteps());
        long branches = plan.cells().stream().mapToInt(Cell::segment).distinct().filter(s -> s >= TunnelPlan.FIRST_BRANCH).count();
        assertEquals(4, branches); // at 3 and 6, both sides
        // the side corridor at main block 3 (z = -3) goes west and east
        assertTrue(plan.cells().contains(new Cell(-TunnelPlan.BRANCH_LENGTH, 5, -3, TunnelPlan.FIRST_BRANCH, true, false)));
        assertTrue(plan.cells().contains(new Cell(TunnelPlan.BRANCH_LENGTH, 5, -3, TunnelPlan.FIRST_BRANCH + 1, true, false)));
    }

    @Test
    void torchesOnWalkingCellsOnly() {
        TunnelPlan plan = new TunnelPlan(0, 0, 1, 0, 40, 20, 16);
        assertTrue(plan.cells().stream().anyMatch(Cell::torch));
        assertFalse(plan.cells().stream().anyMatch(cell -> cell.torch() && !cell.floor()));
        assertTrue(plan.cells().contains(new Cell(28, 20, 0, TunnelPlan.MAIN, true, true))); // main block 8
        assertTrue(TunnelPlan.isTrunk(TunnelPlan.MAIN));
        assertFalse(TunnelPlan.isTrunk(TunnelPlan.FIRST_BRANCH));
    }
}
