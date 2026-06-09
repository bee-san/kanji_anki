package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class SettingsSummaryTextCopyTest {
    @Test
    fun summaryHelpersPreserveFormatting() {
        assertEquals("3+ cards per kanji", SettingsSummaryTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)))
        assertEquals("1+ card per kanji", SettingsSummaryTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)))
        assertEquals("active + suspended + tagged + weak + browser query; 3+ cards per kanji", SettingsSummaryTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)))
        assertEquals("Choose import sources", SettingsSummaryTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)))
        assertEquals("Sync blocked: No provider", SettingsSummaryTextCopy.syncStatusHeadline(false, "No provider", 0, 0))
        assertEquals("Sync blocked: unknown error", SettingsSummaryTextCopy.syncStatusHeadline(false, null, 0, 0))
        assertEquals("4 suspended cards archived, 2 rare kanji added", SettingsSummaryTextCopy.syncStatusHeadline(true, "ignored", 4, 2))
        assertThrows(NullPointerException::class.java) { SettingsSummaryTextCopy.settingsImportSummary(null) }
        assertThrows(NullPointerException::class.java) { SettingsSummaryTextCopy.matchingCardsSummary(null) }
    }

    @Test
    fun summaryHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("漢字ごとに3枚以上", SettingsSummaryTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)))
            assertEquals("有効＋停止＋タグ付き＋弱い＋ブラウザ検索、漢字ごとに3枚以上", SettingsSummaryTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)))
            assertEquals("インポート元を選択", SettingsSummaryTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)))
            assertEquals("同期ブロック: No provider", SettingsSummaryTextCopy.syncStatusHeadline(false, "No provider", 0, 0))
            assertEquals("同期ブロック: 不明なエラー", SettingsSummaryTextCopy.syncStatusHeadline(false, null, 0, 0))
            assertEquals("4件の停止カードをアーカイブ、2件の珍しい漢字を追加", SettingsSummaryTextCopy.syncStatusHeadline(true, "ignored", 4, 2))
        } finally {
            Locale.setDefault(originalLocale)
        }
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
