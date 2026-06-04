package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAutomationTextCopyTest {
    @Test
    fun updateHelpersPreserveFormatting() {
        assertEquals("unknown version", SettingsAutomationTextCopy.versionText(null))
        assertEquals("0.4.33", SettingsAutomationTextCopy.versionText("v0.4.33"))
        assertEquals("GitHub updater", SettingsAutomationTextCopy.updatePageTitle())
        assertEquals(
            "Version 1.2.3. Checks GitHub Releases, then verifies the APK.",
            SettingsAutomationTextCopy.updatePageBody("1.2.3"),
        )
        assertEquals("Automatic updates", SettingsAutomationTextCopy.automaticUpdatesTitle())
        assertEquals("On: checks about once a day", SettingsAutomationTextCopy.autoUpdatePanelStatus(true))
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("not yet"))
        assertEquals("Last result: none", SettingsAutomationTextCopy.autoUpdateLastResultLine("none"))
        assertEquals("Install permission: Ready", SettingsAutomationTextCopy.installPermissionLine(true))
        assertEquals("Verified APK ready: 0.4.33", SettingsAutomationTextCopy.verifiedApkReadyLine("v0.4.33"))
        assertEquals("Android needs confirmation before Kani can replace itself.", SettingsAutomationTextCopy.pendingUpdateFallback())
        assertEquals("Install verified update", SettingsAutomationTextCopy.installVerifiedUpdateLabel())
        assertEquals("Set up app installs", SettingsAutomationTextCopy.setupAppInstallsLabel())
        assertEquals("Turn off automatic updates", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(true))
        assertEquals("Back to settings", SettingsAutomationTextCopy.backToSettingsLabel())
    }

    @Test
    fun automationHelpersPreserveStatusCopy() {
        assertEquals("Notifications off", SettingsAutomationTextCopy.settingsReminderSummary(true, true, "21:05"))
        assertEquals("After first successful sync", SettingsAutomationTextCopy.settingsAutoSyncSummary(false, true, "07:30"))
        assertEquals("Verified APK ready", SettingsAutomationTextCopy.settingsUpdateSummary(true, false))
        assertEquals("Starts after first successful sync", SettingsAutomationTextCopy.autoSyncStatus(false, true, "07:30"))
        assertEquals("Sync once manually; Kani refreshes daily after that.", SettingsAutomationTextCopy.autoSyncDetail(false, true, "", "", ""))
        assertEquals("Daily sync", SettingsAutomationTextCopy.dailyAnkiSyncTitle())
        assertEquals("Turn off daily sync", SettingsAutomationTextCopy.turnOffDailySyncLabel())
        assertEquals("Turn on daily sync", SettingsAutomationTextCopy.turnOnDailySyncLabel())
        assertEquals("App updates", SettingsAutomationTextCopy.appUpdatesTitle())
        assertEquals("Open updater", SettingsAutomationTextCopy.openUpdaterLabel())
        assertEquals("Blocked: notifications off", SettingsAutomationTextCopy.reminderStatus(true, true, "21:05"))
        assertEquals("Daily reminder", SettingsAutomationTextCopy.dailyReminderTitle())
        assertEquals("Daily reminders and review nudges; Android may batch them.", SettingsAutomationTextCopy.dailyReminderBody())
        assertEquals("Morning", SettingsAutomationTextCopy.morningReminderPresetLabel())
        assertEquals("Lunch", SettingsAutomationTextCopy.lunchReminderPresetLabel())
        assertEquals("Evening", SettingsAutomationTextCopy.eveningReminderPresetLabel())
        assertEquals("Night", SettingsAutomationTextCopy.nightReminderPresetLabel())
        assertEquals("Save reminder", SettingsAutomationTextCopy.saveReminderLabel())
        assertEquals("Enable reminder", SettingsAutomationTextCopy.enableReminderLabel())
        assertEquals("Turn off reminder", SettingsAutomationTextCopy.turnOffReminderLabel())
        assertEquals("Android notifications are off, so this reminder cannot appear yet.", SettingsAutomationTextCopy.notificationsBlockedBody())
        assertEquals("Open notification settings", SettingsAutomationTextCopy.openNotificationSettingsLabel())
        assertEquals("Android asks for notification permission before turning this on.", SettingsAutomationTextCopy.notificationPermissionBody())
        assertEquals("21:05", SettingsAutomationTextCopy.reminderTime(21, 5))
        assertEquals("Reminder time: 21:05", SettingsAutomationTextCopy.reminderTimeButtonLabel(21, 5))
        assertEquals("Night 21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("Night", 21, 5))
    }
}
