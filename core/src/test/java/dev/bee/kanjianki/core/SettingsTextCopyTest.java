package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class SettingsTextCopyTest {
    @Test
    public void importSummariesPreserveSourceAndMatchingCopy() {
        assertEquals("3 matching cards per kanji", SettingsTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)));
        assertEquals("1 matching card per kanji", SettingsTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)));
        assertEquals("active + suspended + tagged + weak + query; 3 matching cards per kanji", SettingsTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)));
        assertEquals("No sources", SettingsTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)));
    }

    @Test
    public void workloadSummariesPreserveSettingsCopy() {
        assertEquals("Pareto: up to 5 items", SettingsTextCopy.workloadStatusText(20, 5));
        assertEquals("All kanji: up to 9 items", SettingsTextCopy.workloadStatusText(100, 9));
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(1));
        assertEquals("Auto Pareto: waiting for problem kanji", SettingsTextCopy.autoWorkloadStatusText(null));
        assertEquals(
                "Auto Pareto: 2 items today",
                SettingsTextCopy.autoWorkloadStatusText(new RecordsSchedulerModels.AdaptiveLoadPlan(true, 20, 2, 1, Arrays.asList("裂", "語"), 0, false, "auto"))
        );
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(0));
    }

    private static RecordsSyncModels.Settings settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            boolean weak,
            boolean query,
            int matchingCards
    ) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
                active,
                suspended,
                tagged,
                tagged ? Collections.singletonList("leeches") : Collections.emptyList(),
                weak,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                matchingCards,
                query,
                query ? "deck:Kiku" : "",
                defaults.newCardSortMode,
                defaults.ladderPromotionIntervalDays,
                defaults.ladderDemotionFailStreak
        );
    }
}
