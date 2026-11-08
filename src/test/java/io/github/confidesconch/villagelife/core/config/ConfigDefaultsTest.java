package io.github.confidesconch.villagelife.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConfigDefaultsTest {

    @Test
    void defaultsAreWithinTheirRanges() {
        // Constructing the constants already validates them; touching them forces class init.
        assertDoesNotThrow(() -> ConfigDefaults.VILLAGE_RADIUS.defaultValue());
        assertEquals(64, ConfigDefaults.VILLAGE_RADIUS.defaultValue());
        assertEquals(100, ConfigDefaults.MAX_RESIDENTS_PER_VILLAGE.defaultValue());
    }

    @Test
    void invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigDefaults.IntOption("x", 5, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new ConfigDefaults.IntOption("x", 5, 10, 1));
    }
}
