package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsImportFiltersTextCopyTest {
    @Test
    fun importFilterHelpersPreserveFormatting() {
        assertEquals("Import filters", SettingsImportFiltersTextCopy.importFiltersTitle())
        assertEquals(
            "Start with suspended cards. Add other sources only when needed; leech tags are skipped.",
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
            "Examples: is:suspended, rated:31:1, tag:kani. Note type, rank, and threshold still apply.",
            SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText(),
        )
        assertEquals("tag1, tag2", SettingsImportFiltersTextCopy.ankiNoteTagsHint())
        assertEquals("Note tags", SettingsImportFiltersTextCopy.ankiNoteTagsLabel())
        assertEquals("FSRS difficulty", SettingsImportFiltersTextCopy.fsrsDifficultyLabel())
        assertEquals("Lapses", SettingsImportFiltersTextCopy.lapsesLabel())
        assertEquals("Minimum matching cards per kanji", SettingsImportFiltersTextCopy.minimumMatchingCardsLabel())
        assertEquals("Save import filters", SettingsImportFiltersTextCopy.saveImportFiltersLabel())
        assertEquals("Enter a browser query or turn it off.", SettingsImportFiltersTextCopy.browserQueryRequiredToast())
        assertEquals("Turn on at least one import source.", SettingsImportFiltersTextCopy.importSourceRequiredToast())
        assertEquals("Filters saved. Sync again to rebuild practice.", SettingsImportFiltersTextCopy.importFiltersSavedToast())
        assertEquals("Presets", SettingsImportFiltersTextCopy.presetsTitle())
        assertEquals("Preset saved. Sync again to rebuild practice.", SettingsImportFiltersTextCopy.importPresetSavedToast())
        assertEquals("Use numeric import thresholds.", SettingsImportFiltersTextCopy.numericImportThresholdsToast())
        assertEquals("Use difficulty 1-10, lapses 1-100, and cards 1-1000.", SettingsImportFiltersTextCopy.importThresholdRangeToast())
    }
}
