package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsSectionTextCopyTest {
    @Test
    public void sectionLabelsPreserveFormatting() {
        assertEquals("Anki source", SettingsSectionTextCopy.settingsAnkiSourceTitle());
        assertEquals("What Kani reads from AnkiDroid, and which cards become practice.", SettingsSectionTextCopy.settingsAnkiSourceBody());
        assertEquals("Study behavior", SettingsSectionTextCopy.settingsStudyBehaviorTitle());
        assertEquals("How much appears today, how quickly repeats return, and when cards move rungs.", SettingsSectionTextCopy.settingsStudyBehaviorBody());
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle());
        assertEquals("Background nudges, daily AnkiDroid refreshes, and app update checks.", SettingsSectionTextCopy.settingsAutomationBody());
        assertEquals("Reference data", SettingsSectionTextCopy.settingsReferenceDataTitle());
        assertEquals("Offline dictionaries, frequency ranks, stroke data, fonts, and attribution.", SettingsSectionTextCopy.settingsReferenceDataBody());
        assertEquals("Settings cockpit", SettingsSectionTextCopy.settingsCockpitLabel());
        assertEquals("Source, study, automation, and reference settings. Each setting appears once.", SettingsSectionTextCopy.settingsHeroBody());
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel());
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel());
        assertEquals("Import ranks", SettingsSectionTextCopy.importRanksStatusLabel());
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"));
        assertEquals("Reminder", SettingsSectionTextCopy.reminderStatusLabel());
        assertEquals("Daily sync", SettingsSectionTextCopy.dailySyncStatusLabel());
        assertEquals("Updates", SettingsSectionTextCopy.updatesStatusLabel());
        assertEquals("Matching cards", SettingsSectionTextCopy.matchingCardsStatusLabel());
        assertEquals("Collapse Study behavior", SettingsSectionTextCopy.categoryToggleDescription(true, "Study behavior"));
        assertEquals("Expand Automation", SettingsSectionTextCopy.categoryToggleDescription(false, "Automation"));
        assertEquals("1 card", SettingsSectionTextCopy.settingsCategoryPanelCount(1));
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2));
    }
}
