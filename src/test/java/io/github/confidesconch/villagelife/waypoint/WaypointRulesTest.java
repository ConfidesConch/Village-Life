package io.github.confidesconch.villagelife.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WaypointRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static List<Waypoint> two() {
        List<Waypoint> one = WaypointRules.add(List.of(), "Mine", 3, OVERWORLD, 10, 64, -20, 10).orElseThrow();
        return WaypointRules.add(one, "Base", 99, OVERWORLD, 0, 70, 0, 10).orElseThrow();
    }

    @Test
    void addsWithIncreasingIdsAndValidColour() {
        List<Waypoint> list = two();
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).id());
        assertEquals(2, list.get(1).id());
        assertEquals(0, list.get(1).color()); // 99 is no colour → first colour
        assertTrue(list.get(0).visible());
    }

    @Test
    void respectsLimitAndWorldBorder() {
        assertTrue(WaypointRules.add(two(), "x", 0, OVERWORLD, 0, 0, 0, 2).isEmpty());
        assertTrue(WaypointRules.add(List.of(), "x", 0, OVERWORLD, 40_000_000, 0, 0, 5).isEmpty());
        assertTrue(WaypointRules.add(List.of(), "x", 0, "", 0, 0, 0, 5).isEmpty());
    }

    @Test
    void cleansNamesAndUsesNumberWhenEmpty() {
        Waypoint w = WaypointRules.add(List.of(), "  §cRed§r  ", 0, OVERWORLD, 0, 0, 0, 5).orElseThrow().getFirst();
        assertEquals("Red", w.name());
        Waypoint empty = WaypointRules.add(List.of(), "   ", 0, OVERWORLD, 0, 0, 0, 5).orElseThrow().getFirst();
        assertEquals("#1", empty.name());
        String longName = "x".repeat(100);
        assertEquals(WaypointRules.MAX_NAME_LENGTH,
                WaypointRules.add(List.of(), longName, 0, OVERWORLD, 0, 0, 0, 5).orElseThrow().getFirst().name().length());
    }

    @Test
    void editsTogglesAndRemoves() {
        List<Waypoint> edited = WaypointRules.edit(two(), 2, "Home", 5, true).orElseThrow();
        assertEquals("Home", edited.get(1).name());
        assertTrue(edited.get(1).shared());
        assertFalse(edited.get(0).shared());
        assertEquals(5, edited.get(1).color());
        assertFalse(WaypointRules.toggle(edited, 1).orElseThrow().getFirst().visible());
        List<Waypoint> removed = WaypointRules.remove(edited, 1).orElseThrow();
        assertEquals(1, removed.size());
        assertEquals(2, removed.getFirst().id());
        // a new waypoint never reuses the highest id still in use
        assertEquals(3, WaypointRules.add(removed, "New", 0, OVERWORLD, 0, 0, 0, 5).orElseThrow().get(1).id());
    }

    @Test
    void unknownIdsChangeNothing() {
        assertTrue(WaypointRules.edit(two(), 7, "x", 0, false).isEmpty());
        assertTrue(WaypointRules.toggle(two(), 7).isEmpty());
        assertTrue(WaypointRules.remove(two(), 7).isEmpty());
    }
}
