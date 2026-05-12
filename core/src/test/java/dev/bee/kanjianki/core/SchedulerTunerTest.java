package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SchedulerTunerTest {
    @Test
    public void nullInputsAndAlreadyCountedReviewsDoNotTune() {
        SchedulerTuner tuner = new SchedulerTuner();
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        Records.SchedulerParameters counted = defaults.withAdjustment(
                defaults.againMultiplier,
                defaults.hardMultiplier,
                defaults.goodMultiplier,
                defaults.easyMultiplier,
                0L,
                30
        );

        Records.SchedulerParameters nullCurrent = tuner.maybeTune(null, null, SchedulerTuner.MONTH_MILLIS);
        Records.SchedulerParameters alreadyCounted = tuner.maybeTune(counted, new Records.ReviewStats(30, 5, 5, 15, 5, 10, 0), SchedulerTuner.MONTH_MILLIS);

        assertEquals(defaults.goodMultiplier, nullCurrent.goodMultiplier, 0.001);
        assertEquals(counted.lastAdjustmentReviewCount, alreadyCounted.lastAdjustmentReviewCount);
        assertEquals(counted.goodMultiplier, alreadyCounted.goodMultiplier, 0.001);
    }

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
        Records.ReviewStats easyMonth = new Records.ReviewStats(50, 0, 5, 30, 15, 10, 0);

        Records.SchedulerParameters adjusted = tuner.maybeTune(defaults, easyMonth, SchedulerTuner.MONTH_MILLIS);

        assertTrue(adjusted.goodMultiplier > defaults.goodMultiplier);
        assertTrue(adjusted.easyMultiplier > defaults.easyMultiplier);
    }

    @Test
    public void nearTargetRetentionOnlyAdjustsAgainMultiplier() {
        SchedulerTuner tuner = new SchedulerTuner();
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        Records.ReviewStats nearTarget = new Records.ReviewStats(50, 4, 3, 35, 8, 10, 0);

        Records.SchedulerParameters adjusted = tuner.maybeTune(defaults, nearTarget, SchedulerTuner.MONTH_MILLIS);

        assertEquals(defaults.goodMultiplier, adjusted.goodMultiplier, 0.001);
        assertEquals(defaults.easyMultiplier, adjusted.easyMultiplier, 0.001);
        assertTrue(adjusted.againMultiplier > defaults.againMultiplier);
    }

    @Test
    public void moderateRetentionErrorsUseGentlerSpacingChanges() {
        SchedulerTuner tuner = new SchedulerTuner();
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        Records.SchedulerParameters previouslyAdjusted = defaults.withAdjustment(
                defaults.againMultiplier,
                defaults.hardMultiplier,
                defaults.goodMultiplier,
                defaults.easyMultiplier,
                1L,
                10
        );
        Records.ReviewStats slightlyWeak = new Records.ReviewStats(100, 17, 10, 50, 23, 10, 0);
        Records.ReviewStats slightlyEasy = new Records.ReviewStats(100, 5, 10, 70, 15, 10, 0);

        Records.SchedulerParameters weakAdjustment = tuner.maybeTune(defaults, slightlyWeak, SchedulerTuner.MONTH_MILLIS);
        Records.SchedulerParameters easyAdjustment = tuner.maybeTune(defaults, slightlyEasy, SchedulerTuner.MONTH_MILLIS);
        Records.SchedulerParameters laterAdjustment = tuner.maybeTune(previouslyAdjusted, slightlyWeak, SchedulerTuner.MONTH_MILLIS + 2);

        assertEquals(defaults.goodMultiplier * 0.92, weakAdjustment.goodMultiplier, 0.001);
        assertEquals(defaults.goodMultiplier * 1.06, easyAdjustment.goodMultiplier, 0.001);
        assertEquals(100, laterAdjustment.lastAdjustmentReviewCount);
    }
}
