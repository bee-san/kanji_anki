package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAutomationTextCopyTest {
    @Test
    fun updateHelpersPreserveFormatting() {
        assertEquals("unknown version", SettingsAutomationTextCopy.versionText(null))
        assertEquals("0.4.33", SettingsAutomationTextCopy.versionText("v0.4.33"))
        assertEquals("App updates", SettingsAutomationTextCopy.updatePageTitle())
        assertEquals(
            "Version 1.2.3. Checks releases and verifies the APK.",
            SettingsAutomationTextCopy.updatePageBody("1.2.3"),
        )
        assertEquals("Automatic updates", SettingsAutomationTextCopy.automaticUpdatesTitle())
        assertEquals("On: daily checks", SettingsAutomationTextCopy.autoUpdatePanelStatus(true))
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("not yet"))
        assertEquals("Last result: none", SettingsAutomationTextCopy.autoUpdateLastResultLine("none"))
        assertEquals("Permission granted", SettingsAutomationTextCopy.installPermissionLine(true))
        assertEquals("Ready to install: 0.4.33", SettingsAutomationTextCopy.verifiedApkReadyLine("v0.4.33"))
        assertEquals("Android needs permission to install updates.", SettingsAutomationTextCopy.pendingUpdateFallback())
        assertEquals("Install verified update", SettingsAutomationTextCopy.installVerifiedUpdateLabel())
        assertEquals("Allow app installs", SettingsAutomationTextCopy.setupAppInstallsLabel())
        assertEquals("Turn off updates", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(true))
        assertEquals("Back to settings", SettingsAutomationTextCopy.backToSettingsLabel())
    }

    @Test
    fun automationHelpersPreserveStatusCopy() {
        assertEquals("Notifications off", SettingsAutomationTextCopy.settingsReminderSummary(true, true, "21:05"))
        assertEquals("After first sync", SettingsAutomationTextCopy.settingsAutoSyncSummary(false, true, "07:30"))
        assertEquals("Ready to install", SettingsAutomationTextCopy.settingsUpdateSummary(true, false))
        assertEquals("Starts after first sync", SettingsAutomationTextCopy.autoSyncStatus(false, true, "07:30"))
        assertEquals("Sync once. Kani handles daily syncs.", SettingsAutomationTextCopy.autoSyncDetail(false, true, "", "", ""))
        assertEquals("Daily sync", SettingsAutomationTextCopy.dailyAnkiSyncTitle())
        assertEquals("Turn off daily sync", SettingsAutomationTextCopy.turnOffDailySyncLabel())
        assertEquals("Turn on daily sync", SettingsAutomationTextCopy.turnOnDailySyncLabel())
        assertEquals("App updates", SettingsAutomationTextCopy.appUpdatesTitle())
        assertEquals("Open updater", SettingsAutomationTextCopy.openUpdaterLabel())
        assertEquals("Blocked: notifications off", SettingsAutomationTextCopy.reminderStatus(true, true, "21:05"))
        assertEquals("Daily reminder", SettingsAutomationTextCopy.dailyReminderTitle())
        assertEquals("Android may delay this reminder.", SettingsAutomationTextCopy.dailyReminderBody())
        assertEquals("Morning", SettingsAutomationTextCopy.morningReminderPresetLabel())
        assertEquals("Lunch", SettingsAutomationTextCopy.lunchReminderPresetLabel())
        assertEquals("Evening", SettingsAutomationTextCopy.eveningReminderPresetLabel())
        assertEquals("Night", SettingsAutomationTextCopy.nightReminderPresetLabel())
        assertEquals("Save reminder", SettingsAutomationTextCopy.saveReminderLabel())
        assertEquals("Enable reminder", SettingsAutomationTextCopy.enableReminderLabel())
        assertEquals("Turn off reminder", SettingsAutomationTextCopy.turnOffReminderLabel())
        assertEquals("Turn on notifications to get this reminder.", SettingsAutomationTextCopy.notificationsBlockedBody())
        assertEquals("Open notification settings", SettingsAutomationTextCopy.openNotificationSettingsLabel())
        assertEquals("Grant notification permission.", SettingsAutomationTextCopy.notificationPermissionBody())
        assertEquals("21:05", SettingsAutomationTextCopy.reminderTime(21, 5))
        assertEquals("Reminder time: 21:05", SettingsAutomationTextCopy.reminderTimeButtonLabel(21, 5))
        assertEquals("Night 21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("Night", 21, 5))
    }
}
