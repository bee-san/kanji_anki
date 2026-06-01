package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("AnkiDroid fields, import filters, frequency, and daily sync.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Deck options", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("Study steps, FSRS, workload, sorting, ahead limits, and ladder thresholds.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Advanced controls", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Reminders and update checks that run Kani in the background.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Offline dictionaries, stroke data, fonts, and attribution.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Settings cockpit", SettingsSectionTextCopy.settingsCockpitLabel())
        assertEquals("Grouped by import, deck, automation, and data. Each setting appears once.", SettingsSectionTextCopy.settingsHeroBody())
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel())
        assertEquals("Import ranks", SettingsSectionTextCopy.importRanksStatusLabel())
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"))
        assertEquals("Reminder", SettingsSectionTextCopy.reminderStatusLabel())
        assertEquals("Daily sync", SettingsSectionTextCopy.dailySyncStatusLabel())
        assertEquals("Updates", SettingsSectionTextCopy.updatesStatusLabel())
        assertEquals("Matching cards", SettingsSectionTextCopy.matchingCardsStatusLabel())
        assertEquals("Collapse Study behavior", SettingsSectionTextCopy.categoryToggleDescription(true, "Study behavior"))
        assertEquals("Expand Automation", SettingsSectionTextCopy.categoryToggleDescription(false, "Automation"))
        assertEquals("1 card", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
    }
}
