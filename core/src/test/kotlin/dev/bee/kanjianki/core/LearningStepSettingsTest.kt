package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays
import java.util.Collections

class LearningStepSettingsTest {
    @Test
    fun parsesMinuteAndHourSteps() {
        val parsed = RecordsSchedulerModels.LearningStepSettings.tryParseSteps("1m, 10m 1h")

        assertEquals(listOf(1, 10, 60), parsed)
        assertEquals("1m, 10m, 1h", RecordsSchedulerModels.LearningStepSettings.formatSteps(parsed))
    }

    @Test
    fun parsesAndFormatsAnkiStyleDaySteps() {
        val parsed = RecordsSchedulerModels.LearningStepSettings.tryParseSteps("1m 10m 1h 2d")

        assertEquals(listOf(1, 10, 60, 2 * 24 * 60), parsed)
        assertEquals("1m, 10m, 1h, 2d", RecordsSchedulerModels.LearningStepSettings.formatSteps(parsed))
    }

    @Test
    fun rejectsInvalidSteps() {
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("").isEmpty())
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps(null).isEmpty())
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("0m, 10m").isEmpty())
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("h").isEmpty())
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("d").isEmpty())
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("999999999999999999999h").isEmpty())
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("soon").isEmpty())
    }

    @Test
    fun defaultsMatchAnkiStyleLearningAndRelearning() {
        val defaults = RecordsSchedulerModels.LearningStepSettings.defaults()

        assertEquals(listOf(1, 10), defaults.newStepsMinutes)
        assertEquals(listOf(10), defaults.reviewStepsMinutes)
        assertEquals("1m, 10m", defaults.newStepsText())
        assertEquals("10m", defaults.reviewStepsText())
    }

    @Test
    fun constructorAllowsExplicitEmptyRelearningSteps() {
        val settings = RecordsSchedulerModels.LearningStepSettings(
            null,
            emptyList<Int>()
        )

        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertTrue(settings.reviewStepsMinutes.isEmpty())
        assertEquals("", settings.reviewStepsText())
    }

    @Test
    fun constructorUsesDefaultRelearningStepsWhenUnset() {
        val settings = RecordsSchedulerModels.LearningStepSettings(
            null,
            null
        )

        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertEquals(listOf(10), settings.reviewStepsMinutes)
        assertEquals("10m", settings.reviewStepsText())
    }

    @Test
    fun parseStepsFallsBackAndConstructorNormalizesInvalidLists() {
        assertEquals(listOf(5, 15), RecordsSchedulerModels.LearningStepSettings.parseSteps("bad", listOf(5, 15)))
        assertEquals(listOf(1, 10), RecordsSchedulerModels.LearningStepSettings.parseSteps("bad", null))
        assertEquals(listOf(30), RecordsSchedulerModels.LearningStepSettings.parseSteps("30m", listOf(5, 15)))
        assertTrue(RecordsSchedulerModels.LearningStepSettings.parseSteps("", listOf(10), true).isEmpty())
        assertEquals(listOf(10), RecordsSchedulerModels.LearningStepSettings.parseSteps("soon", listOf(10), true))

        val settings = RecordsSchedulerModels.LearningStepSettings(
            listOf(3, null, 9),
            listOf(-1, 7)
        )

        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertEquals(listOf(10), settings.reviewStepsMinutes)
    }

    @Test
    fun syncSettingsNormalizeRankRangeAndWritingTrigger() {
        val legacy = RecordsSyncModels.Settings(
            "Custom",
            "Mining",
            "Front",
            "Reading",
            "Back",
            "Sentence",
            "Frequency",
            "FrequencySort",
            21,
            2,
            2500,
            24,
            3
        )
        val legacyWithTrigger = RecordsSyncModels.Settings(
            "Custom",
            "Mining",
            "Front",
            "Reading",
            "Back",
            "Sentence",
            "Frequency",
            "FrequencySort",
            21,
            2,
            2500,
            24,
            3,
            4
        )
        val settings = RecordsSyncModels.Settings(
            "Custom",
            "Mining",
            "Front",
            "Reading",
            "Back",
            "Sentence",
            "Frequency",
            "FrequencySort",
            21,
            2,
            3000,
            100,
            24,
            3,
            0,
            0
        )

        assertEquals(RecordsBase.DEFAULT_SUSPENDED_RANK_MIN, legacy.suspendedRankMin)
        assertEquals(2500, legacy.suspendedRankMax)
        assertEquals(RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS, legacy.writingTriggerMissDays)
        assertEquals(RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES, legacy.recognitionPromotionPasses)
        assertEquals(4, legacyWithTrigger.writingTriggerMissDays)
        assertEquals(RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES, legacyWithTrigger.recognitionPromotionPasses)
        assertEquals(100, settings.suspendedRankMin)
        assertEquals(3000, settings.suspendedRankMax)
        assertEquals(3000, settings.suspendedRankCutoff)
        assertEquals(1, settings.writingTriggerMissDays)
        assertEquals(1, settings.recognitionPromotionPasses)
    }

    @Test
    fun importFilterSettingsDefaultAndNormalizeInvalidValues() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        assertFalse(defaults.importActiveCards)
        assertTrue(defaults.importSuspendedCards)
        assertFalse(defaults.importTaggedCards)
        assertTrue(defaults.importTags.isEmpty())
        // Weak-card import is on by default (leeches are the highest-value repair
        // targets) with stricter-than-a-single-lapse thresholds.
        assertTrue(defaults.importWeakCards)
        assertEquals(7.5, defaults.importWeakFsrsDifficultyThreshold, 0.001)
        assertEquals(3, defaults.importWeakLapsesThreshold)
        assertEquals(1, defaults.importMinMatchingCardsPerKanji)

        val settings = RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            false,
            false,
            true,
            RecordsBase.parseImportTags("focus, focus weak"),
            true,
            Double.NaN,
            0,
            0
        )

        assertFalse(settings.importActiveCards)
        assertFalse(settings.importSuspendedCards)
        assertTrue(settings.importTaggedCardsEnabled())
        assertEquals(listOf("focus", "weak"), settings.importTags)
        assertTrue(settings.importWeakCards)
        assertEquals(RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, settings.importWeakFsrsDifficultyThreshold, 0.001)
        assertEquals(1, settings.importWeakLapsesThreshold)
        assertEquals(1, settings.importMinMatchingCardsPerKanji)
    }
}
