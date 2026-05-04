package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SchedulerTunerTest {
    @Test
    public void waitsForEnoughReviewsAndMonthlyWindow() {
        SchedulerTuner tuner = new SchedulerTuner();
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();

        Records.SchedulerParameters tooFew = tuner.maybeTune(defaults, new Records.ReviewStats(10, 4, 2, 3, 1, 5, 2), SchedulerTuner.MONTH_MILLIS);
        assertEquals(defaults.goodMultiplier, tooFew.goodMultiplier, 0.001);

        Records.SchedulerParameters adjusted = tuner.maybeTune(defaults, new Records.ReviewStats(30, 10, 5, 10, 5, 20, 8), SchedulerTuner.MONTH_MILLIS);
        Records.SchedulerParameters tooSoon = tuner.maybeTune(adjusted, new Records.ReviewStats(40, 20, 5, 10, 5, 20, 8), SchedulerTuner.MONTH_MILLIS + 1);

        assertEquals(adjusted.lastAdjustedAtMillis, tooSoon.lastAdjustedAtMillis);
        assertEquals(adjusted.goodMultiplier, tooSoon.goodMultiplier, 0.001);
    }

    @Test
    public void shortensIntervalsWhenRetentionIsBelowTarget() {
        SchedulerTuner tuner = new SchedulerTuner();
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        Records.ReviewStats weakMonth = new Records.ReviewStats(50, 15, 10, 20, 5, 30, 12);

        Records.SchedulerParameters adjusted = tuner.maybeTune(defaults, weakMonth, SchedulerTuner.MONTH_MILLIS);

        assertTrue(adjusted.goodMultiplier < defaults.goodMultiplier);
        assertTrue(adjusted.easyMultiplier < defaults.easyMultiplier);
        assertEquals(50, adjusted.lastAdjustmentReviewCount);
    }

    @Test
    public void lengthensIntervalsWhenRetentionIsComfortablyAboveTarget() {
        SchedulerTuner tuner = new SchedulerTuner();
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        Records.ReviewStats easyMonth = new Records.ReviewStats(50, 1, 4, 30, 15, 10, 0);

        Records.SchedulerParameters adjusted = tuner.maybeTune(defaults, easyMonth, SchedulerTuner.MONTH_MILLIS);

        assertTrue(adjusted.goodMultiplier > defaults.goodMultiplier);
        assertTrue(adjusted.easyMultiplier > defaults.easyMultiplier);
    }
}
