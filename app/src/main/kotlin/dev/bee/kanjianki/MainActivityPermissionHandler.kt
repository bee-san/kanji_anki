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
        when (activity.currentRoute) {
            MainActivityBase.NAV_HOME_ROUTE -> activity.renderHome()
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsImportSync(true)
        }
    }

    fun handlePostNotificationPermission(granted: Boolean) {
        val pending = activity.pendingReminderSettings
        if (granted) {
            saveGrantedReminderPermission(pending)
        } else {
            preserveReminderAfterDeniedPermission(pending)
        }
        activity.pendingReminderSettings = null
        if (
            activity is MainActivitySettings &&
            activity.currentRoute == MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE
        ) {
            activity.renderSettingsAutomation(true)
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

    fun preserveReminderAfterDeniedPermission(pending: LocalStoreBase.ReminderSettings?) {
        val reminder = pending ?: activity.store.reminderSettings()
        activity.store.saveReminderSettings(reminder)
        ReminderScheduler.schedule(activity, reminder)
        Toast.makeText(activity, ReminderSettingsSavePolicy.permissionDeniedMessage(), Toast.LENGTH_LONG).show()
    }
}
