package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAutomationTextCopyTest {
    @Test
    fun updateHelpersPreserveFormatting() {
        assertEquals("unknown version", SettingsAutomationTextCopy.versionText(null))
        assertEquals("0.4.33", SettingsAutomationTextCopy.versionText("v0.4.33"))
        assertEquals("App updates", SettingsAutomationTextCopy.updatePageTitle())
        assertEquals(
            "Version 1.2.3. Check for updates.",
            SettingsAutomationTextCopy.updatePageBody("1.2.3"),
        )
        assertEquals(
            "Version unknown. Check for updates.",
            SettingsAutomationTextCopy.updatePageBody(null),
        )
        assertEquals("Automatic updates", SettingsAutomationTextCopy.automaticUpdatesTitle())
        assertEquals("Daily checks enabled", SettingsAutomationTextCopy.autoUpdatePanelStatus(true))
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("not yet"))
        assertEquals("Last check: not yet", SettingsAutomationTextCopy.autoUpdateLastCheckLine("  "))
        assertEquals("Last result: none", SettingsAutomationTextCopy.autoUpdateLastResultLine("none"))
        assertEquals("Last result: not yet", SettingsAutomationTextCopy.autoUpdateLastResultLine(null))
        assertEquals(
            "Last result: No automatic update check has run yet.",
            SettingsAutomationTextCopy.autoUpdateLastResultLine("No automatic update check has run yet."),
        )
        assertEquals("App installs allowed", SettingsAutomationTextCopy.installPermissionLine(true))
        assertEquals("Allow app installs first", SettingsAutomationTextCopy.installPermissionLine(false))
        assertEquals("Ready to install: 0.4.33", SettingsAutomationTextCopy.verifiedApkReadyLine("v0.4.33"))
        assertEquals("Choose an update action.", SettingsAutomationTextCopy.pendingUpdateFallback())
        assertEquals("Install verified update first.", SettingsAutomationTextCopy.pendingUpdateFallback(true))
        assertEquals("Allow app installs first.", SettingsAutomationTextCopy.pendingUpdateFallback(false))
        assertEquals("Install verified update", SettingsAutomationTextCopy.installVerifiedUpdateLabel())
        assertEquals("Allow app installs", SettingsAutomationTextCopy.setupAppInstallsLabel())
        assertEquals(
            "Automatically update in the background",
            SettingsAutomationTextCopy.autoUpdateInBackgroundLabel(),
        )
        assertEquals("Turn off updates", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(true))
        assertEquals("Back to settings", SettingsAutomationTextCopy.backToSettingsLabel())
    }

    @Test
    fun automationHelpersPreserveStatusCopy() {
        assertEquals("Notifications blocked", SettingsAutomationTextCopy.settingsReminderSummary(true, true, "21:05"))
        assertEquals("Sync once to enable daily sync.", SettingsAutomationTextCopy.settingsAutoSyncSummary(false, true, "07:30"))
        assertEquals("Ready to install", SettingsAutomationTextCopy.settingsUpdateSummary(true, false))
        assertEquals("Sync once to enable daily sync.", SettingsAutomationTextCopy.autoSyncStatus(false, true, "07:30"))
        assertEquals("On", SettingsAutomationTextCopy.autoSyncStatus(true, true, null))
        assertEquals("Sync once to enable daily sync.", SettingsAutomationTextCopy.autoSyncDetail(false, true, "", "", ""))
        assertEquals("Scheduled; Android may delay it.", SettingsAutomationTextCopy.autoSyncDetail(true, true, "", "", ""))
        assertEquals("Sync paused.", SettingsAutomationTextCopy.autoSyncDetail(true, false, "", "", ""))
        assertEquals("Daily sync", SettingsAutomationTextCopy.dailyAnkiSyncTitle())
        assertEquals("Turn off daily sync", SettingsAutomationTextCopy.turnOffDailySyncLabel())
        assertEquals("Turn on daily sync", SettingsAutomationTextCopy.turnOnDailySyncLabel())
        assertEquals("App updates", SettingsAutomationTextCopy.appUpdatesTitle())
        assertEquals("Open updater", SettingsAutomationTextCopy.openUpdaterLabel())
        assertEquals("Blocked: notifications disabled", SettingsAutomationTextCopy.reminderStatus(true, true, "21:05"))
        assertEquals("Daily", SettingsAutomationTextCopy.reminderStatus(true, false, null))
        assertEquals("Daily reminder", SettingsAutomationTextCopy.dailyReminderTitle())
        assertEquals("Android may delay reminders.", SettingsAutomationTextCopy.dailyReminderBody())
        assertEquals("Morning", SettingsAutomationTextCopy.morningReminderPresetLabel())
        assertEquals("Lunch", SettingsAutomationTextCopy.lunchReminderPresetLabel())
        assertEquals("Evening", SettingsAutomationTextCopy.eveningReminderPresetLabel())
        assertEquals("Night", SettingsAutomationTextCopy.nightReminderPresetLabel())
        assertEquals("Save reminder", SettingsAutomationTextCopy.saveReminderLabel())
        assertEquals("Enable reminder", SettingsAutomationTextCopy.enableReminderLabel())
        assertEquals("Turn off reminder", SettingsAutomationTextCopy.turnOffReminderLabel())
        assertEquals("Turn on reminder notifications.", SettingsAutomationTextCopy.notificationsBlockedBody())
        assertEquals("Open notification settings", SettingsAutomationTextCopy.openNotificationSettingsLabel())
        assertEquals("Save to turn on reminders.", SettingsAutomationTextCopy.notificationPermissionBody())
        assertEquals("21:05", SettingsAutomationTextCopy.reminderTime(21, 5))
        assertEquals("Reminder time: 21:05", SettingsAutomationTextCopy.reminderTimeButtonLabel(21, 5))
        assertEquals("Night 21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("Night", 21, 5))
        assertEquals("21:05", SettingsAutomationTextCopy.reminderPresetButtonLabel("  ", 21, 5))
        assertEquals("Max reminders per day: 2", SettingsAutomationTextCopy.reminderMaxPerDayLabel(2))
        assertEquals("Quiet hours: 22:00–08:00", SettingsAutomationTextCopy.reminderQuietHoursLabel(22 * 60, 8 * 60))
        assertEquals(
            "No reminders during quiet hours; late clusters are pulled earlier.",
            SettingsAutomationTextCopy.reminderQuietHoursBody(),
        )
        assertEquals("Quiet start: 22:00", SettingsAutomationTextCopy.reminderQuietStartButtonLabel(22 * 60))
        assertEquals("Quiet end: 08:00", SettingsAutomationTextCopy.reminderQuietEndButtonLabel(8 * 60))
    }

    @Test
    fun automationHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("不明なバージョン", SettingsAutomationTextCopy.versionText(null))
            assertEquals("0.4.33", SettingsAutomationTextCopy.versionText("v0.4.33"))
            assertEquals("アプリの更新", SettingsAutomationTextCopy.updatePageTitle())
            assertEquals(
                "バージョン 1.2.3。更新を確認します。",
                SettingsAutomationTextCopy.updatePageBody("1.2.3"),
            )
            assertEquals(
                "バージョン不明。更新を確認します。",
                SettingsAutomationTextCopy.updatePageBody(null),
            )
            assertEquals("自動更新", SettingsAutomationTextCopy.automaticUpdatesTitle())
            assertEquals("更新を確認", SettingsAutomationTextCopy.checkForUpdateLabel())
            assertEquals("毎日の確認が有効", SettingsAutomationTextCopy.autoUpdatePanelStatus(true))
            assertEquals("オフ", SettingsAutomationTextCopy.autoUpdatePanelStatus(false))
            assertEquals("最終確認: まだ", SettingsAutomationTextCopy.autoUpdateLastCheckLine(null))
            assertEquals("最終結果: なし", SettingsAutomationTextCopy.autoUpdateLastResultLine("なし"))
            assertEquals("最終結果: まだ", SettingsAutomationTextCopy.autoUpdateLastResultLine("No automatic update check has run yet."))
            assertEquals("アプリのインストールを許可済み", SettingsAutomationTextCopy.installPermissionLine(true))
            assertEquals("先にアプリのインストールを許可してください", SettingsAutomationTextCopy.installPermissionLine(false))
            assertEquals("インストール準備完了: 0.4.33", SettingsAutomationTextCopy.verifiedApkReadyLine("v0.4.33"))
            assertEquals("更新操作を選んでください。", SettingsAutomationTextCopy.pendingUpdateFallback())
            assertEquals("確認済みの更新を先にインストールしてください。", SettingsAutomationTextCopy.pendingUpdateFallback(true))
            assertEquals("先にアプリのインストールを許可してください。", SettingsAutomationTextCopy.pendingUpdateFallback(false))
            assertEquals("確認済みの更新をインストール", SettingsAutomationTextCopy.installVerifiedUpdateLabel())
            assertEquals("アプリのインストールを許可", SettingsAutomationTextCopy.setupAppInstallsLabel())
            assertEquals("バックグラウンドで自動更新", SettingsAutomationTextCopy.autoUpdateInBackgroundLabel())
            assertEquals("更新をオフにする", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(true))
            assertEquals("更新をオンにする", SettingsAutomationTextCopy.automaticUpdatesToggleLabel(false))
            assertEquals("設定に戻る", SettingsAutomationTextCopy.backToSettingsLabel())
            assertEquals("通知がブロックされています", SettingsAutomationTextCopy.settingsReminderSummary(true, true, "21:05"))
            assertEquals("オフ", SettingsAutomationTextCopy.settingsReminderSummary(false, false, "21:05"))
            assertEquals("毎日同期を有効にするには一度同期してください。", SettingsAutomationTextCopy.settingsAutoSyncSummary(false, true, "07:30"))
            assertEquals("インストール準備完了", SettingsAutomationTextCopy.settingsUpdateSummary(true, false))
            assertEquals("毎日同期を有効にするには一度同期してください。", SettingsAutomationTextCopy.autoSyncStatus(false, true, "07:30"))
            assertEquals("オン", SettingsAutomationTextCopy.autoSyncStatus(true, true, null))
            assertEquals("オン 07:30ごろ", SettingsAutomationTextCopy.autoSyncStatus(true, true, "07:30"))
            assertEquals(
                "最終同期: 昨日。最終試行: 今日。次回: 明日。",
                SettingsAutomationTextCopy.autoSyncDetail(true, true, "昨日", "今日", "明日"),
            )
            assertEquals(
                "スケジュール済み。Androidにより遅れることがあります。",
                SettingsAutomationTextCopy.autoSyncDetail(true, true, "", "", ""),
            )
            assertEquals("同期を一時停止中。", SettingsAutomationTextCopy.autoSyncDetail(true, false, "", "", ""))
            assertEquals("毎日同期", SettingsAutomationTextCopy.dailyAnkiSyncTitle())
            assertEquals("毎日同期をオフにする", SettingsAutomationTextCopy.turnOffDailySyncLabel())
            assertEquals("毎日同期をオンにする", SettingsAutomationTextCopy.turnOnDailySyncLabel())
            assertEquals("アプリの更新", SettingsAutomationTextCopy.appUpdatesTitle())
            assertEquals("アップデーターを開く", SettingsAutomationTextCopy.openUpdaterLabel())
            assertEquals("ブロック: 通知が無効", SettingsAutomationTextCopy.reminderStatus(true, true, "21:05"))
            assertEquals("毎日", SettingsAutomationTextCopy.reminderStatus(true, false, null))
            assertEquals("毎日 21:05ごろ", SettingsAutomationTextCopy.reminderStatus(true, false, "21:05"))
            assertEquals("毎日のリマインダー", SettingsAutomationTextCopy.dailyReminderTitle())
            assertEquals("Androidによりリマインダーが遅れることがあります。", SettingsAutomationTextCopy.dailyReminderBody())
            assertEquals("朝", SettingsAutomationTextCopy.morningReminderPresetLabel())
            assertEquals("昼", SettingsAutomationTextCopy.lunchReminderPresetLabel())
            assertEquals("夕方", SettingsAutomationTextCopy.eveningReminderPresetLabel())
            assertEquals("夜", SettingsAutomationTextCopy.nightReminderPresetLabel())
            assertEquals("リマインダーを保存", SettingsAutomationTextCopy.saveReminderLabel())
            assertEquals("リマインダーを有効にする", SettingsAutomationTextCopy.enableReminderLabel())
            assertEquals("リマインダーをオフにする", SettingsAutomationTextCopy.turnOffReminderLabel())
            assertEquals("リマインダー通知をオンにしてください。", SettingsAutomationTextCopy.notificationsBlockedBody())
            assertEquals("通知設定を開く", SettingsAutomationTextCopy.openNotificationSettingsLabel())
            assertEquals("保存するとリマインダーがオンになります。", SettingsAutomationTextCopy.notificationPermissionBody())
            assertEquals("21:05", SettingsAutomationTextCopy.reminderTime(21, 5))
            assertEquals("リマインダー時刻: 21:05", SettingsAutomationTextCopy.reminderTimeButtonLabel(21, 5))
            assertEquals(
                "夜 21:05",
                SettingsAutomationTextCopy.reminderPresetButtonLabel(SettingsAutomationTextCopy.nightReminderPresetLabel(), 21, 5),
            )
            assertEquals("1日の最大リマインダー数: 2", SettingsAutomationTextCopy.reminderMaxPerDayLabel(2))
            assertEquals("サイレント時間: 22:00〜08:00", SettingsAutomationTextCopy.reminderQuietHoursLabel(22 * 60, 8 * 60))
            assertEquals(
                "サイレント時間中はリマインダーを送りません。遅い時間の分は早めにまとめます。",
                SettingsAutomationTextCopy.reminderQuietHoursBody(),
            )
            assertEquals("開始: 22:00", SettingsAutomationTextCopy.reminderQuietStartButtonLabel(22 * 60))
            assertEquals("終了: 08:00", SettingsAutomationTextCopy.reminderQuietEndButtonLabel(8 * 60))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
