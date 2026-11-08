package io.github.confidesconch.villagelife.resident.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StuckTrackerTest {

    @Test
    void walkingOnIsFine() {
        StuckTracker tracker = new StuckTracker();
        tracker.reset(0, 0, 64, 0, 50);
        for (int t = 20; t <= 400; t += 20) {
            assertEquals(StuckTracker.Result.OK, tracker.update(t, t / 10.0, 64, 0, 50 - t / 10.0));
        }
    }

    @Test
    void standingStillIsStuckAfterThreeSeconds() {
        StuckTracker tracker = new StuckTracker();
        tracker.reset(0, 0, 64, 0, 50);
        assertEquals(StuckTracker.Result.OK, tracker.update(40, 0.1, 64, 0, 50));
        assertEquals(StuckTracker.Result.STUCK, tracker.update(60, 0.2, 64, 0, 50));
        assertEquals(StuckTracker.Result.OK, tracker.update(80, 0.2, 64, 0, 50)); // next check in 3 s
    }

    @Test
    void walkingInCirclesNeedsARescueAfterThirtySeconds() {
        StuckTracker tracker = new StuckTracker();
        tracker.reset(0, 0, 64, 0, 50);
        StuckTracker.Result last = StuckTracker.Result.OK;
        for (int t = 20; t <= 600; t += 20) {
            last = tracker.update(t, Math.sin(t) * 5, 64, Math.cos(t) * 5, 49); // moves, but never closer
        }
        assertEquals(StuckTracker.Result.RESCUE, last);
        tracker.postponeRescue(600);
        assertEquals(StuckTracker.Result.OK, tracker.update(620, 3, 64, 3, 49));
        assertEquals(StuckTracker.Result.RESCUE, tracker.update(700, 1, 64, 1, 49));
    }

    @Test
    void gettingCloserResetsTheRescueClock() {
        StuckTracker tracker = new StuckTracker();
        tracker.reset(0, 0, 64, 0, 50);
        tracker.update(590, 1, 64, 0, 45);
        assertEquals(StuckTracker.Result.OK, tracker.update(700, 5, 64, 0, 45));
    }
}
