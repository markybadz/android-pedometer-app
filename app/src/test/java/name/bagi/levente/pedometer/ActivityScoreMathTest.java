package name.bagi.levente.pedometer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Checks the PAI-style score against the calibration it was tuned to. */
public class ActivityScoreMathTest {
    private static final int RESTING = 60;
    private static final int MAX = WatchSteps.ScoreMath.maxHeartRate(35); // 183

    @Test
    public void maxHeartRateUsesTanakaFormula() {
        assertEquals(183, MAX);
        assertEquals(166, WatchSteps.ScoreMath.maxHeartRate(60));
    }

    @Test
    public void lightActivityEarnsNothing() {
        // 25% of heart-rate reserve: below the 30% threshold.
        assertEquals(0.0, WatchSteps.ScoreMath.pointsPerMinute(91, RESTING, MAX), 0.0);
        assertEquals(0.0, WatchSteps.ScoreMath.pointsPerMinute(RESTING, RESTING, MAX), 0.0);
    }

    @Test
    public void fortyMinutesVeryHardIsAboutOneHundred() {
        double perMinute = WatchSteps.ScoreMath.pointsPerMinute(165, RESTING, MAX); // ~85% reserve
        double week = perMinute * 40;
        assertTrue("got " + week, week > 90 && week < 110);
    }

    @Test
    public void tenHoursEasyWalkingIsAboutOneHundred() {
        double perMinute = WatchSteps.ScoreMath.pointsPerMinute(109, RESTING, MAX); // ~40% reserve
        double week = perMinute * 600;
        assertTrue("got " + week, week > 90 && week < 110);
    }

    @Test
    public void harderEffortAlwaysEarnsMore() {
        double previous = 0;
        for (int bpm = 100; bpm <= 183; bpm += 5) {
            double points = WatchSteps.ScoreMath.pointsPerMinute(bpm, RESTING, MAX);
            assertTrue(points >= previous);
            previous = points;
        }
    }

    @Test
    public void heartRateAboveMaximumIsCapped() {
        assertEquals(WatchSteps.ScoreMath.pointsPerMinute(MAX, RESTING, MAX),
                WatchSteps.ScoreMath.pointsPerMinute(220, RESTING, MAX), 1e-9);
    }
}
