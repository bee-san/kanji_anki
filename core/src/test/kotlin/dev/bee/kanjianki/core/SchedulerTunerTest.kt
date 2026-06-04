package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTunerTest {
    @Test
    fun nullInputsAndAlreadyCountedReviewsDoNotTune() {
        val tuner = SchedulerTuner()
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        val counted = defaults.withAdjustment(
            defaults.againMultiplier,
            defaults.hardMultiplier,
            defaults.goodMultiplier,
            defaults.easyMultiplier,
            0L,
            30
        )

        val nullCurrent = tuner.maybeTune(null, null, SchedulerTuner.MONTH_MILLIS)
        val alreadyCounted = tuner.maybeTune(counted, RecordsSchedulerModels.ReviewStats(30, 5, 5, 15, 5, 10, 0), SchedulerTuner.MONTH_MILLIS)

        assertEquals(defaults.goodMultiplier, nullCurrent.goodMultiplier, 0.001)
        assertEquals(counted.lastAdjustmentReviewCount, alreadyCounted.lastAdjustmentReviewCount)
        assertEquals(counted.goodMultiplier, alreadyCounted.goodMultiplier, 0.001)
    }

    @Test
    fun waitsForEnoughReviewsAndMonthlyWindow() {
        val tuner = SchedulerTuner()
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()

        val tooFew = tuner.maybeTune(defaults, RecordsSchedulerModels.ReviewStats(10, 4, 2, 3, 1, 5, 2), SchedulerTuner.MONTH_MILLIS)
        assertEquals(defaults.goodMultiplier, tooFew.goodMultiplier, 0.001)

        val adjusted = tuner.maybeTune(defaults, RecordsSchedulerModels.ReviewStats(30, 10, 5, 10, 5, 20, 8), SchedulerTuner.MONTH_MILLIS)
        val tooSoon = tuner.maybeTune(adjusted, RecordsSchedulerModels.ReviewStats(40, 20, 5, 10, 5, 20, 8), SchedulerTuner.MONTH_MILLIS + 1)

        assertEquals(adjusted.lastAdjustedAtMillis, tooSoon.lastAdjustedAtMillis)
        assertEquals(adjusted.goodMultiplier, tooSoon.goodMultiplier, 0.001)
    }

    @Test
    fun shortensIntervalsWhenRetentionIsBelowTarget() {
        val tuner = SchedulerTuner()
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        val weakMonth = RecordsSchedulerModels.ReviewStats(50, 15, 10, 20, 5, 30, 12)

        val adjusted = tuner.maybeTune(defaults, weakMonth, SchedulerTuner.MONTH_MILLIS)

        assertTrue(adjusted.goodMultiplier < defaults.goodMultiplier)
        assertTrue(adjusted.easyMultiplier < defaults.easyMultiplier)
        assertEquals(50, adjusted.lastAdjustmentReviewCount)
    }

    @Test
    fun lengthensIntervalsWhenRetentionIsComfortablyAboveTarget() {
        val tuner = SchedulerTuner()
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        val easyMonth = RecordsSchedulerModels.ReviewStats(50, 0, 5, 30, 15, 10, 0)

        val adjusted = tuner.maybeTune(defaults, easyMonth, SchedulerTuner.MONTH_MILLIS)

        assertTrue(adjusted.goodMultiplier > defaults.goodMultiplier)
        assertTrue(adjusted.easyMultiplier > defaults.easyMultiplier)
    }

    @Test
    fun nearTargetRetentionOnlyAdjustsAgainMultiplier() {
        val tuner = SchedulerTuner()
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        val nearTarget = RecordsSchedulerModels.ReviewStats(50, 4, 3, 35, 8, 10, 0)

        val adjusted = tuner.maybeTune(defaults, nearTarget, SchedulerTuner.MONTH_MILLIS)

        assertEquals(defaults.goodMultiplier, adjusted.goodMultiplier, 0.001)
        assertEquals(defaults.easyMultiplier, adjusted.easyMultiplier, 0.001)
        assertTrue(adjusted.againMultiplier > defaults.againMultiplier)
    }

    @Test
    fun moderateRetentionErrorsUseGentlerSpacingChanges() {
        val tuner = SchedulerTuner()
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        val previouslyAdjusted = defaults.withAdjustment(
            defaults.againMultiplier,
            defaults.hardMultiplier,
            defaults.goodMultiplier,
            defaults.easyMultiplier,
            1L,
            10
        )
        val slightlyWeak = RecordsSchedulerModels.ReviewStats(100, 17, 10, 50, 23, 10, 0)
        val slightlyEasy = RecordsSchedulerModels.ReviewStats(100, 5, 10, 70, 15, 10, 0)

        val weakAdjustment = tuner.maybeTune(defaults, slightlyWeak, SchedulerTuner.MONTH_MILLIS)
        val easyAdjustment = tuner.maybeTune(defaults, slightlyEasy, SchedulerTuner.MONTH_MILLIS)
        val laterAdjustment = tuner.maybeTune(previouslyAdjusted, slightlyWeak, SchedulerTuner.MONTH_MILLIS + 2)

        assertEquals(defaults.goodMultiplier * 0.92, weakAdjustment.goodMultiplier, 0.001)
        assertEquals(defaults.goodMultiplier * 1.06, easyAdjustment.goodMultiplier, 0.001)
        assertEquals(100, laterAdjustment.lastAdjustmentReviewCount)
    }
}
