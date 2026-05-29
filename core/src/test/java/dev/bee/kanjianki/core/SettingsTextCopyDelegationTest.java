package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class SettingsTextCopyDelegationTest {
    @Test
    public void wrapperDelegatesToExtractedHelpers() {
        RecordsSyncModels.Settings importSettings = settings(true, true, true, true, true, 3);
        assertEquals(
                Arrays.asList(
                        SettingsSummaryTextCopy.settingsImportSummary(importSettings),
                        SettingsSummaryTextCopy.matchingCardsSummary(importSettings),
                        SettingsSummaryTextCopy.syncStatusHeadline(true, null, 4, 2),
                        SettingsSummaryTextCopy.syncStatusHeadline(false, "No provider", 0, 0),
                        SettingsAutomationTextCopy.settingsReminderSummary(true, false, "21:05"),
                        SettingsAutomationTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow")
                ),
                Arrays.asList(
                        SettingsTextCopy.settingsImportSummary(importSettings),
                        SettingsTextCopy.matchingCardsSummary(importSettings),
                        SettingsTextCopy.syncStatusHeadline(true, null, 4, 2),
                        SettingsTextCopy.syncStatusHeadline(false, "No provider", 0, 0),
                        SettingsTextCopy.settingsReminderSummary(true, false, "21:05"),
                        SettingsTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow")
                )
        );
        assertEquals(
                Arrays.asList(
                        SettingsSectionTextCopy.settingsAnkiSourceTitle(),
                        SettingsLearningTextCopy.learningStepsTitle(),
                        SettingsImportFiltersTextCopy.importFiltersTitle(),
                        SettingsReferenceDataTextCopy.frequencyRangeTitle(),
                        SettingsStudyPlanTextCopy.newCardSortTitle(),
                        SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(Arrays.asList("人/入")),
                        SettingsStudyPlanTextCopy.deckLimitsTitle(),
                        SettingsStudyPlanTextCopy.deckLimitsBody(),
                        SettingsStudyPlanTextCopy.newCardsPerDayLabel(),
                        SettingsStudyPlanTextCopy.saveDeckLimitsLabel(),
                        SettingsStudyPlanTextCopy.fsrsRetentionTitle(),
                        SettingsStudyPlanTextCopy.studyLadderTitle(),
                        SettingsStudyAheadTextCopy.studyAheadTitle(),
                        SettingsStudyAheadTextCopy.studyAheadBody(),
                        SettingsStudyAheadTextCopy.saveStudyAheadLabel(),
                        SettingsStudyAheadTextCopy.studyAheadSavedToast(),
                        SettingsStudyAheadTextCopy.studyAheadMinutesLabel(),
                        SettingsStudyAheadTextCopy.studyAheadMinutesRange(),
                        SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText(),
                        SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
                        SettingsStudyAheadTextCopy.studyAheadMaxDescription(),
                        SettingsLadderThresholdTextCopy.ladderThresholdsTitle(),
                        SettingsLadderThresholdTextCopy.ladderThresholdsBody(),
                        SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel(),
                        SettingsLadderThresholdTextCopy.failsToGoDownLabel()
                ),
                Arrays.asList(
                        SettingsTextCopy.settingsAnkiSourceTitle(),
                        SettingsTextCopy.learningStepsTitle(),
                        SettingsTextCopy.importFiltersTitle(),
                        SettingsTextCopy.frequencyRangeTitle(),
                        SettingsTextCopy.newCardSortTitle(),
                        SettingsTextCopy.newCardSortConfusablePreviewWarning(Arrays.asList("人/入")),
                        SettingsTextCopy.deckLimitsTitle(),
                        SettingsTextCopy.deckLimitsBody(),
                        SettingsTextCopy.newCardsPerDayLabel(),
                        SettingsTextCopy.saveDeckLimitsLabel(),
                        SettingsTextCopy.fsrsRetentionTitle(),
                        SettingsTextCopy.studyLadderTitle(),
                        SettingsTextCopy.studyAheadTitle(),
                        SettingsTextCopy.studyAheadBody(),
                        SettingsTextCopy.saveStudyAheadLabel(),
                        SettingsTextCopy.studyAheadSavedToast(),
                        SettingsTextCopy.studyAheadMinutesLabel(),
                        SettingsTextCopy.studyAheadMinutesRange(),
                        SettingsTextCopy.studyAheadWholeNumberErrorText(),
                        SettingsTextCopy.studyAheadOutOfRangeErrorText(),
                        SettingsTextCopy.studyAheadMaxDescription(),
                        SettingsTextCopy.ladderThresholdsTitle(),
                        SettingsTextCopy.ladderThresholdsBody(),
                        SettingsTextCopy.fsrsDaysToGoUpLabel(),
                        SettingsTextCopy.failsToGoDownLabel()
                )
        );
        assertEquals(
                Arrays.asList(
                        SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel(),
                        SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel(),
                        SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast(),
                        SettingsNoteTypeTextCopy.noteTypeFieldsTitle(),
                        SettingsNoteTypeTextCopy.noteTypeUsingText("Kiku"),
                        SettingsNoteTypeTextCopy.noteTypeFieldsBody(),
                        SettingsNoteTypeTextCopy.requiredFieldsTitle(),
                        SettingsNoteTypeTextCopy.requiredFieldsBody(),
                        SettingsNoteTypeTextCopy.expressionFieldLabel(),
                        SettingsNoteTypeTextCopy.readingFieldLabel(),
                        SettingsNoteTypeTextCopy.meaningFieldLabel(),
                        SettingsNoteTypeTextCopy.sentenceFieldLabel(),
                        SettingsNoteTypeTextCopy.frequencyFieldLabel(),
                        SettingsNoteTypeTextCopy.frequencySortFieldLabel(),
                        SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel(),
                        SettingsNoteTypeTextCopy.useKikuLabel(),
                        SettingsNoteTypeTextCopy.saveNoteTypeLabel(),
                        SettingsNoteTypeTextCopy.noteTypeRequiredToast(),
                        SettingsNoteTypeTextCopy.expressionFieldRequiredToast(),
                        SettingsNoteTypeTextCopy.noteTypeSavedToast()
                ),
                Arrays.asList(
                        SettingsTextCopy.useDefaultLadderThresholdsLabel(),
                        SettingsTextCopy.saveLadderThresholdsLabel(),
                        SettingsTextCopy.ladderThresholdsSavedToast(),
                        SettingsTextCopy.noteTypeFieldsTitle(),
                        SettingsTextCopy.noteTypeUsingText("Kiku"),
                        SettingsTextCopy.noteTypeFieldsBody(),
                        SettingsTextCopy.requiredFieldsTitle(),
                        SettingsTextCopy.requiredFieldsBody(),
                        SettingsTextCopy.expressionFieldLabel(),
                        SettingsTextCopy.readingFieldLabel(),
                        SettingsTextCopy.meaningFieldLabel(),
                        SettingsTextCopy.sentenceFieldLabel(),
                        SettingsTextCopy.frequencyFieldLabel(),
                        SettingsTextCopy.frequencySortFieldLabel(),
                        SettingsTextCopy.chooseFromAnkiDroidLabel(),
                        SettingsTextCopy.useKikuLabel(),
                        SettingsTextCopy.saveNoteTypeLabel(),
                        SettingsTextCopy.noteTypeRequiredToast(),
                        SettingsTextCopy.expressionFieldRequiredToast(),
                        SettingsTextCopy.noteTypeSavedToast()
                )
        );
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
