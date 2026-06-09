package dev.bee.kanjianki.core

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
        assertEquals("Save import filters", SettingsImportFiltersTextCopy.saveImportFiltersLabel())
        assertEquals("Add a search or turn it off.", SettingsImportFiltersTextCopy.browserQueryRequiredToast())
        assertEquals("Turn on at least one source.", SettingsImportFiltersTextCopy.importSourceRequiredToast())
        assertEquals("Saved. Sync to refresh.", SettingsImportFiltersTextCopy.importFiltersSavedToast())
        assertEquals("Presets", SettingsImportFiltersTextCopy.presetsTitle())
        assertEquals("Preset saved. Sync to refresh.", SettingsImportFiltersTextCopy.importPresetSavedToast())
        assertEquals("Use numeric thresholds.", SettingsImportFiltersTextCopy.numericImportThresholdsToast())
        assertEquals("Difficulty 1-10. Lapses 1-100. Cards 1-1000.", SettingsImportFiltersTextCopy.importThresholdRangeToast())
    }
}
