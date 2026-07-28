package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy
import dev.bee.kanjianki.data.LocalStoreBase

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
            MainActivityBase.NAV_HOME_ROUTE -> {
                val home = activity as? MainActivityHome
                val route = home?.currentHomeRouteRestoration
                if (home != null && route != null) {
                    home.renderRestoredHomeRoute(route)
                } else {
                    activity.renderHome()
                }
            }
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsImportSync(true)
        }
    }

    fun handlePostNotificationPermission(granted: Boolean) {
        val reminder = activity.pendingReminderSettings ?: activity.store.reminderSettings()
        showReminderPermissionResult(reminder, granted)
        activity.pendingReminderSettings = null
        if (
            activity is MainActivitySettings &&
            activity.currentRoute == MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE
        ) {
            activity.renderSettingsAutomation(true)
        }
    }

    private fun showReminderPermissionResult(
        reminder: LocalStoreBase.ReminderSettings,
        granted: Boolean,
    ) {
        val allowed = granted && activity.notificationsAllowedForReminders()
        val message = if (granted) {
            ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed)
        } else {
            ReminderSettingsSavePolicy.permissionDeniedMessage()
        }
        Toast.makeText(activity, message, if (allowed) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }
}
