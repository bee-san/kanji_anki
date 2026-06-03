package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Import from Anki", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("Choose the note type, import filters, and suspended-card range.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Study settings", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("Card ordering, daily limits, learning steps, retention, study ahead, and ladder rules.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Daily sync, reminder notifications, and update checks.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Reference data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Offline dictionaries, stroke data, fonts, and license attributions.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Settings overview", SettingsSectionTextCopy.settingsCockpitLabel())
        assertEquals("Grouped by what they affect: Anki imports, study settings, automation, and reference data.", SettingsSectionTextCopy.settingsHeroBody())
        assertEquals("Anki note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel())
        assertEquals("Suspended card range", SettingsSectionTextCopy.importRanksStatusLabel())
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"))
        assertEquals("Daily reminder", SettingsSectionTextCopy.reminderStatusLabel())
        assertEquals("Daily Anki sync", SettingsSectionTextCopy.dailySyncStatusLabel())
        assertEquals("App updates", SettingsSectionTextCopy.updatesStatusLabel())
        assertEquals("Matching cards", SettingsSectionTextCopy.matchingCardsStatusLabel())
        assertEquals("Collapse Study settings", SettingsSectionTextCopy.categoryToggleDescription(true, "Study settings"))
        assertEquals("Expand Automation", SettingsSectionTextCopy.categoryToggleDescription(false, "Automation"))
        assertEquals("1 card", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
    }
}
