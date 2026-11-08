package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.profession.work.CraftPlanner.Action;
import io.github.confidesconch.villagelife.profession.work.CraftPlanner.State;

class CraftPlannerTest {

    @Test
    void craftsTheFirstWantedRecipeWithInputs() {
        var choice = CraftPlanner.choose(List.of(new State(64, 64, true, true), new State(10, 32, true, true)));
        assertEquals(1, choice.index());
        assertEquals(Action.CRAFT, choice.action());
    }

    @Test
    void fetchesInputsFirst() {
        var choice = CraftPlanner.choose(List.of(new State(0, 64, false, true)));
        assertEquals(Action.FETCH, choice.action());
    }

    @Test
    void skipsRecipesWithoutMaterial() {
        var choice = CraftPlanner.choose(List.of(new State(0, 64, false, false), new State(0, 16, false, true)));
        assertEquals(1, choice.index());
        assertEquals(Action.NONE, CraftPlanner.choose(List.of(new State(64, 64, true, true))).action());
    }

    @Test
    void reportsMissingMaterialOnlyWhenNothingWantedCanBeMade() {
        assertTrue(CraftPlanner.missingMaterial(List.of(new State(0, 64, false, false))));
        assertFalse(CraftPlanner.missingMaterial(List.of(new State(0, 64, false, false), new State(0, 8, true, false))));
        assertFalse(CraftPlanner.missingMaterial(List.of(new State(64, 64, false, false)))); // nothing wanted
    }

    @Test
    void craftsWantedRoundsUp() {
        assertEquals(22, CraftPlanner.craftsWanted(0, 64, 3));
        assertEquals(0, CraftPlanner.craftsWanted(70, 64, 1));
        assertEquals(1, CraftPlanner.craftsWanted(30, 32, 8));
    }
}
