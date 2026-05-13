package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LearningStepSettingsTest {
    @Test
    public void parsesMinuteAndHourSteps() {
        List<Integer> parsed = Records.LearningStepSettings.tryParseSteps("1m, 10m 1h");

        assertEquals(Arrays.asList(1, 10, 60), parsed);
        assertEquals("1m, 10m, 1h", Records.LearningStepSettings.formatSteps(parsed));
    }

    @Test
    public void rejectsInvalidSteps() {
        assertTrue(Records.LearningStepSettings.tryParseSteps("").isEmpty());
        assertTrue(Records.LearningStepSettings.tryParseSteps(null).isEmpty());
        assertTrue(Records.LearningStepSettings.tryParseSteps("0m, 10m").isEmpty());
        assertTrue(Records.LearningStepSettings.tryParseSteps("h").isEmpty());
        assertTrue(Records.LearningStepSettings.tryParseSteps("999999999999999999999h").isEmpty());
        assertTrue(Records.LearningStepSettings.tryParseSteps("soon").isEmpty());
    }

    @Test
    public void defaultsMatchAnkiStyleLearningAndRelearning() {
        Records.LearningStepSettings defaults = Records.LearningStepSettings.defaults();

        assertEquals(Arrays.asList(1, 10), defaults.newStepsMinutes);
        assertEquals(Arrays.asList(10), defaults.reviewStepsMinutes);
        assertEquals("1m, 10m", defaults.newStepsText());
        assertEquals("10m", defaults.reviewStepsText());
    }

    @Test
    public void parseStepsFallsBackAndConstructorNormalizesInvalidLists() {
        assertEquals(Arrays.asList(5, 15), Records.LearningStepSettings.parseSteps("bad", Arrays.asList(5, 15)));
        assertEquals(Arrays.asList(1, 10), Records.LearningStepSettings.parseSteps("bad", null));
        assertEquals(Arrays.asList(30), Records.LearningStepSettings.parseSteps("30m", Arrays.asList(5, 15)));

        Records.LearningStepSettings settings = new Records.LearningStepSettings(
                Arrays.asList(3, null, 9),
                Arrays.asList(-1, 7)
        );

        assertEquals(Arrays.asList(1, 10), settings.newStepsMinutes);
        assertEquals(Arrays.asList(10), settings.reviewStepsMinutes);
    }

    @Test
    public void syncSettingsNormalizeRankRangeAndWritingTrigger() {
        Records.Settings legacy = new Records.Settings(
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
        Records.Settings legacyWithTrigger = new Records.Settings(
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
        Records.Settings settings = new Records.Settings(
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

        assertEquals(Records.DEFAULT_SUSPENDED_RANK_MIN, legacy.suspendedRankMin);
        assertEquals(2500, legacy.suspendedRankMax);
        assertEquals(Records.DEFAULT_WRITING_TRIGGER_MISS_DAYS, legacy.writingTriggerMissDays);
        assertEquals(Records.DEFAULT_RECOGNITION_PROMOTION_PASSES, legacy.recognitionPromotionPasses);
        assertEquals(4, legacyWithTrigger.writingTriggerMissDays);
        assertEquals(Records.DEFAULT_RECOGNITION_PROMOTION_PASSES, legacyWithTrigger.recognitionPromotionPasses);
        assertEquals(100, settings.suspendedRankMin);
        assertEquals(3000, settings.suspendedRankMax);
        assertEquals(3000, settings.suspendedRankCutoff);
        assertEquals(1, settings.writingTriggerMissDays);
        assertEquals(1, settings.recognitionPromotionPasses);
    }

    @Test
    public void importFilterSettingsDefaultAndNormalizeInvalidValues() {
        Records.Settings defaults = Records.Settings.kikuDefaults();

        assertFalse(defaults.importActiveCards);
        assertTrue(defaults.importSuspendedCards);
        assertFalse(defaults.importTaggedCards);
        assertTrue(defaults.importTags.isEmpty());
        assertFalse(defaults.importWeakCards);
        assertEquals(7.0, defaults.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(2, defaults.importWeakLapsesThreshold);
        assertEquals(1, defaults.importMinMatchingCardsPerKanji);

        Records.Settings settings = new Records.Settings(
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
                Records.parseImportTags("focus, focus weak"),
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
        assertEquals(Records.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, settings.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(1, settings.importWeakLapsesThreshold);
        assertEquals(1, settings.importMinMatchingCardsPerKanji);
    }
}
