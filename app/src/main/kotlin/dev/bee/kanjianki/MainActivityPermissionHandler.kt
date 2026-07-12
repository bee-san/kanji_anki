package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivityPermissionHandler(private val activity: MainActivityBase) {
    fun requestAnkiPermissionIfNeeded() {
        val status = activity.gateway.status()
        val permission = status.permission
        if (permission != null && !status.permissionGranted) {
            activity.launchAnkiDatabasePermission(permission)
        }
    }

    fun handleAnkiPermissionResult() {
        activity.renderHome()
    }

    fun handlePostNotificationPermission(granted: Boolean) {
        val pending = activity.pendingReminderSettings
        if (granted) {
            saveGrantedReminderPermission(pending)
        } else {
            disableReminderAfterDeniedPermission(pending)
        }
        activity.pendingReminderSettings = null
        if (activity is MainActivitySettings) {
            activity.renderSettingsAutomation(true)
        } else {
            activity.renderSettings(true)
        }
    }

    fun saveGrantedReminderPermission(pending: LocalStoreBase.ReminderSettings?) {
        val reminder = pending ?: activity.store.reminderSettings()
        activity.store.saveReminderSettings(reminder)
        ReminderScheduler.schedule(activity, reminder)
        val allowed = activity.notificationsAllowedForReminders()
        Toast.makeText(
            activity,
            ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed),
            if (allowed) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
        ).show()
    }

    fun disableReminderAfterDeniedPermission(pending: LocalStoreBase.ReminderSettings?) {
        val fallback = pending ?: activity.store.reminderSettings()
        val fields = ReminderSettingsSavePolicy.fields(false, fallback.hour, fallback.minute)
        activity.store.saveReminderSettings(
            LocalStoreBase.ReminderSettings(fields.enabled, fields.hour, fields.minute)
        )
        ReminderScheduler.cancel(activity)
        Toast.makeText(activity, ReminderSettingsSavePolicy.permissionDeniedMessage(), Toast.LENGTH_LONG).show()
    }
}
