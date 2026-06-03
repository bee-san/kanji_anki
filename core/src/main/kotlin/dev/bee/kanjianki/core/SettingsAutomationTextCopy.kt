package dev.bee.kanjianki.core

object SettingsAutomationTextCopy {
    @JvmStatic
    fun settingsReminderSummary(enabled: Boolean, blocked: Boolean, displayTime: String?): String? {
        if (blocked) {
            return "Blocked"
        }
        return if (enabled) displayTime else "Off"
    }

    @JvmStatic
    fun settingsAutoSyncSummary(configured: Boolean, enabled: Boolean, displayTime: String?): String? {
        if (!configured) {
            return "After first sync"
        }
        return if (enabled) displayTime else "Off"
    }

    @JvmStatic
    fun settingsUpdateSummary(hasPendingUpdate: Boolean, enabled: Boolean): String {
        if (hasPendingUpdate) {
            return "Verified APK ready"
        }
        return if (enabled) "Automatic checks on" else "Manual checks"
    }

    @JvmStatic
    fun versionText(version: String?): String {
        if (version == null || version.javaTrim().isEmpty()) {
            return "unknown version"
        }
        return version.replaceFirst("^v".toRegex(), "")
    }

    @JvmStatic
    fun updatePageTitle(): String = "GitHub updater"

    @JvmStatic
    fun updatePageBody(versionName: String?): String {
        return "Version " + versionName.toString() + ". Checks GitHub Releases, then verifies the APK."
    }

    @JvmStatic
    fun automaticUpdatesTitle(): String = "Automatic updates"

    @JvmStatic
    fun checkForUpdateLabel(): String = "Check for update"

    @JvmStatic
    fun autoUpdatePanelStatus(enabled: Boolean): String {
        return if (enabled) "On: checks about once a day" else "Off"
    }

    @JvmStatic
    fun autoUpdateLastCheckLine(lastCheckText: String?): String {
        return "Last check: " + lastCheckText.toString()
    }

    @JvmStatic
    fun autoUpdateLastResultLine(lastResult: String?): String {
        return "Last result: " + lastResult.toString()
    }

    @JvmStatic
    fun installPermissionLine(canInstall: Boolean): String {
        return "Install permission: " + if (canInstall) "Ready" else "Missing"
    }

    @JvmStatic
    fun verifiedApkReadyLine(version: String?): String {
        return "Verified APK ready: " + versionText(version)
    }

    @JvmStatic
    fun pendingUpdateFallback(): String {
        return "Android needs confirmation before Kani can replace itself."
    }

    @JvmStatic
    fun installVerifiedUpdateLabel(): String = "Install verified update"

    @JvmStatic
    fun setupAppInstallsLabel(): String = "Set up app installs"

    @JvmStatic
    fun automaticUpdatesToggleLabel(enabled: Boolean): String {
        return if (enabled) "Turn off automatic updates" else "Turn on automatic updates"
    }

    @JvmStatic
    fun backToSettingsLabel(): String = "Back to settings"

    @JvmStatic
    fun autoSyncStatus(configured: Boolean, enabled: Boolean, displayTime: String?): String {
        if (!configured) {
            return "Starts after first successful sync"
        }
        if (enabled) {
            return "On around $displayTime"
        }
        return "Off"
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
            return "Sync once manually; Kani refreshes daily after that."
        }
        val details = ArrayList<String>()
        addDetail(details, "Last successful sync ", lastSuccessText)
        addDetail(details, "Last sync attempt ", lastAttemptText)
        if (enabled) {
            addDetail(details, "Next sync ", nextRunText)
        }
        if (details.isEmpty()) {
            return if (enabled) {
                "Scheduled daily; Android may batch the time."
            } else {
                "Daily background sync is paused."
            }
        }
        return details.joinToString(". ") + "."
    }

    @JvmStatic
    fun dailyAnkiSyncTitle(): String = "Daily sync"

    @JvmStatic
    fun turnOffDailySyncLabel(): String = "Turn off daily sync"

    @JvmStatic
    fun turnOnDailySyncLabel(): String = "Turn on daily sync"

    @JvmStatic
    fun appUpdatesTitle(): String = "App updates"

    @JvmStatic
    fun openUpdaterLabel(): String = "Open updater"

    @JvmStatic
    fun reminderStatus(enabled: Boolean, blocked: Boolean, displayTime: String?): String {
        if (blocked) {
            return "Blocked: notifications off"
        }
        if (enabled) {
            return "Daily around $displayTime"
        }
        return "Off"
    }

    @JvmStatic
    fun dailyReminderTitle(): String = "Daily reminder"

    @JvmStatic
    fun dailyReminderBody(): String {
        return "Daily nudge for active problem kanji; Android may batch it."
    }

    @JvmStatic
    fun morningReminderPresetLabel(): String = "Morning"

    @JvmStatic
    fun lunchReminderPresetLabel(): String = "Lunch"

    @JvmStatic
    fun eveningReminderPresetLabel(): String = "Evening"

    @JvmStatic
    fun nightReminderPresetLabel(): String = "Night"

    @JvmStatic
    fun saveReminderLabel(): String = "Save reminder"

    @JvmStatic
    fun enableReminderLabel(): String = "Enable reminder"

    @JvmStatic
    fun turnOffReminderLabel(): String = "Turn off reminder"

    @JvmStatic
    fun notificationsBlockedBody(): String {
        return "Android notifications are off, so this reminder cannot appear yet."
    }

    @JvmStatic
    fun openNotificationSettingsLabel(): String = "Open notification settings"

    @JvmStatic
    fun notificationPermissionBody(): String {
        return "Android asks for notification permission before turning this on."
    }

    @JvmStatic
    fun reminderTime(hour: Int, minute: Int): String {
        return TimeOfDaySettingsPolicy.displayTime(hour, minute)
    }

    @JvmStatic
    fun reminderTimeButtonLabel(hour: Int, minute: Int): String {
        return "Reminder time: " + TimeOfDaySettingsPolicy.displayTime(hour, minute)
    }

    @JvmStatic
    fun reminderPresetButtonLabel(label: String?, hour: Int, minute: Int): String {
        return label.toString() + " " + reminderTime(hour, minute)
    }

    private fun addDetail(details: MutableList<String>, prefix: String, value: String?) {
        if (value != null && value.isNotEmpty()) {
            details.add(prefix + value)
        }
    }

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }
}
