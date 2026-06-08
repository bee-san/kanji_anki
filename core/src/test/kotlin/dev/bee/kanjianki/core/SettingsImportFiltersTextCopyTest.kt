package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsImportFiltersTextCopyTest {
    @Test
    fun importFilterHelpersPreserveFormatting() {
        assertEquals("Import filters", SettingsImportFiltersTextCopy.importFiltersTitle())
        assertEquals(
            "Prefer suspended cards. Leech tags stay skipped.",
            SettingsImportFiltersTextCopy.importFiltersBody(),
        )
        assertEquals("Active cards", SettingsImportFiltersTextCopy.activeCardsLabel())
        assertEquals("Suspended cards", SettingsImportFiltersTextCopy.suspendedCardsLabel())
        assertEquals("Tagged cards", SettingsImportFiltersTextCopy.taggedCardsLabel())
        assertEquals("Weak cards", SettingsImportFiltersTextCopy.weakCardsLabel())
        assertEquals("Use browser query", SettingsImportFiltersTextCopy.browserQueryLabel())
        assertEquals("deck:Japanese tag:kani", SettingsImportFiltersTextCopy.ankiBrowserQueryHint())
        assertEquals("Browser query", SettingsImportFiltersTextCopy.ankiBrowserQueryLabel())
        assertEquals(
            "Examples: is:suspended, rated:31:1, tag:kani.",
            SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText(),
        )
        assertEquals("tag1, tag2", SettingsImportFiltersTextCopy.ankiNoteTagsHint())
        assertEquals("Note tags", SettingsImportFiltersTextCopy.ankiNoteTagsLabel())
        assertEquals("FSRS difficulty", SettingsImportFiltersTextCopy.fsrsDifficultyLabel())
        assertEquals("Lapses", SettingsImportFiltersTextCopy.lapsesLabel())
        assertEquals("Min cards per kanji", SettingsImportFiltersTextCopy.minimumMatchingCardsLabel())
        assertEquals("Save filters", SettingsImportFiltersTextCopy.saveImportFiltersLabel())
        assertEquals("Add a query or turn it off.", SettingsImportFiltersTextCopy.browserQueryRequiredToast())
        assertEquals("Enable at least one source.", SettingsImportFiltersTextCopy.importSourceRequiredToast())
        assertEquals("Saved. Sync to refresh practice.", SettingsImportFiltersTextCopy.importFiltersSavedToast())
        assertEquals("Presets", SettingsImportFiltersTextCopy.presetsTitle())
        assertEquals("Preset saved. Sync to refresh practice.", SettingsImportFiltersTextCopy.importPresetSavedToast())
        assertEquals("Use numeric import thresholds.", SettingsImportFiltersTextCopy.numericImportThresholdsToast())
        assertEquals("Difficulty 1-10; lapses 1-100; cards 1-1000.", SettingsImportFiltersTextCopy.importThresholdRangeToast())
    }
}
