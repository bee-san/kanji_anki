package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LearningStepSettingsTest {
    @Test
    public void parsesMinuteAndHourSteps() {
        List<Integer> parsed = RecordsSchedulerModels.LearningStepSettings.tryParseSteps("1m, 10m 1h");

        assertEquals(Arrays.asList(1, 10, 60), parsed);
        assertEquals("1m, 10m, 1h", RecordsSchedulerModels.LearningStepSettings.formatSteps(parsed));
    }

    @Test
    public void rejectsInvalidSteps() {
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("").isEmpty());
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps(null).isEmpty());
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("0m, 10m").isEmpty());
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("h").isEmpty());
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("999999999999999999999h").isEmpty());
        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps("soon").isEmpty());
    }

    @Test
    public void defaultsMatchAnkiStyleLearningAndRelearning() {
        RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();

        assertEquals(Arrays.asList(1, 10), defaults.newStepsMinutes);
        assertEquals(Arrays.asList(10), defaults.reviewStepsMinutes);
        assertEquals("1m, 10m", defaults.newStepsText());
        assertEquals("10m", defaults.reviewStepsText());
    }

    @Test
    public void constructorAllowsExplicitEmptyRelearningSteps() {
        RecordsSchedulerModels.LearningStepSettings settings = new RecordsSchedulerModels.LearningStepSettings(
                null,
                Collections.emptyList()
        );

        assertEquals(Arrays.asList(1, 10), settings.newStepsMinutes);
        assertTrue(settings.reviewStepsMinutes.isEmpty());
        assertEquals("", settings.reviewStepsText());
    }

    @Test
    public void constructorUsesDefaultRelearningStepsWhenUnset() {
        RecordsSchedulerModels.LearningStepSettings settings = new RecordsSchedulerModels.LearningStepSettings(
                null,
                null
        );

        assertEquals(Arrays.asList(1, 10), settings.newStepsMinutes);
        assertEquals(Arrays.asList(10), settings.reviewStepsMinutes);
        assertEquals("10m", settings.reviewStepsText());
    }

    @Test
    public void parseStepsFallsBackAndConstructorNormalizesInvalidLists() {
        assertEquals(Arrays.asList(5, 15), RecordsSchedulerModels.LearningStepSettings.parseSteps("bad", Arrays.asList(5, 15)));
        assertEquals(Arrays.asList(1, 10), RecordsSchedulerModels.LearningStepSettings.parseSteps("bad", null));
        assertEquals(Arrays.asList(30), RecordsSchedulerModels.LearningStepSettings.parseSteps("30m", Arrays.asList(5, 15)));

        RecordsSchedulerModels.LearningStepSettings settings = new RecordsSchedulerModels.LearningStepSettings(
                Arrays.asList(3, null, 9),
                Arrays.asList(-1, 7)
        );

        assertEquals(Arrays.asList(1, 10), settings.newStepsMinutes);
        assertEquals(Arrays.asList(10), settings.reviewStepsMinutes);
    }

    @Test
    public void syncSettingsNormalizeRankRangeAndWritingTrigger() {
        RecordsSyncModels.Settings legacy = new RecordsSyncModels.Settings(
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
        );
        RecordsSyncModels.Settings legacyWithTrigger = new RecordsSyncModels.Settings(
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
        );
        RecordsSyncModels.Settings settings = new RecordsSyncModels.Settings(
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
        );

        assertEquals(RecordsBase.DEFAULT_SUSPENDED_RANK_MIN, legacy.suspendedRankMin);
        assertEquals(2500, legacy.suspendedRankMax);
        assertEquals(RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS, legacy.writingTriggerMissDays);
        assertEquals(RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES, legacy.recognitionPromotionPasses);
        assertEquals(4, legacyWithTrigger.writingTriggerMissDays);
        assertEquals(RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES, legacyWithTrigger.recognitionPromotionPasses);
        assertEquals(100, settings.suspendedRankMin);
        assertEquals(3000, settings.suspendedRankMax);
        assertEquals(3000, settings.suspendedRankCutoff);
        assertEquals(1, settings.writingTriggerMissDays);
        assertEquals(1, settings.recognitionPromotionPasses);
    }

    @Test
    public void importFilterSettingsDefaultAndNormalizeInvalidValues() {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();

        assertFalse(defaults.importActiveCards);
        assertTrue(defaults.importSuspendedCards);
        assertFalse(defaults.importTaggedCards);
        assertTrue(defaults.importTags.isEmpty());
        assertFalse(defaults.importWeakCards);
        assertEquals(7.0, defaults.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(2, defaults.importWeakLapsesThreshold);
        assertEquals(1, defaults.importMinMatchingCardsPerKanji);

        RecordsSyncModels.Settings settings = new RecordsSyncModels.Settings(
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
        );

        assertFalse(settings.importActiveCards);
        assertFalse(settings.importSuspendedCards);
        assertTrue(settings.importTaggedCardsEnabled());
        assertEquals(Arrays.asList("focus", "weak"), settings.importTags);
        assertTrue(settings.importWeakCards);
        assertEquals(RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, settings.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(1, settings.importWeakLapsesThreshold);
        assertEquals(1, settings.importMinMatchingCardsPerKanji);
    }
}
