package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsImportFiltersTextCopyTest {
    @Test
    public void importFilterHelpersPreserveFormatting() {
        assertEquals("Import filters", SettingsImportFiltersTextCopy.importFiltersTitle());
        assertEquals(
                "Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included.",
                SettingsImportFiltersTextCopy.importFiltersBody()
        );
        assertEquals("Active cards", SettingsImportFiltersTextCopy.activeCardsLabel());
        assertEquals("Suspended cards", SettingsImportFiltersTextCopy.suspendedCardsLabel());
        assertEquals("Tagged cards", SettingsImportFiltersTextCopy.taggedCardsLabel());
        assertEquals("Weak cards", SettingsImportFiltersTextCopy.weakCardsLabel());
        assertEquals("Browser query", SettingsImportFiltersTextCopy.browserQueryLabel());
        assertEquals("deck:Japanese tag:kani", SettingsImportFiltersTextCopy.ankiBrowserQueryHint());
        assertEquals("Anki browser query", SettingsImportFiltersTextCopy.ankiBrowserQueryLabel());
        assertEquals(
                "Import cards matched by this query; Kani still applies note type, rank range, and matching-card threshold.",
                SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText()
        );
        assertEquals("tag1, tag2", SettingsImportFiltersTextCopy.ankiNoteTagsHint());
        assertEquals("Anki note tags", SettingsImportFiltersTextCopy.ankiNoteTagsLabel());
        assertEquals("FSRS difficulty", SettingsImportFiltersTextCopy.fsrsDifficultyLabel());
        assertEquals("Lapses", SettingsImportFiltersTextCopy.lapsesLabel());
        assertEquals("Minimum matching cards per kanji", SettingsImportFiltersTextCopy.minimumMatchingCardsLabel());
        assertEquals("Save import filters", SettingsImportFiltersTextCopy.saveImportFiltersLabel());
        assertEquals("Enter an Anki browser query or turn off Browser query.", SettingsImportFiltersTextCopy.browserQueryRequiredToast());
        assertEquals("Turn on at least one import source.", SettingsImportFiltersTextCopy.importSourceRequiredToast());
        assertEquals("Import filters saved. Sync again to rebuild practice.", SettingsImportFiltersTextCopy.importFiltersSavedToast());
        assertEquals("Presets", SettingsImportFiltersTextCopy.presetsTitle());
        assertEquals("Import preset saved. Sync again to rebuild practice.", SettingsImportFiltersTextCopy.importPresetSavedToast());
        assertEquals("Use numeric import thresholds.", SettingsImportFiltersTextCopy.numericImportThresholdsToast());
        assertEquals("Use difficulty 1-10, lapses 1-100, and cards 1-1000.", SettingsImportFiltersTextCopy.importThresholdRangeToast());
    }
}
