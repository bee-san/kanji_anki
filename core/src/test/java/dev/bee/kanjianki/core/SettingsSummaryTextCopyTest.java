package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class SettingsSummaryTextCopyTest {
    @Test
    public void summaryHelpersPreserveFormatting() {
        assertEquals("3 matching cards per kanji", SettingsSummaryTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)));
        assertEquals("1 matching card per kanji", SettingsSummaryTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)));
        assertEquals("active + suspended + tagged + weak + query; 3 matching cards per kanji", SettingsSummaryTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)));
        assertEquals("No sources", SettingsSummaryTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)));
        assertEquals("Sync blocked: No provider", SettingsSummaryTextCopy.syncStatusHeadline(false, "No provider", 0, 0));
        assertEquals("Sync blocked: null", SettingsSummaryTextCopy.syncStatusHeadline(false, null, 0, 0));
        assertEquals("4 suspended cards archived, 2 rare kanji added; active cards optional", SettingsSummaryTextCopy.syncStatusHeadline(true, "ignored", 4, 2));
        assertThrows(NullPointerException.class, () -> SettingsSummaryTextCopy.settingsImportSummary(null));
        assertThrows(NullPointerException.class, () -> SettingsSummaryTextCopy.matchingCardsSummary(null));
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
                Arrays.asList("leeches"),
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
