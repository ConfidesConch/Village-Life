package io.github.confidesconch.villagelife.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.social.RelationshipRules.Stage;

class RelationshipRulesTest {

    @Test
    void pointsGiveStages() {
        assertEquals(Stage.DISLIKE, RelationshipRules.stage(-30));
        assertEquals(Stage.STRANGER, RelationshipRules.stage(0));
        assertEquals(Stage.ACQUAINTANCE, RelationshipRules.stage(10));
        assertEquals(Stage.FRIEND, RelationshipRules.stage(30));
        assertEquals(Stage.BEST_FRIEND, RelationshipRules.stage(60));
        assertTrue(Stage.BEST_FRIEND.isFriend());
        assertFalse(Stage.ACQUAINTANCE.isFriend());
    }

    @Test
    void onlyGrumpyResidentsQuarrel() {
        assertFalse(RelationshipRules.isQuarrel(80, 70, 0.0)); // both content: never
        assertTrue(RelationshipRules.isQuarrel(30, 70, 0.1));
        assertFalse(RelationshipRules.isQuarrel(30, 70, 0.5)); // grumpy, but the talk went well
    }

    @Test
    void bestFriendsSometimesBecomeACouple() {
        assertTrue(RelationshipRules.becomesCouple(70, true, 0.05));
        assertFalse(RelationshipRules.becomesCouple(70, true, 0.5));  // not this time
        assertFalse(RelationshipRules.becomesCouple(50, true, 0.05)); // only friends
        assertFalse(RelationshipRules.becomesCouple(70, false, 0.05)); // one has a partner or is a child
    }

    @Test
    void sociableUntilTheNextMorning() {
        assertEquals(120000, RelationshipRules.nextMorning(110000, 14000)); // 20:00 → 6:00 next day
    }

    @Test
    void pointsStayInRange() {
        assertEquals(2, RelationshipRules.after(0, false));
        assertEquals(-6, RelationshipRules.after(0, true));
        assertEquals(100, RelationshipRules.after(99, false));
        assertEquals(-100, RelationshipRules.after(-98, true));
    }

    @Test
    void friendsLiftTheMoodAndQuarrelsLowerIt() {
        assertEquals(5, RelationshipRules.socialMood(1, false, false));
        assertEquals(10, RelationshipRules.socialMood(4, false, false)); // capped
        assertEquals(0, RelationshipRules.socialMood(2, true, false));
        assertEquals(15, RelationshipRules.socialMood(2, false, true)); // an evening in the tavern
        assertEquals(100, RelationshipRules.withSocial(95, 10));
        assertEquals(0, RelationshipRules.withSocial(5, -10));
    }
}
