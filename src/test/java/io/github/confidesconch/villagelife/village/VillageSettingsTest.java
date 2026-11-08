package io.github.confidesconch.villagelife.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillageSettingsTest {
    private final VillageSettings settings = VillageSettings.DEFAULT;

    @Test
    void changesValidValues() {
        assertFalse(settings.with("immigration", "false").orElseThrow().immigration());
        assertEquals(8, settings.with("work_start", "8").orElseThrow().workStart());
        assertEquals(20, settings.with("work_end", "20").orElseThrow().workEnd());
        assertEquals(32, settings.with("food_reserve", "32").orElseThrow().foodReserve());
        assertFalse(settings.with("strangers_may_read", "false").orElseThrow().strangersMayRead());
    }

    @Test
    void rejectsInvalidValuesAndUnknownKeys() {
        assertTrue(settings.with("work_start", "3").isEmpty());
        assertTrue(settings.with("work_end", "21").isEmpty());
        assertTrue(settings.with("food_reserve", "-1").isEmpty());
        assertTrue(settings.with("food_reserve", "lots").isEmpty());
        assertTrue(settings.with("immigration", "yes").isEmpty());
        assertTrue(settings.with("give_me_diamonds", "true").isEmpty());
    }

    @Test
    void defaultHoursAreDetected() {
        assertTrue(settings.usesDefaultHours());
        assertFalse(settings.with("work_start", "7").orElseThrow().usesDefaultHours());
    }

    @Test
    void cleansNames() {
        assertEquals("Eichenhain", VillageSettings.cleanName("  §6Eichenhain  "));
        assertEquals("", VillageSettings.cleanName("   "));
        assertEquals(32, VillageSettings.cleanName("x".repeat(50)).length());
    }
}
