package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsTextCopyDelegationTest {
    @Test
    public void wrapperDelegatesToExtractedHelpers() {
        RecordsSyncModels.Settings importSettings = settings(true, true, true, true, true, 3);
        assertEquals(
                SettingsSummaryTextCopy.settingsImportSummary(importSettings),
                SettingsTextCopy.settingsImportSummary(importSettings)
        );
        assertEquals(
                SettingsSummaryTextCopy.matchingCardsSummary(importSettings),
                SettingsTextCopy.matchingCardsSummary(importSettings)
        );
        assertEquals(
                SettingsSummaryTextCopy.syncStatusHeadline(true, null, 4, 2),
                SettingsTextCopy.syncStatusHeadline(true, null, 4, 2)
        );
        assertEquals(
            SettingsSummaryTextCopy.syncStatusHeadline(false, "No provider", 0, 0),
            SettingsTextCopy.syncStatusHeadline(false, "No provider", 0, 0)
        );
        assertEquals(
                SettingsAutomationTextCopy.settingsReminderSummary(true, false, "21:05"),
                SettingsTextCopy.settingsReminderSummary(true, false, "21:05")
        );
        assertEquals(
                SettingsAutomationTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow"),
                SettingsTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow")
        );
        assertEquals(SettingsSectionTextCopy.settingsAnkiSourceTitle(), SettingsTextCopy.settingsAnkiSourceTitle());
        assertEquals(SettingsLearningTextCopy.learningStepsTitle(), SettingsTextCopy.learningStepsTitle());
        assertEquals(SettingsImportFiltersTextCopy.importFiltersTitle(), SettingsTextCopy.importFiltersTitle());
        assertEquals(SettingsReferenceDataTextCopy.frequencyRangeTitle(), SettingsTextCopy.frequencyRangeTitle());
        assertEquals(SettingsStudyPlanTextCopy.newCardSortTitle(), SettingsTextCopy.newCardSortTitle());
        assertEquals(SettingsStudyPlanTextCopy.fsrsRetentionTitle(), SettingsTextCopy.fsrsRetentionTitle());
        assertEquals(SettingsStudyPlanTextCopy.studyLadderTitle(), SettingsTextCopy.studyLadderTitle());
        assertEquals(SettingsStudyAheadTextCopy.studyAheadTitle(), SettingsTextCopy.studyAheadTitle());
        assertEquals(SettingsStudyAheadTextCopy.studyAheadBody(), SettingsTextCopy.studyAheadBody());
        assertEquals(SettingsStudyAheadTextCopy.saveStudyAheadLabel(), SettingsTextCopy.saveStudyAheadLabel());
        assertEquals(SettingsStudyAheadTextCopy.studyAheadSavedToast(), SettingsTextCopy.studyAheadSavedToast());
        assertEquals(SettingsLadderThresholdTextCopy.ladderThresholdsTitle(), SettingsTextCopy.ladderThresholdsTitle());
        assertEquals(SettingsLadderThresholdTextCopy.ladderThresholdsBody(), SettingsTextCopy.ladderThresholdsBody());
        assertEquals(SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel(), SettingsTextCopy.fsrsDaysToGoUpLabel());
        assertEquals(SettingsLadderThresholdTextCopy.failsToGoDownLabel(), SettingsTextCopy.failsToGoDownLabel());
        assertEquals(SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel(), SettingsTextCopy.useDefaultLadderThresholdsLabel());
        assertEquals(SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel(), SettingsTextCopy.saveLadderThresholdsLabel());
        assertEquals(SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast(), SettingsTextCopy.ladderThresholdsSavedToast());
        assertEquals(SettingsNoteTypeTextCopy.noteTypeFieldsTitle(), SettingsTextCopy.noteTypeFieldsTitle());
        assertEquals(SettingsNoteTypeTextCopy.noteTypeUsingText("Kiku"), SettingsTextCopy.noteTypeUsingText("Kiku"));
        assertEquals(SettingsNoteTypeTextCopy.noteTypeFieldsBody(), SettingsTextCopy.noteTypeFieldsBody());
        assertEquals(SettingsNoteTypeTextCopy.requiredFieldsTitle(), SettingsTextCopy.requiredFieldsTitle());
        assertEquals(SettingsNoteTypeTextCopy.requiredFieldsBody(), SettingsTextCopy.requiredFieldsBody());
        assertEquals(SettingsNoteTypeTextCopy.expressionFieldLabel(), SettingsTextCopy.expressionFieldLabel());
        assertEquals(SettingsNoteTypeTextCopy.readingFieldLabel(), SettingsTextCopy.readingFieldLabel());
        assertEquals(SettingsNoteTypeTextCopy.meaningFieldLabel(), SettingsTextCopy.meaningFieldLabel());
        assertEquals(SettingsNoteTypeTextCopy.sentenceFieldLabel(), SettingsTextCopy.sentenceFieldLabel());
        assertEquals(SettingsNoteTypeTextCopy.frequencyFieldLabel(), SettingsTextCopy.frequencyFieldLabel());
        assertEquals(SettingsNoteTypeTextCopy.frequencySortFieldLabel(), SettingsTextCopy.frequencySortFieldLabel());
        assertEquals(SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel(), SettingsTextCopy.chooseFromAnkiDroidLabel());
        assertEquals(SettingsNoteTypeTextCopy.useKikuLabel(), SettingsTextCopy.useKikuLabel());
        assertEquals(SettingsNoteTypeTextCopy.saveNoteTypeLabel(), SettingsTextCopy.saveNoteTypeLabel());
        assertEquals(SettingsNoteTypeTextCopy.noteTypeRequiredToast(), SettingsTextCopy.noteTypeRequiredToast());
        assertEquals(SettingsNoteTypeTextCopy.expressionFieldRequiredToast(), SettingsTextCopy.expressionFieldRequiredToast());
        assertEquals(SettingsNoteTypeTextCopy.noteTypeSavedToast(), SettingsTextCopy.noteTypeSavedToast());
    }

    private static RecordsSyncModels.Settings settings(
            boolean importActiveCards,
            boolean importSuspendedCards,
            boolean importTaggedCards,
            boolean importWeakCards,
            boolean browserQueryCards,
            int minMatchingCardsPerKanji
    ) {
        return new RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                2,
                RecordsBase.DEFAULT_SUSPENDED_RANK_MIN,
                RecordsBase.DEFAULT_SUSPENDED_RANK_MAX,
                24,
                3,
                RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS,
                RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES,
                RecordsBase.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE,
                importActiveCards,
                importSuspendedCards,
                importTaggedCards,
                java.util.Arrays.asList("leeches"),
                importWeakCards,
                RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
                RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
                minMatchingCardsPerKanji,
                browserQueryCards,
                browserQueryCards ? "deck:Kiku" : "",
                RecordsBase.DEFAULT_NEW_CARD_SORT_MODE,
                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
        );
    }
}
