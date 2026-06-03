package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsSummaryTextCopyTest {
    @Test
    fun summaryHelpersPreserveFormatting() {
        assertEquals("3 matching cards per kanji", SettingsSummaryTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)))
        assertEquals("1 matching card per kanji", SettingsSummaryTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)))
        assertEquals("active + suspended + tagged + weak + query; 3 matching cards per kanji", SettingsSummaryTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)))
        assertEquals("No import sources selected", SettingsSummaryTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)))
        assertEquals("Sync blocked: No provider", SettingsSummaryTextCopy.syncStatusHeadline(false, "No provider", 0, 0))
        assertEquals("Sync blocked: unknown error", SettingsSummaryTextCopy.syncStatusHeadline(false, null, 0, 0))
        assertEquals("4 suspended cards archived, 2 rare kanji added; active cards remain optional", SettingsSummaryTextCopy.syncStatusHeadline(true, "ignored", 4, 2))
        assertThrows(NullPointerException::class.java) { SettingsSummaryTextCopy.settingsImportSummary(null) }
        assertThrows(NullPointerException::class.java) { SettingsSummaryTextCopy.matchingCardsSummary(null) }
    }
}

private fun settings(
    importActiveCards: Boolean,
    importSuspendedCards: Boolean,
    importTaggedCards: Boolean,
    importWeakCards: Boolean,
    browserQueryCards: Boolean,
    minMatchingCardsPerKanji: Int,
): RecordsSyncModels.Settings {
    return RecordsSyncModels.Settings(
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
        listOf("leeches"),
        importWeakCards,
        RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
        RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
        minMatchingCardsPerKanji,
        browserQueryCards,
        if (browserQueryCards) "deck:Kiku" else "",
        RecordsBase.DEFAULT_NEW_CARD_SORT_MODE,
        RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
        RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK,
    )
}
