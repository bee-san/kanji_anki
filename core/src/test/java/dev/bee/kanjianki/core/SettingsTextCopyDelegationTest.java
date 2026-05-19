package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsTextCopyDelegationTest {
    @Test
    public void wrapperDelegatesToExtractedHelpers() {
        assertEquals(
                SettingsAutomationTextCopy.settingsReminderSummary(true, false, "21:05"),
                SettingsTextCopy.settingsReminderSummary(true, false, "21:05")
        );
        assertEquals(
                SettingsAutomationTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow"),
                SettingsTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow")
        );
        assertEquals(SettingsSectionTextCopy.settingsAnkiSourceTitle(), SettingsTextCopy.settingsAnkiSourceTitle());
        assertEquals(SettingsLearningTextCopy.learningStepsTitle(), SettingsTextCopy.learningStepsTitle());
        assertEquals(SettingsImportFiltersTextCopy.importFiltersTitle(), SettingsTextCopy.importFiltersTitle());
        assertEquals(SettingsReferenceDataTextCopy.frequencyRangeTitle(), SettingsTextCopy.frequencyRangeTitle());
        assertEquals(SettingsStudyPlanTextCopy.newCardSortTitle(), SettingsTextCopy.newCardSortTitle());
        assertEquals(SettingsStudyPlanTextCopy.fsrsRetentionTitle(), SettingsTextCopy.fsrsRetentionTitle());
        assertEquals(SettingsStudyPlanTextCopy.studyLadderTitle(), SettingsTextCopy.studyLadderTitle());
    }
}
