package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HealerRulesTest {

    @Test
    void blessingOncePerMorning() {
        assertTrue(HealerRules.blessingDue(7 * 60, 3, 2));
        assertFalse(HealerRules.blessingDue(7 * 60, 3, 3)); // already today
        assertTrue(HealerRules.blessingDue(13 * 60, 3, 2)); // a village that starts work late
        assertFalse(HealerRules.blessingDue(17 * 60 + 30, 3, 2)); // evening: too late
        assertFalse(HealerRules.blessingDue(5 * 60, 3, 2)); // still night
    }

    @Test
    void blessingLastsUntilSix() {
        assertEquals(1000L + 12000L, HealerRules.blessingEnds(1000L, 0L)); // 6:00 → 18:00 = 12 h
        assertEquals(500L + 2000L, HealerRules.blessingEnds(500L, 10000L)); // 16:00 → 2 h
        assertEquals(500L, HealerRules.blessingEnds(500L, 15000L)); // 21:00: over at once
    }

    @Test
    void daysAndHealing() {
        assertEquals(0L, HealerRules.day(23999L));
        assertEquals(1L, HealerRules.day(24000L));
        assertEquals(14.0F, HealerRules.healed(10.0F, 20.0F), 1e-6);
        assertEquals(20.0F, HealerRules.healed(18.0F, 20.0F), 1e-6);
    }
}
