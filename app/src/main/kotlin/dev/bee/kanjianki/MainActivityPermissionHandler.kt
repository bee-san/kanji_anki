package dev.bee.kanjianki

import android.content.pm.PackageManager
import android.widget.Toast
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivityPermissionHandler(private val activity: MainActivityBase) {
    fun requestAnkiPermissionIfNeeded() {
        val status = activity.gateway.status()
        val permission = status.permission
        if (permission != null && !status.permissionGranted) {
            activity.requestPermissions(arrayOf(permission), 7)
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == 7) {
            activity.renderHome()
        } else if (requestCode == MainActivityBase.REQUEST_POST_NOTIFICATIONS) {
            handlePostNotificationPermission(grantResults)
        }
    }

    fun handlePostNotificationPermission(grantResults: IntArray) {
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
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
