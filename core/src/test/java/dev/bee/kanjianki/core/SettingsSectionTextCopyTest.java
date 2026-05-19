package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsSectionTextCopyTest {
    @Test
    public void sectionLabelsPreserveFormatting() {
        assertEquals("Anki source", SettingsSectionTextCopy.settingsAnkiSourceTitle());
        assertEquals("What Kani reads from AnkiDroid, and which cards become practice.", SettingsSectionTextCopy.settingsAnkiSourceBody());
        assertEquals("Study behavior", SettingsSectionTextCopy.settingsStudyBehaviorTitle());
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle());
        assertEquals("Reference data", SettingsSectionTextCopy.settingsReferenceDataTitle());
        assertEquals("Settings cockpit", SettingsSectionTextCopy.settingsCockpitLabel());
        assertEquals("Grouped by outcome: source data, study behavior, automation, and offline references. Each setting appears once, next to the thing it changes.", SettingsSectionTextCopy.settingsHeroBody());
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"));
        assertEquals("Collapse Study behavior", SettingsSectionTextCopy.categoryToggleDescription(true, "Study behavior"));
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2));
    }
}
