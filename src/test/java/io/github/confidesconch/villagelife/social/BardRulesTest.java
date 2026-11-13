package io.github.confidesconch.villagelife.social;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BardRulesTest {

    @Test
    void squareByDayTavernInTheEvening() {
        assertFalse(BardRules.playsInTavern(16 * 60 + 59, true));
        assertTrue(BardRules.playsInTavern(17 * 60, true));
        assertFalse(BardRules.playsInTavern(20 * 60, false)); // no tavern: stays on the square
    }

    @Test
    void tunesStayInRangeAndRepeat() {
        int[] tune = BardRules.tune(42L, 64);
        assertEquals(64, tune.length);
        boolean anyNote = false;
        for (int note : tune) {
            assertTrue(note == -1 || note >= 0 && note <= 24, "note " + note);
            anyNote |= note >= 0;
        }
        assertTrue(anyNote);
        assertEquals(tune[1], tune[17]); // the phrase comes back
        assertArrayEquals(tune, BardRules.tune(42L, 64)); // same seed, same tune
    }

    @Test
    void pitchLikeNoteBlocks() {
        assertEquals(0.5F, BardRules.pitch(0), 1e-6);
        assertEquals(1.0F, BardRules.pitch(12), 1e-6);
        assertEquals(2.0F, BardRules.pitch(24), 1e-6);
    }

    @Test
    void musicLiftsTheMoodOnce() {
        assertEquals(0, RelationshipRules.socialMood(0, false, false, false));
        assertEquals(BardRules.MUSIC_BONUS, RelationshipRules.socialMood(0, false, false, true));
        assertEquals(RelationshipRules.TAVERN_BONUS + BardRules.MUSIC_BONUS, RelationshipRules.socialMood(0, false, true, true));
    }
}
