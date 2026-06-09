package dev.bee.kanjianki.core

object SettingsAutomationTextCopy {
    @JvmStatic
    fun settingsReminderSummary(enabled: Boolean, blocked: Boolean, displayTime: String?): String? {
        if (blocked) {
            return "Notifications blocked"
        }
        return if (enabled) displayTime else "Off"
    }

    @JvmStatic
    fun settingsAutoSyncSummary(configured: Boolean, enabled: Boolean, displayTime: String?): String? {
        if (!configured) {
            return "Sync once to schedule daily syncs."
        }
        return if (enabled) displayTime else "Off"
    }

    @JvmStatic
    fun settingsUpdateSummary(hasPendingUpdate: Boolean, enabled: Boolean): String {
        if (hasPendingUpdate) {
            return "Ready to install"
        }
        return if (enabled) "Daily checks enabled" else "Off"
    }

    @JvmStatic
    fun versionText(version: String?): String {
        if (version == null || version.javaTrim().isEmpty()) {
            return "unknown version"
        }
        return version.replaceFirst("^v".toRegex(), "")
    }

    @JvmStatic
    fun updatePageTitle(): String = "App updates"

    @JvmStatic
    fun updatePageBody(versionName: String?): String {
        val version = versionText(versionName)
        val versionLine = if (version == "unknown version") "Version unknown" else "Version $version"
        return "$versionLine. Check for verified updates."
    }

    @JvmStatic
    fun automaticUpdatesTitle(): String = "Automatic updates"

    @JvmStatic
    fun checkForUpdateLabel(): String = "Check for updates"

    @JvmStatic
    fun autoUpdatePanelStatus(enabled: Boolean): String {
        return if (enabled) "Daily checks enabled" else "Off"
    }

    @JvmStatic
    fun autoUpdateLastCheckLine(lastCheckText: String?): String {
        return "Last check: " + displayValue(lastCheckText, "not yet")
    }

    @JvmStatic
    fun autoUpdateLastResultLine(lastResult: String?): String {
        return "Last result: " + displayValue(lastResult, "not yet")
    }

    @JvmStatic
    fun installPermissionLine(canInstall: Boolean): String {
        return if (canInstall) "App installs allowed" else "App installs need permission"
    }

    @JvmStatic
    fun verifiedApkReadyLine(version: String?): String {
        return "Ready to install: " + versionText(version)
    }

    @JvmStatic
    fun pendingUpdateFallback(): String {
        return "Choose the next update action."
    }

    @JvmStatic
    fun pendingUpdateFallback(canInstall: Boolean): String {
        return if (canInstall) {
            "Install verified update to continue."
        } else {
            "Allow app installs to continue."
        }
    }

    @JvmStatic
    fun installVerifiedUpdateLabel(): String = "Install verified update"

    @JvmStatic
    fun setupAppInstallsLabel(): String = "Allow app installs"

    @JvmStatic
    fun automaticUpdatesToggleLabel(enabled: Boolean): String {
        return if (enabled) "Turn off updates" else "Turn on updates"
    }

    @JvmStatic
    fun backToSettingsLabel(): String = "Back to settings"

    @JvmStatic
    fun autoSyncStatus(configured: Boolean, enabled: Boolean, displayTime: String?): String {
        if (!configured) {
            return "Sync once to schedule daily syncs."
        }
        if (enabled) {
            return timedStatus("On around", "On", displayTime)
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
            return "Sync once to schedule daily syncs."
        }
        val details = ArrayList<String>()
        addDetail(details, "Last sync: ", lastSuccessText)
        addDetail(details, "Last attempt: ", lastAttemptText)
        if (enabled) {
            addDetail(details, "Next: ", nextRunText)
        }
        if (details.isEmpty()) {
            return if (enabled) {
                "Scheduled daily. Android may delay it."
            } else {
                "Daily sync paused."
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
            return "Blocked: notifications disabled"
        }
        if (enabled) {
            return timedStatus("Daily around", "Daily", displayTime)
        }
        return "Off"
    }

    @JvmStatic
    fun dailyReminderTitle(): String = "Daily reminder"

    @JvmStatic
    fun dailyReminderBody(): String {
        return "Android may delay reminders."
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
        return "Turn on notifications for reminders."
    }

    @JvmStatic
    fun openNotificationSettingsLabel(): String = "Open notification settings"

    @JvmStatic
    fun notificationPermissionBody(): String {
        return "Save to allow reminders."
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

    private fun timedStatus(prefix: String, fallback: String, displayTime: String?): String {
        val time = displayTime?.javaTrim()
        if (time == null || time.isEmpty()) {
            return fallback
        }
        return "$prefix $time"
    }

    private fun displayValue(value: String?, fallback: String): String {
        val text = value?.javaTrim()
        if (text == null || text.isEmpty()) {
            return fallback
        }
        return text
    }

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }
}
