package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsSectionTextCopyTest {
    @Test
    public void sectionLabelsPreserveFormatting() {
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle());
        assertEquals("AnkiDroid note fields, import filters, frequency range, and sync live together.", SettingsSectionTextCopy.settingsAnkiSourceBody());
        assertEquals("Deck options", SettingsSectionTextCopy.settingsStudyBehaviorTitle());
        assertEquals("Study steps, FSRS retention, workload, sorting, ahead limits, and ladder thresholds.", SettingsSectionTextCopy.settingsStudyBehaviorBody());
        assertEquals("Advanced controls", SettingsSectionTextCopy.settingsAutomationTitle());
        assertEquals("Reminders and app update checks that change how Kani runs in the background.", SettingsSectionTextCopy.settingsAutomationBody());
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle());
        assertEquals("Offline dictionaries, stroke data, fonts, and attribution shown by the app.", SettingsSectionTextCopy.settingsReferenceDataBody());
        assertEquals("Settings cockpit", SettingsSectionTextCopy.settingsCockpitLabel());
        assertEquals("Grouped by import, deck behavior, automation, and reference data. Each setting appears once.", SettingsSectionTextCopy.settingsHeroBody());
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
