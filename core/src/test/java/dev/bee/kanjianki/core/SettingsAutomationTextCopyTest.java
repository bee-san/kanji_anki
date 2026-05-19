package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsAutomationTextCopyTest {
    @Test
    public void updateHelpersPreserveFormatting() {
        assertEquals("unknown version", SettingsAutomationTextCopy.versionText(null));
        assertEquals("0.4.33", SettingsAutomationTextCopy.versionText("v0.4.33"));
        assertEquals("GitHub updater", SettingsAutomationTextCopy.updatePageTitle());
        assertEquals(
                "Current version 1.2.3. Checks GitHub Releases, verifies the APK, and asks Android to install it.",
                SettingsAutomationTextCopy.updatePageBody("1.2.3")
        );
        assertEquals("Automatic updates", SettingsAutomationTextCopy.automaticUpdatesTitle());
        assertEquals("On: checks about once a day", SettingsAutomationTextCopy.autoUpdatePanelStatus(true));
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("not yet"));
        assertEquals("Last result: none", SettingsAutomationTextCopy.autoUpdateLastResultLine("none"));
        assertEquals("Install permission: Ready", SettingsAutomationTextCopy.installPermissionLine(true));
        assertEquals("Verified APK ready: 0.4.33", SettingsAutomationTextCopy.verifiedApkReadyLine("v0.4.33"));
        assertEquals("Android needs confirmation before Kani can replace itself.", SettingsAutomationTextCopy.pendingUpdateFallback());
        assertEquals("Install verified update", SettingsAutomationTextCopy.installVerifiedUpdateLabel());
        assertEquals("Set up app installs", SettingsAutomationTextCopy.setupAppInstallsLabel());
        assertEquals("Turn off automatic updates", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(true));
        assertEquals("Back to settings", SettingsAutomationTextCopy.backToSettingsLabel());
    }

    @Test
    public void automationHelpersPreserveStatusCopy() {
        assertEquals("Blocked", SettingsAutomationTextCopy.settingsReminderSummary(true, true, "21:05"));
        assertEquals("After first sync", SettingsAutomationTextCopy.settingsAutoSyncSummary(false, true, "07:30"));
        assertEquals("Verified APK ready", SettingsAutomationTextCopy.settingsUpdateSummary(true, false));
        assertEquals("Starts after first successful sync", SettingsAutomationTextCopy.autoSyncStatus(false, true, "07:30"));
        assertEquals("Manual sync once, then Kani will keep itself refreshed once per day.", SettingsAutomationTextCopy.autoSyncDetail(false, true, "", "", ""));
        assertEquals("Daily Anki sync", SettingsAutomationTextCopy.dailyAnkiSyncTitle());
        assertEquals("Turn off daily sync", SettingsAutomationTextCopy.turnOffDailySyncLabel());
        assertEquals("Turn on daily sync", SettingsAutomationTextCopy.turnOnDailySyncLabel());
        assertEquals("App updates", SettingsAutomationTextCopy.appUpdatesTitle());
        assertEquals("Open updater", SettingsAutomationTextCopy.openUpdaterLabel());
        assertEquals("Blocked: notifications off", SettingsAutomationTextCopy.reminderStatus(true, true, "21:05"));
        assertEquals("Daily reminder", SettingsAutomationTextCopy.dailyReminderTitle());
        assertEquals("Morning", SettingsAutomationTextCopy.morningReminderPresetLabel());
        assertEquals("Lunch", SettingsAutomationTextCopy.lunchReminderPresetLabel());
        assertEquals("Evening", SettingsAutomationTextCopy.eveningReminderPresetLabel());
        assertEquals("Night", SettingsAutomationTextCopy.nightReminderPresetLabel());
        assertEquals("Save reminder", SettingsAutomationTextCopy.saveReminderLabel());
        assertEquals("Enable reminder", SettingsAutomationTextCopy.enableReminderLabel());
        assertEquals("Turn off reminder", SettingsAutomationTextCopy.turnOffReminderLabel());
        assertEquals("Android notifications are off for Kani, so this reminder cannot appear yet.", SettingsAutomationTextCopy.notificationsBlockedBody());
        assertEquals("Open notification settings", SettingsAutomationTextCopy.openNotificationSettingsLabel());
        assertEquals("Android will ask for notification permission before turning this on.", SettingsAutomationTextCopy.notificationPermissionBody());
        assertEquals("21:05", SettingsAutomationTextCopy.reminderTime(21, 5));
        assertEquals("Reminder time: 21:05", SettingsAutomationTextCopy.reminderTimeButtonLabel(21, 5));
        assertEquals("Night 21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("Night", 21, 5));
        assertEquals("Minutes (0-1440)", SettingsAutomationTextCopy.studyAheadMinutesLabel());
        assertEquals("0-1440", SettingsAutomationTextCopy.studyAheadMinutesRange());
        assertEquals("1440 minutes (24h)", SettingsAutomationTextCopy.studyAheadMaxDescription());
        assertEquals("Use a whole number of minutes (0-1440).", SettingsAutomationTextCopy.studyAheadWholeNumberErrorText());
        assertEquals("Use 0 to disable, or up to 1440 minutes (24h).", SettingsAutomationTextCopy.studyAheadOutOfRangeErrorText());
    }
}
