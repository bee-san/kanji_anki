package dev.bee.kanjianki.core

import java.util.Locale

object SettingsAutomationTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val AUTO_UPDATE_DEFAULT_LAST_RESULT = "No automatic update check has run yet."

    @JvmStatic
    fun settingsReminderSummary(enabled: Boolean, blocked: Boolean, displayTime: String?): String? {
        if (blocked) {
            return localizedText("Notifications blocked", "通知がブロックされています")
        }
        return if (enabled) displayTime else offText()
    }

    @JvmStatic
    fun settingsAutoSyncSummary(configured: Boolean, enabled: Boolean, displayTime: String?): String? {
        if (!configured) {
            return syncOnceToEnableDailySyncText()
        }
        return if (enabled) displayTime else offText()
    }

    @JvmStatic
    fun settingsUpdateSummary(hasPendingUpdate: Boolean, enabled: Boolean): String {
        if (hasPendingUpdate) {
            return readyToInstallText()
        }
        return if (enabled) dailyChecksEnabledText() else offText()
    }

    @JvmStatic
    fun versionText(version: String?): String {
        if (version == null || version.javaTrim().isEmpty()) {
            return localizedText("unknown version", "不明なバージョン")
        }
        return version.replaceFirst("^v".toRegex(), "")
    }

    @JvmStatic
    fun updatePageTitle(): String = appUpdatesTitle()

    @JvmStatic
    fun updatePageBody(versionName: String?): String {
        val version = versionText(versionName)
        val versionLine = if (version == versionText(null)) {
            localizedText("Version unknown", "バージョン不明")
        } else {
            localizedText("Version $version", "バージョン $version")
        }
        return localizedText("$versionLine. Check for updates.", "$versionLine。更新を確認します。")
    }

    @JvmStatic
    fun automaticUpdatesTitle(): String = localizedText("Automatic updates", "自動更新")

    @JvmStatic
    fun checkForUpdateLabel(): String = localizedText("Check for updates", "更新を確認")

    @JvmStatic
    fun autoUpdatePanelStatus(enabled: Boolean): String {
        return if (enabled) dailyChecksEnabledText() else offText()
    }

    @JvmStatic
    fun autoUpdateLastCheckLine(lastCheckText: String?): String {
        return localizedText("Last check: ", "最終確認: ") + displayValue(lastCheckText, localizedText("not yet", "まだ"))
    }

    @JvmStatic
    fun autoUpdateLastResultLine(lastResult: String?): String {
        return localizedText("Last result: ", "最終結果: ") + displayValue(
            localizedAutoUpdateResultText(lastResult),
            localizedText("not yet", "まだ"),
        )
    }

    @JvmStatic
    fun installPermissionLine(canInstall: Boolean): String {
        return if (canInstall) {
            localizedText("App installs allowed", "アプリのインストールを許可済み")
        } else {
            allowAppInstallsFirstText()
        }
    }

    @JvmStatic
    fun verifiedApkReadyLine(version: String?): String {
        return localizedText("Ready to install: ", "インストール準備完了: ") + versionText(version)
    }

    @JvmStatic
    fun pendingUpdateFallback(): String {
        return localizedText("Choose an update action.", "更新操作を選んでください。")
    }

    @JvmStatic
    fun pendingUpdateFallback(canInstall: Boolean): String {
        return if (canInstall) {
            localizedText("Install verified update first.", "確認済みの更新を先にインストールしてください。")
        } else {
            allowAppInstallsFirstText() + localizedText(".", "。")
        }
    }

    @JvmStatic
    fun installVerifiedUpdateLabel(): String = localizedText("Install verified update", "確認済みの更新をインストール")

    @JvmStatic
    fun setupAppInstallsLabel(): String = localizedText("Allow app installs", "アプリのインストールを許可")

    @JvmStatic
    fun automaticUpdatesToggleLabel(enabled: Boolean): String {
        return if (enabled) localizedText("Turn off updates", "更新をオフにする") else localizedText("Turn on updates", "更新をオンにする")
    }

    @JvmStatic
    fun backToSettingsLabel(): String = localizedText("Back to settings", "設定に戻る")

    @JvmStatic
    fun autoSyncStatus(configured: Boolean, enabled: Boolean, displayTime: String?): String {
        if (!configured) {
            return syncOnceToEnableDailySyncText()
        }
        if (enabled) {
            return timedStatus("On around", "On", "オン", "オン", displayTime)
        }
        return offText()
    }

    @JvmStatic
    fun autoSyncDetail(
        configured: Boolean,
        enabled: Boolean,
        lastSuccessText: String?,
        lastAttemptText: String?,
        nextRunText: String?,
    ): String {
        if (!configured) {
            return syncOnceToEnableDailySyncText()
        }
        val details = ArrayList<String>()
        addDetail(details, localizedText("Last sync: ", "最終同期: "), lastSuccessText)
        addDetail(details, localizedText("Last attempt: ", "最終試行: "), lastAttemptText)
        if (enabled) {
            addDetail(details, localizedText("Next: ", "次回: "), nextRunText)
        }
        if (details.isEmpty()) {
            return if (enabled) {
                localizedText("Scheduled; Android may delay it.", "スケジュール済み。Androidにより遅れることがあります。")
            } else {
                localizedText("Sync paused.", "同期を一時停止中。")
            }
        }
        return details.joinToString(localizedText(". ", "。")) + localizedText(".", "。")
    }

    @JvmStatic
    fun dailyAnkiSyncTitle(): String = localizedText("Daily sync", "毎日同期")

    @JvmStatic
    fun turnOffDailySyncLabel(): String = localizedText("Turn off daily sync", "毎日同期をオフにする")

    @JvmStatic
    fun turnOnDailySyncLabel(): String = localizedText("Turn on daily sync", "毎日同期をオンにする")

    @JvmStatic
    fun appUpdatesTitle(): String = localizedText("App updates", "アプリの更新")

    @JvmStatic
    fun openUpdaterLabel(): String = localizedText("Open updater", "アップデーターを開く")

    @JvmStatic
    fun reminderStatus(enabled: Boolean, blocked: Boolean, displayTime: String?): String {
        if (blocked) {
            return localizedText("Blocked: notifications disabled", "ブロック: 通知が無効")
        }
        if (enabled) {
            return timedStatus("Daily around", "Daily", "毎日", "毎日", displayTime)
        }
        return offText()
    }

    @JvmStatic
    fun dailyReminderTitle(): String = localizedText("Daily reminder", "毎日のリマインダー")

    @JvmStatic
    fun dailyReminderBody(): String {
        return localizedText("Android may delay reminders.", "Androidによりリマインダーが遅れることがあります。")
    }

    @JvmStatic
    fun morningReminderPresetLabel(): String = localizedText("Morning", "朝")

    @JvmStatic
    fun lunchReminderPresetLabel(): String = localizedText("Lunch", "昼")

    @JvmStatic
    fun eveningReminderPresetLabel(): String = localizedText("Evening", "夕方")

    @JvmStatic
    fun nightReminderPresetLabel(): String = localizedText("Night", "夜")

    @JvmStatic
    fun saveReminderLabel(): String = localizedText("Save reminder", "リマインダーを保存")

    @JvmStatic
    fun enableReminderLabel(): String = localizedText("Enable reminder", "リマインダーを有効にする")

    @JvmStatic
    fun turnOffReminderLabel(): String = localizedText("Turn off reminder", "リマインダーをオフにする")

    @JvmStatic
    fun notificationsBlockedBody(): String {
        return localizedText("Turn on reminder notifications.", "リマインダー通知をオンにしてください。")
    }

    @JvmStatic
    fun openNotificationSettingsLabel(): String = localizedText("Open notification settings", "通知設定を開く")

    @JvmStatic
    fun notificationPermissionBody(): String {
        return localizedText("Save to turn on reminders.", "保存するとリマインダーがオンになります。")
    }

    @JvmStatic
    fun reminderTime(hour: Int, minute: Int): String {
        return TimeOfDaySettingsPolicy.displayTime(hour, minute)
    }

    @JvmStatic
    fun reminderTimeButtonLabel(hour: Int, minute: Int): String {
        return localizedText("Reminder time: ", "リマインダー時刻: ") + TimeOfDaySettingsPolicy.displayTime(hour, minute)
    }

    @JvmStatic
    fun reminderPresetButtonLabel(label: String?, hour: Int, minute: Int): String {
        val time = reminderTime(hour, minute)
        val safeLabel = label?.javaTrim()
        if (safeLabel == null || safeLabel.isEmpty()) {
            return time
        }
        return "$safeLabel $time"
    }

    private fun addDetail(details: MutableList<String>, prefix: String, value: String?) {
        if (value != null && value.isNotEmpty()) {
            details.add(prefix + value)
        }
    }

    private fun timedStatus(
        englishPrefix: String,
        englishFallback: String,
        japanesePrefix: String,
        japaneseFallback: String,
        displayTime: String?,
    ): String {
        val time = displayTime?.javaTrim()
        if (time == null || time.isEmpty()) {
            return localizedText(englishFallback, japaneseFallback)
        }
        return if (isJapaneseLocale()) "$japanesePrefix ${time}ごろ" else "$englishPrefix $time"
    }

    private fun displayValue(value: String?, fallback: String): String {
        val text = value?.javaTrim()
        if (text == null || text.isEmpty()) {
            return fallback
        }
        return text
    }

    private fun localizedAutoUpdateResultText(value: String?): String? {
        val text = value?.javaTrim()
        if (text == null || text.isEmpty()) {
            return null
        }
        if (isJapaneseLocale() && text == AUTO_UPDATE_DEFAULT_LAST_RESULT) {
            return null
        }
        return text
    }

    private fun offText(): String = localizedText("Off", "オフ")

    private fun dailyChecksEnabledText(): String = localizedText("Daily checks enabled", "毎日の確認が有効")

    private fun readyToInstallText(): String = localizedText("Ready to install", "インストール準備完了")

    private fun syncOnceToEnableDailySyncText(): String = localizedText("Sync once to enable daily sync.", "毎日同期を有効にするには一度同期してください。")

    private fun allowAppInstallsFirstText(): String = localizedText("Allow app installs first", "先にアプリのインストールを許可してください")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }
}
