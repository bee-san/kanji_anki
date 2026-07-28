package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MissingKanjiTextCopyTest {
    @Test
    fun englishCopyExplainsScopeFrequencyAndPrivacy() {
        withLocale(Locale.US) {
            assertEquals("Missing Kanji", MissingKanjiTextCopy.actionLabel())
            assertEquals("Scan Anki", MissingKanjiTextCopy.scanAnkiLabel())
            assertTrue(MissingKanjiTextCopy.firstRunBody().contains("every note field"))
            assertTrue(MissingKanjiTextCopy.firstRunBody().contains("never saved"))
            assertTrue(MissingKanjiTextCopy.frequencyBody().contains("Smaller rank numbers"))
            assertEquals("Jiten #2,000", MissingKanjiTextCopy.rankLabel(2_000))
            assertEquals("Unranked", MissingKanjiTextCopy.rankLabel(null))
            assertTrue(MissingKanjiTextCopy.addToKaniConfirmationBody(25, 5).contains("5 new items per day"))
            assertTrue(MissingKanjiTextCopy.kaniAdmissionResultBody(3, 1, 2, 1, 0).contains("3 added"))
            assertEquals("Export to Anki", MissingKanjiTextCopy.exportToAnkiLabel())
            assertTrue(MissingKanjiTextCopy.exportSelectionTitle(25).contains("25"))
            assertTrue(MissingKanjiTextCopy.csvImportInstructions().contains("allow HTML"))
            assertTrue(MissingKanjiTextCopy.csvImportInstructions().contains("skip the header"))
            assertTrue(
                MissingKanjiTextCopy.ankiExportResultBody(
                    deckName = "Kani::Missing Kanji",
                    created = 2,
                    alreadyPresent = 1,
                    skipped = 0,
                    unfinished = 1,
                    failureCode = "provider_unavailable",
                ).contains("will not be duplicated"),
            )
        }
    }

    @Test
    fun japaneseCopyCoversActionsStatesAndAccessibility() {
        withLocale(Locale.JAPAN) {
            assertEquals("未登録漢字", MissingKanjiTextCopy.actionLabel())
            assertEquals("Ankiをスキャン", MissingKanjiTextCopy.scanAnkiLabel())
            assertEquals("アクセスを許可", MissingKanjiTextCopy.grantAccessLabel())
            assertEquals("スキャンをキャンセル", MissingKanjiTextCopy.cancelLabel())
            assertTrue(MissingKanjiTextCopy.firstRunBody().contains("すべてのノート欄"))
            assertTrue(MissingKanjiTextCopy.frequencyBody().contains("数字が小さいほど"))
            assertTrue(MissingKanjiTextCopy.addToKaniConfirmationBody(25, 5).contains("1日最大5件"))
            assertEquals("Ankiへ書き出す", MissingKanjiTextCopy.exportToAnkiLabel())
            assertEquals("CSVを共有", MissingKanjiTextCopy.shareCsvLabel())
            assertTrue(MissingKanjiTextCopy.csvImportInstructions().contains("HTMLを許可"))
            assertTrue(
                MissingKanjiTextCopy.rowDescription(
                    literal = "語",
                    meaning = "language",
                    reading = "ご",
                    rank = 301,
                    selected = true,
                ).contains("詳細を開く"),
            )
        }
    }

    @Test
    fun dynamicCountsAndErrorsUseLocalizedCopy() {
        withLocale(Locale.US) {
            assertEquals("5,000 dictionary kanji in this range", MissingKanjiTextCopy.expectedEligibleCount(5_000))
            assertEquals("Select 42 visible", MissingKanjiTextCopy.selectVisibleLabel(42))
            assertEquals("Scan cancelled", MissingKanjiTextCopy.scanErrorTitle("cancelled"))
            assertTrue(MissingKanjiTextCopy.scanErrorBody("dictionary_unavailable").contains("offline dictionary"))
        }
        withLocale(Locale.JAPAN) {
            assertTrue(MissingKanjiTextCopy.expectedEligibleCount(5_000).contains("5,000"))
            assertEquals("スキャンをキャンセルしました", MissingKanjiTextCopy.scanErrorTitle("cancelled"))
        }
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
