package io.github.confidesconch.villagelife.resident.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.confidesconch.villagelife.village.LawSet;
import io.github.confidesconch.villagelife.village.VillageLaws;

import org.junit.jupiter.api.Test;

class LawRulesTest {
    private static final LawSet ALL = new LawSet(true, 7, true, 20, true, 2);

    @Test
    void daysStartAtMidnight() {
        assertEquals(0, LawRules.day(17999)); // 23:59 on day 0
        assertEquals(1, LawRules.day(18000)); // 0:00
        assertEquals(1, LawRules.day(24000)); // 6:00
    }

    @Test
    void everySeventhDayIsARestDay() {
        assertFalse(LawRules.isRestDay(LawSet.DEFAULT, 0));
        assertTrue(LawRules.isRestDay(LawSet.DEFAULT, 6));
        assertTrue(LawRules.isRestDay(LawSet.DEFAULT, 13));
        assertEquals(6, LawRules.daysUntilRest(LawSet.DEFAULT, 0));
        assertEquals(0, LawRules.daysUntilRest(LawSet.DEFAULT, 6));
        assertEquals(6, LawRules.daysUntilRest(LawSet.DEFAULT, 7));
        LawSet none = LawSet.DEFAULT.with("rest_day", "false").orElseThrow();
        assertFalse(LawRules.isRestDay(none, 6));
        assertEquals(-1, LawRules.daysUntilRest(none, 6));
    }

    @Test
    void lawsChangeTheDay() {
        assertEquals(Activity.LEISURE, LawRules.adjust(Activity.WORK, 10 * 60, ALL, 18, true)); // rest day
        assertEquals(Activity.WORK, LawRules.adjust(Activity.WORK, 15 * 60, ALL, 18, false));
        assertEquals(Activity.LEISURE, LawRules.adjust(Activity.WORK, 16 * 60, ALL, 18, false)); // long evening
        assertEquals(Activity.LEISURE, LawRules.adjust(Activity.LEISURE, 19 * 60 + 59, ALL, 18, false));
        assertEquals(Activity.HOME, LawRules.adjust(Activity.LEISURE, 20 * 60, ALL, 18, false)); // curfew
        assertEquals(Activity.SLEEP, LawRules.adjust(Activity.SLEEP, 22 * 60, ALL, 18, false));
        assertEquals(Activity.LEISURE, LawRules.adjust(Activity.LEISURE, 20 * 60, LawSet.DEFAULT, 18, false));
    }

    @Test
    void moodFromLaws() {
        assertEquals(LawRules.REST_DAY_LAW_MOOD, LawRules.mood(LawSet.DEFAULT, false));
        assertEquals(LawRules.REST_DAY_MOOD, LawRules.mood(LawSet.DEFAULT, true));
        assertEquals(3 - 5 + 5, LawRules.mood(ALL, false));
    }

    @Test
    void changesApplyFromTheNextMorning() {
        VillageLaws laws = VillageLaws.DEFAULT.propose("curfew", "true", 10).orElseThrow();
        assertFalse(laws.effective(10).curfew());
        assertTrue(laws.isPending(10));
        assertTrue(laws.effective(11).curfew());
        assertTrue(laws.planned().curfew());
        // switching it back on the same day: nothing waits any more
        VillageLaws back = laws.propose("curfew", "false", 10).orElseThrow();
        assertFalse(back.isPending(10));
        assertFalse(back.effective(11).curfew());
        // invalid values are refused
        assertTrue(VillageLaws.DEFAULT.propose("rest_interval", "99", 10).isEmpty());
        assertTrue(VillageLaws.DEFAULT.propose("unknown", "1", 10).isEmpty());
    }
}
