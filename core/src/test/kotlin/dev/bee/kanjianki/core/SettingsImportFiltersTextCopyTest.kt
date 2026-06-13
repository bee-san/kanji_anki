package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsImportFiltersTextCopyTest {
    @Test
    fun importFilterHelpersPreserveFormatting() {
        assertEquals("Import filters", SettingsImportFiltersTextCopy.importFiltersTitle())
        assertEquals(
            "Pick sources, save, sync.",
            SettingsImportFiltersTextCopy.importFiltersBody(),
        )
        assertEquals("Active cards", SettingsImportFiltersTextCopy.activeCardsLabel())
        assertEquals("Suspended cards", SettingsImportFiltersTextCopy.suspendedCardsLabel())
        assertEquals("Tagged cards", SettingsImportFiltersTextCopy.taggedCardsLabel())
        assertEquals("Weak cards", SettingsImportFiltersTextCopy.weakCardsLabel())
        assertEquals("Browser query", SettingsImportFiltersTextCopy.browserQueryLabel())
        assertEquals("deck:Japanese tag:kani", SettingsImportFiltersTextCopy.ankiBrowserQueryHint())
        assertEquals("Anki search", SettingsImportFiltersTextCopy.ankiBrowserQueryLabel())
        assertEquals(
            "Try is:suspended or tag:kani.",
            SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText(),
        )
        assertEquals("tag1, tag2", SettingsImportFiltersTextCopy.ankiNoteTagsHint())
        assertEquals("Tags to include", SettingsImportFiltersTextCopy.ankiNoteTagsLabel())
        assertEquals("Minimum FSRS difficulty", SettingsImportFiltersTextCopy.fsrsDifficultyLabel())
        assertEquals("Minimum lapses", SettingsImportFiltersTextCopy.lapsesLabel())
        assertEquals("Matching cards per kanji", SettingsImportFiltersTextCopy.minimumMatchingCardsLabel())
        assertEquals("Save filters", SettingsImportFiltersTextCopy.saveImportFiltersLabel())
        assertEquals("Add a search or turn it off.", SettingsImportFiltersTextCopy.browserQueryRequiredToast())
        assertEquals("Turn on at least one source.", SettingsImportFiltersTextCopy.importSourceRequiredToast())
        assertEquals("Saved. Sync to refresh.", SettingsImportFiltersTextCopy.importFiltersSavedToast())
        assertEquals("Presets", SettingsImportFiltersTextCopy.presetsTitle())
        assertEquals("Preset saved. Sync to refresh.", SettingsImportFiltersTextCopy.importPresetSavedToast())
        assertEquals("Use numeric thresholds.", SettingsImportFiltersTextCopy.numericImportThresholdsToast())
        assertEquals("Difficulty 1-10. Lapses 1-100. Cards 1-1000.", SettingsImportFiltersTextCopy.importThresholdRangeToast())
    }

    @Test
    fun importFilterHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("インポートフィルター", SettingsImportFiltersTextCopy.importFiltersTitle())
            assertEquals(
                "ソースを選んで、保存して、同期する。",
                SettingsImportFiltersTextCopy.importFiltersBody(),
            )
            assertEquals("有効", SettingsImportFiltersTextCopy.activeCardsLabel())
            assertEquals("停止", SettingsImportFiltersTextCopy.suspendedCardsLabel())
            assertEquals("タグ付き", SettingsImportFiltersTextCopy.taggedCardsLabel())
            assertEquals("弱い", SettingsImportFiltersTextCopy.weakCardsLabel())
            assertEquals("ブラウザ検索", SettingsImportFiltersTextCopy.browserQueryLabel())
            assertEquals("deck:Japanese tag:kani", SettingsImportFiltersTextCopy.ankiBrowserQueryHint())
            assertEquals("Anki検索", SettingsImportFiltersTextCopy.ankiBrowserQueryLabel())
            assertEquals(
                "is:suspended や tag:kani を試す。",
                SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText(),
            )
            assertEquals("tag1, tag2", SettingsImportFiltersTextCopy.ankiNoteTagsHint())
            assertEquals("含めるタグ", SettingsImportFiltersTextCopy.ankiNoteTagsLabel())
            assertEquals("最小FSRS難度", SettingsImportFiltersTextCopy.fsrsDifficultyLabel())
            assertEquals("最小失敗数", SettingsImportFiltersTextCopy.lapsesLabel())
            assertEquals("漢字ごとの一致カード数", SettingsImportFiltersTextCopy.minimumMatchingCardsLabel())
            assertEquals("フィルターを保存", SettingsImportFiltersTextCopy.saveImportFiltersLabel())
            assertEquals("検索条件を追加するか、オフにしてください。", SettingsImportFiltersTextCopy.browserQueryRequiredToast())
            assertEquals("少なくとも1つのソースをオンにしてください。", SettingsImportFiltersTextCopy.importSourceRequiredToast())
            assertEquals("保存しました。同期すると更新されます。", SettingsImportFiltersTextCopy.importFiltersSavedToast())
            assertEquals("プリセット", SettingsImportFiltersTextCopy.presetsTitle())
            assertEquals("プリセットを保存しました。同期すると更新されます。", SettingsImportFiltersTextCopy.importPresetSavedToast())
            assertEquals("数値しきい値を使ってください。", SettingsImportFiltersTextCopy.numericImportThresholdsToast())
            assertEquals("難度 1-10。失敗 1-100。カード 1-1000。", SettingsImportFiltersTextCopy.importThresholdRangeToast())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
