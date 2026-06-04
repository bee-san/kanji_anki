package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("AnkiDroid note fields, import filters, frequency range, and daily sync live together.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Deck options", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("Study steps, deck limits, FSRS retention, workload, sorting, ahead limits, and ladder thresholds.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Daily reminders and update checks that run in the background.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Offline dictionaries, stroke data, fonts, and attribution shown by the app.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Settings overview", SettingsSectionTextCopy.settingsCockpitLabel())
        assertEquals("Choose a section below. Expanding it keeps the page in place and preserves your scroll position.", SettingsSectionTextCopy.settingsHeroBody())
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel())
        assertEquals("Kanji frequency range", SettingsSectionTextCopy.importRanksStatusLabel())
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"))
        assertEquals("Daily reminder", SettingsSectionTextCopy.reminderStatusLabel())
        assertEquals("Daily sync", SettingsSectionTextCopy.dailySyncStatusLabel())
        assertEquals("App updates", SettingsSectionTextCopy.updatesStatusLabel())
        assertEquals("Cards per kanji", SettingsSectionTextCopy.matchingCardsStatusLabel())
        assertEquals("Collapse Deck options", SettingsSectionTextCopy.categoryToggleDescription(true, "Deck options"))
        assertEquals("Expand Automation", SettingsSectionTextCopy.categoryToggleDescription(false, "Automation"))
        assertEquals("1 card", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
    }
}
