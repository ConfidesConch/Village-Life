package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github.confidesconch.villagelife.profession.work.FieldPlanner.Cell;
import io.github.confidesconch.villagelife.profession.work.FieldPlanner.CellState;

import org.junit.jupiter.api.Test;

class FieldPlannerTest {
    private final Cell nearEmpty = new Cell(1, 64, 0, CellState.EMPTY);
    private final Cell farRipe = new Cell(8, 64, 0, CellState.RIPE);
    private final Cell nearRipe = new Cell(3, 64, 0, CellState.RIPE);
    private final Cell growing = new Cell(0, 64, 1, CellState.GROWING);

    @Test
    void harvestsNearestRipeCropFirst() {
        assertEquals(nearRipe, FieldPlanner.next(List.of(nearEmpty, farRipe, nearRipe, growing), 0, 0, true).orElseThrow());
    }

    @Test
    void plantsEmptyFarmlandOnlyWithSeeds() {
        assertEquals(nearEmpty, FieldPlanner.next(List.of(nearEmpty, growing), 0, 0, true).orElseThrow());
        assertTrue(FieldPlanner.next(List.of(nearEmpty, growing), 0, 0, false).isEmpty());
    }

    @Test
    void nothingToDoWhileEverythingGrows() {
        assertTrue(FieldPlanner.next(List.of(growing), 0, 0, true).isEmpty());
        assertTrue(FieldPlanner.next(List.of(), 0, 0, true).isEmpty());
    }
}
