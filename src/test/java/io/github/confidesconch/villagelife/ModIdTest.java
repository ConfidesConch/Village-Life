package io.github.confidesconch.villagelife;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * First unit test: proves the JUnit setup works and guards the mod id format required by NeoForge.
 * MOD_ID is a compile-time constant, so this test does not load any Minecraft classes.
 */
class ModIdTest {

    @Test
    void modIdMatchesNeoForgeRules() {
        assertTrue(VillageLife.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"),
                "Mod id must match [a-z][a-z0-9_]{1,63}");
    }
}
