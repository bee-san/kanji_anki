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
            "Version 1.2.3. Verified updates only.",
            SettingsAutomationTextCopy.updatePageBody("1.2.3"),
        )
        assertEquals(
            "Version unknown. Verified updates only.",
            SettingsAutomationTextCopy.updatePageBody(null),
        )
        assertEquals("Automatic updates", SettingsAutomationTextCopy.automaticUpdatesTitle())
        assertEquals("On: daily checks", SettingsAutomationTextCopy.autoUpdatePanelStatus(true))
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("not yet"))
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("  "))
        assertEquals("Last result: none", SettingsAutomationTextCopy.autoUpdateLastResultLine("none"))
        assertEquals("Last result: No checks yet", SettingsAutomationTextCopy.autoUpdateLastResultLine(null))
        assertEquals("App installs allowed", SettingsAutomationTextCopy.installPermissionLine(true))
        assertEquals("App installs blocked", SettingsAutomationTextCopy.installPermissionLine(false))
        assertEquals("Ready to install: 0.4.33", SettingsAutomationTextCopy.verifiedApkReadyLine("v0.4.33"))
        assertEquals("Allow app installs to update Kani.", SettingsAutomationTextCopy.pendingUpdateFallback())
        assertEquals("Install verified update", SettingsAutomationTextCopy.installVerifiedUpdateLabel())
        assertEquals("Allow app installs", SettingsAutomationTextCopy.setupAppInstallsLabel())
        assertEquals("Turn off updates", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(true))
        assertEquals("Back to settings", SettingsAutomationTextCopy.backToSettingsLabel())
    }

    @Test
    fun automationHelpersPreserveStatusCopy() {
        assertEquals("Notifications off", SettingsAutomationTextCopy.settingsReminderSummary(true, true, "21:05"))
        assertEquals("Sync once to start", SettingsAutomationTextCopy.settingsAutoSyncSummary(false, true, "07:30"))
        assertEquals("Ready to install", SettingsAutomationTextCopy.settingsUpdateSummary(true, false))
        assertEquals("Sync once to start", SettingsAutomationTextCopy.autoSyncStatus(false, true, "07:30"))
        assertEquals("On", SettingsAutomationTextCopy.autoSyncStatus(true, true, null))
        assertEquals("Sync once to start daily sync.", SettingsAutomationTextCopy.autoSyncDetail(false, true, "", "", ""))
        assertEquals("Daily sync", SettingsAutomationTextCopy.dailyAnkiSyncTitle())
        assertEquals("Turn off daily sync", SettingsAutomationTextCopy.turnOffDailySyncLabel())
        assertEquals("Turn on daily sync", SettingsAutomationTextCopy.turnOnDailySyncLabel())
        assertEquals("App updates", SettingsAutomationTextCopy.appUpdatesTitle())
        assertEquals("Manage updates", SettingsAutomationTextCopy.openUpdaterLabel())
        assertEquals("Notifications off", SettingsAutomationTextCopy.reminderStatus(true, true, "21:05"))
        assertEquals("Daily", SettingsAutomationTextCopy.reminderStatus(true, false, null))
        assertEquals("Daily reminder", SettingsAutomationTextCopy.dailyReminderTitle())
        assertEquals("Choose a time; Android may delay reminders.", SettingsAutomationTextCopy.dailyReminderBody())
        assertEquals("Morning", SettingsAutomationTextCopy.morningReminderPresetLabel())
        assertEquals("Lunch", SettingsAutomationTextCopy.lunchReminderPresetLabel())
        assertEquals("Evening", SettingsAutomationTextCopy.eveningReminderPresetLabel())
        assertEquals("Night", SettingsAutomationTextCopy.nightReminderPresetLabel())
        assertEquals("Save reminder", SettingsAutomationTextCopy.saveReminderLabel())
        assertEquals("Enable reminder", SettingsAutomationTextCopy.enableReminderLabel())
        assertEquals("Turn off reminder", SettingsAutomationTextCopy.turnOffReminderLabel())
        assertEquals("Turn on notifications to get reminders.", SettingsAutomationTextCopy.notificationsBlockedBody())
        assertEquals("Open notification settings", SettingsAutomationTextCopy.openNotificationSettingsLabel())
        assertEquals("Allow notifications to get reminders.", SettingsAutomationTextCopy.notificationPermissionBody())
        assertEquals("21:05", SettingsAutomationTextCopy.reminderTime(21, 5))
        assertEquals("Reminder time: 21:05", SettingsAutomationTextCopy.reminderTimeButtonLabel(21, 5))
        assertEquals("Night 21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("Night", 21, 5))
        assertEquals("21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("  ", 21, 5))
    }
}
