package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("Choose which AnkiDroid cards Kani imports and when sync runs.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Deck options", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("Learning steps, deck limits, retention, workload, sorting, and ladder movement.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Reminders & updates", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Daily reminders, daily sync, and app updates.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Offline dictionaries, stroke data, fonts, and attribution.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Settings overview", SettingsSectionTextCopy.settingsCockpitLabel())
        assertEquals("Choose a section below. Expanding it keeps the page in place and preserves your scroll position.", SettingsSectionTextCopy.settingsHeroBody())
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel())
        assertEquals("Suspended card range", SettingsSectionTextCopy.importRanksStatusLabel())
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"))
        assertEquals("Daily reminder", SettingsSectionTextCopy.reminderStatusLabel())
        assertEquals("Daily sync", SettingsSectionTextCopy.dailySyncStatusLabel())
        assertEquals("App updates", SettingsSectionTextCopy.updatesStatusLabel())
        assertEquals("Cards per kanji", SettingsSectionTextCopy.matchingCardsStatusLabel())
        assertEquals("Collapse Deck options", SettingsSectionTextCopy.categoryToggleDescription(true, "Deck options"))
        assertEquals("Expand Reminders & updates", SettingsSectionTextCopy.categoryToggleDescription(false, "Reminders & updates"))
        assertEquals("Expanded", SettingsSectionTextCopy.categoryStateDescription(true))
        assertEquals("Collapsed", SettingsSectionTextCopy.categoryStateDescription(false))
        assertEquals("1 setting", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
        assertEquals("2 settings", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
    }
}
