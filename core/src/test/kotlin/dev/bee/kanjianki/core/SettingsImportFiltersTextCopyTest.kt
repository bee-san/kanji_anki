package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsImportFiltersTextCopyTest {
    @Test
    fun importFilterHelpersPreserveFormatting() {
        assertEquals("Import filters", SettingsImportFiltersTextCopy.importFiltersTitle())
        assertEquals(
            "Choose which Anki cards to import. Leeches stay excluded.",
            SettingsImportFiltersTextCopy.importFiltersBody(),
        )
        assertEquals("Include active cards", SettingsImportFiltersTextCopy.activeCardsLabel())
        assertEquals("Include suspended cards", SettingsImportFiltersTextCopy.suspendedCardsLabel())
        assertEquals("Include tagged cards", SettingsImportFiltersTextCopy.taggedCardsLabel())
        assertEquals("Include weak cards", SettingsImportFiltersTextCopy.weakCardsLabel())
        assertEquals("Use browser query", SettingsImportFiltersTextCopy.browserQueryLabel())
        assertEquals("deck:Japanese tag:kani", SettingsImportFiltersTextCopy.ankiBrowserQueryHint())
        assertEquals("Browser query", SettingsImportFiltersTextCopy.ankiBrowserQueryLabel())
        assertEquals(
            "Examples: is:suspended, rated:31:1, tag:kani.",
            SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText(),
        )
        assertEquals("tag1, tag2", SettingsImportFiltersTextCopy.ankiNoteTagsHint())
        assertEquals("Note tags", SettingsImportFiltersTextCopy.ankiNoteTagsLabel())
        assertEquals("Minimum FSRS difficulty", SettingsImportFiltersTextCopy.fsrsDifficultyLabel())
        assertEquals("Minimum lapses", SettingsImportFiltersTextCopy.lapsesLabel())
        assertEquals("Cards per kanji", SettingsImportFiltersTextCopy.minimumMatchingCardsLabel())
        assertEquals("Save import filters", SettingsImportFiltersTextCopy.saveImportFiltersLabel())
        assertEquals("Add a browser query or turn it off.", SettingsImportFiltersTextCopy.browserQueryRequiredToast())
        assertEquals("Turn on at least one source.", SettingsImportFiltersTextCopy.importSourceRequiredToast())
        assertEquals("Filters saved. Sync to refresh practice.", SettingsImportFiltersTextCopy.importFiltersSavedToast())
        assertEquals("Presets", SettingsImportFiltersTextCopy.presetsTitle())
        assertEquals("Preset saved. Sync to refresh practice.", SettingsImportFiltersTextCopy.importPresetSavedToast())
        assertEquals("Enter numeric thresholds.", SettingsImportFiltersTextCopy.numericImportThresholdsToast())
        assertEquals("Difficulty 1-10, lapses 1-100, cards 1-1000.", SettingsImportFiltersTextCopy.importThresholdRangeToast())
    }
}
