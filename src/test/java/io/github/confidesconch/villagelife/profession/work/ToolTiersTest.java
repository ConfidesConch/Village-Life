package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolTiersTest {

    @Test
    void betterMaterialsAreFaster() {
        assertEquals(1.0, ToolTiers.speed("wooden_axe"));
        assertTrue(ToolTiers.speed("stone_axe") > ToolTiers.speed("wooden_axe"));
        assertTrue(ToolTiers.speed("iron_hoe") > ToolTiers.speed("stone_hoe"));
        assertTrue(ToolTiers.speed("diamond_axe") > ToolTiers.speed("iron_axe"));
        assertEquals(2.0, ToolTiers.speed("netherite_axe"));
        assertEquals(ToolTiers.speed("copper_axe"), ToolTiers.speed("iron_axe"));
    }

    @Test
    void unknownMaterialsCountAsStone() {
        assertEquals(ToolTiers.speed("stone_axe"), ToolTiers.speed("bronze_axe"));
    }

    @Test
    void fasterToolsNeedFewerTicks() {
        assertEquals(15, ToolTiers.ticks(15, 1.0));
        assertEquals(10, ToolTiers.ticks(15, 1.5));
        assertEquals(8, ToolTiers.ticks(15, 2.0));
        assertEquals(1, ToolTiers.ticks(1, 2.0));
    }

    @Test
    void wornBelowAFifth() {
        assertTrue(ToolTiers.isWorn(210, 250));
        assertFalse(ToolTiers.isWorn(200, 250));
        assertFalse(ToolTiers.isWorn(0, 0)); // not damageable
    }
}
