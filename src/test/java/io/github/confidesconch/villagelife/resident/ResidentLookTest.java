package io.github.confidesconch.villagelife.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResidentLookTest {

    @Test
    void sameNumberSameLook() {
        assertEquals(ResidentLook.of(12345, true), ResidentLook.of(12345, true));
        assertEquals(ResidentLook.of(-7, false).key(), ResidentLook.of(-7, false).key());
    }

    @Test
    void valuesStayInsideTheBuildingBlocks() {
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            ResidentLook look = ResidentLook.of(random.nextInt(), random.nextBoolean());
            assertTrue(look.skinTone() >= 0 && look.skinTone() < ResidentLook.SKIN_TONES.length);
            assertTrue(look.face() >= 0 && look.face() < ResidentLook.FACES);
            assertTrue(look.hair() >= 0 && look.hair() < ResidentLook.HAIR_STYLES);
            assertTrue(look.hairColor() >= 0 && look.hairColor() < ResidentLook.HAIR_COLORS.length);
            assertTrue(look.top() >= 0 && look.top() < ResidentLook.TOPS);
            assertTrue(look.topColor() >= 0 && look.topColor() < ResidentLook.CLOTH_COLORS.length);
            assertTrue(look.bottom() >= 0 && look.bottom() < ResidentLook.BOTTOMS);
            assertTrue(look.bottomColor() >= 0 && look.bottomColor() < ResidentLook.PANTS_COLORS.length);
            assertTrue(look.shoeColor() >= 0 && look.shoeColor() < ResidentLook.SHOE_COLORS.length);
        }
    }

    @Test
    void manyDifferentLooks() {
        Set<String> keys = new HashSet<>();
        for (int seed = 0; seed < 300; seed++) {
            keys.add(ResidentLook.of(seed, seed % 2 == 0).key());
        }
        assertTrue(keys.size() > 280, "expected nearly every look to differ, got " + keys.size());
    }

    @Test
    void oldSkinNumbersStillGiveDifferentLooks() {
        Set<String> keys = new HashSet<>();
        for (int seed = 0; seed < 9; seed++) { // residents saved before M21c had 0–8
            keys.add(ResidentLook.of(seed, false).key());
        }
        assertEquals(9, keys.size());
    }

    @Test
    void genderFollowsTheName() {
        assertTrue(NameGenerator.isFemale("Anna"));
        assertFalse(NameGenerator.isFemale("Lukas"));
        assertFalse(NameGenerator.isFemale("Unbekannt"));
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            assertTrue(NameGenerator.isFemale(NameGenerator.random(random, true).first()));
            assertFalse(NameGenerator.isFemale(NameGenerator.random(random, false).first()));
        }
    }

    @Test
    void namesAreOnlyInOneList() {
        for (String name : NameGenerator.FEMALE_NAMES) {
            assertFalse(NameGenerator.MALE_NAMES.contains(name), name);
        }
    }
}
