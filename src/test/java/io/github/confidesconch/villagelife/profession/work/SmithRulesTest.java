package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.profession.work.SmithRules.Kind;

class SmithRulesTest {

    @Test
    void kindFollowsTheBasicTool() {
        assertEquals(Optional.of(Kind.AXE), SmithRules.kindOf("wooden_axe"));
        assertEquals(Optional.of(Kind.PICKAXE), SmithRules.kindOf("stone_pickaxe"));
        assertEquals(Optional.of(Kind.HOE), SmithRules.kindOf("wooden_hoe"));
        assertEquals(Optional.of(Kind.SWORD), SmithRules.kindOf("wooden_sword"));
        assertEquals(Optional.of(Kind.SHEARS), SmithRules.kindOf("shears"));
        assertTrue(SmithRules.kindOf("fishing_rod").isEmpty());
        assertTrue(SmithRules.kindOf("bowl").isEmpty());
    }

    @Test
    void itemNamesPerMaterial() {
        assertEquals(Optional.of("iron_axe"), Kind.AXE.itemPath("iron"));
        assertEquals(Optional.of("copper_pickaxe"), Kind.PICKAXE.itemPath("copper"));
        assertEquals(Optional.of("shears"), Kind.SHEARS.itemPath("iron"));
        assertTrue(Kind.SHEARS.itemPath("copper").isEmpty());
    }

    @Test
    void costsLikeMinecraft() {
        assertEquals(3, Kind.AXE.ingots());
        assertEquals(2, Kind.AXE.sticks());
        assertEquals(2, Kind.SWORD.ingots());
        assertEquals(1, Kind.SWORD.sticks());
        assertEquals(0, Kind.SHEARS.sticks());
        assertEquals(4, SmithRules.sticksFromPlanks(2));
        assertEquals(8, SmithRules.sticksFromLogs(1));
    }

    @Test
    void repairsLikeAnAnvil() {
        assertTrue(SmithRules.needsRepair(200, 250));
        assertFalse(SmithRules.needsRepair(100, 250));
        assertEquals(4, SmithRules.ingotsToRepair(240, 250)); // at most 4
        assertEquals(3, SmithRules.ingotsToRepair(130, 250)); // 62 per ingot
        assertEquals(6, SmithRules.damageAfterRepair(130, 250, 2));
        assertEquals(0, SmithRules.damageAfterRepair(130, 250, 3));
    }

    @Test
    void materialFromTheToolName() {
        assertEquals(Optional.of("iron"), SmithRules.materialOf("iron_pickaxe"));
        assertEquals(Optional.of("iron"), SmithRules.materialOf("shears"));
        assertEquals(Optional.of("copper"), SmithRules.materialOf("copper_axe"));
        assertTrue(SmithRules.materialOf("diamond_axe").isEmpty());
        assertTrue(SmithRules.materialOf("stone_hoe").isEmpty());
    }
}
