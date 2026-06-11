package dev.bee.kanjianki

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivitySettingsAutomationReminder(private val activity: MainActivitySettings) {
    fun reminderSettingsPanelModel(): SettingsReminderPanelModel {
        val reminder = activity.store.reminderSettings()
        val notificationsAllowed = activity.notificationsAllowedForReminders()
        val blocked = reminder.enabled && !notificationsAllowed
        val selectedHour = intArrayOf(reminder.hour)
        val selectedMinute = intArrayOf(reminder.minute)
        val warning = reminderWarning(blocked)
        val notificationSettings = notificationSettingsAction(blocked)
        return SettingsReminderPanelModel(
            title = SettingsTextCopy.dailyReminderTitle(),
            status = SettingsTextCopy.reminderStatus(reminder.enabled, blocked, reminder.displayTime()),
            statusColor = reminderStatusColor(reminder.enabled, blocked),
            body = SettingsTextCopy.dailyReminderBody(),
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            presets = reminderPresets(),
            saveLabel = if (reminder.enabled) SettingsTextCopy.saveReminderLabel() else SettingsTextCopy.enableReminderLabel(),
            turnOffLabel = if (reminder.enabled) SettingsTextCopy.turnOffReminderLabel() else null,
            warning = warning,
            notificationSettingsLabel = notificationSettings?.label,
            onPickTime = SettingsReminderTimePickerAction { hour, minute, onSelected ->
                showReminderTimePicker(hour, minute, onSelected)
            },
            onSave = SettingsReminderAction {
                saveReminderFromSelection(selectedHour[0], selectedMinute[0], true)
            },
            onTurnOff = if (reminder.enabled) {
                SettingsReminderAction { disableReminder(reminder) }
            } else {
                null
            },
            onOpenNotificationSettings = notificationSettings?.action
        )
    }

    fun saveReminderFromSelection(hour: Int, minute: Int, enabled: Boolean) {
        val fields = ReminderSettingsSavePolicy.fields(enabled, hour, minute)
        val reminder = LocalStoreBase.ReminderSettings(fields.enabled, fields.hour, fields.minute)
        if (!enabled) {
            disableReminder(reminder)
            return
        }
        ReminderScheduler.ensureNotificationChannel(activity)
        if (!activity.hasRuntimeNotificationPermissionForReminder()) {
            activity.pendingReminderSettings = reminder
            activity.requestPermissions(
                arrayOf(MainActivityBase.PERMISSION_POST_NOTIFICATIONS),
                MainActivityBase.REQUEST_POST_NOTIFICATIONS
            )
            return
        }
        val allowed = activity.notificationsAllowedForReminders()
        saveReminderSettings(
            traceSection = "kani.settings.reminder.save",
            reminder = reminder,
            toastMessage = ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed),
            toastLength = if (allowed) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            onSaved = {
                ReminderScheduler.schedule(activity, reminder)
            },
        )
    }

    private fun disableReminder(reminder: LocalStoreBase.ReminderSettings) {
        val fields = ReminderSettingsSavePolicy.fields(false, reminder.hour, reminder.minute)
        saveReminderSettings(
            traceSection = "kani.settings.reminder.disable",
            reminder = LocalStoreBase.ReminderSettings(fields.enabled, fields.hour, fields.minute),
            toastMessage = ReminderSettingsSavePolicy.disabledMessage(),
            toastLength = Toast.LENGTH_SHORT,
            onSaved = {
                ReminderScheduler.cancel(activity)
            },
        )
    }

    private fun saveReminderSettings(
        traceSection: String,
        reminder: LocalStoreBase.ReminderSettings,
        toastMessage: String,
        toastLength: Int,
        onSaved: () -> Unit,
    ) {
        activity.runSettingsWrite(
            traceSection = traceSection,
            write = {
                activity.store.saveReminderSettings(reminder)
            },
        ) {
            onSaved()
            Toast.makeText(activity, toastMessage, toastLength).show()
            activity.renderSettings(true)
        }
    }

    private fun reminderWarning(blocked: Boolean): String? {
        return when {
            blocked -> SettingsTextCopy.notificationsBlockedBody()
            !activity.hasRuntimeNotificationPermissionForReminder() -> SettingsTextCopy.notificationPermissionBody()
            else -> null
        }
    }

    private fun notificationSettingsAction(blocked: Boolean): NotificationSettingsAction? {
        if (!blocked) {
            return null
        }
        return NotificationSettingsAction(
            label = SettingsTextCopy.openNotificationSettingsLabel(),
            action = SettingsReminderAction { openNotificationSettings() }
        )
    }

    private fun reminderPresets(): List<SettingsReminderPresetModel> {
        return listOf(
            SettingsReminderPresetModel(SettingsTextCopy.morningReminderPresetLabel(), 8, 0),
            SettingsReminderPresetModel(SettingsTextCopy.lunchReminderPresetLabel(), 12, 30),
            SettingsReminderPresetModel(SettingsTextCopy.eveningReminderPresetLabel(), 19, 0),
            SettingsReminderPresetModel(SettingsTextCopy.nightReminderPresetLabel(), 21, 0)
        )
    }

    private fun showReminderTimePicker(
        selectedHour: Int,
        selectedMinute: Int,
        onSelected: SettingsReminderSelectedTimeAction,
    ) {
        TimePickerDialog(
            activity,
            { _, hour, minute -> onSelected.select(hour, minute) },
            selectedHour,
            selectedMinute,
            true
        ).show()
    }

    private fun openNotificationSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        )
    }

    private data class NotificationSettingsAction(
        val label: String,
        val action: SettingsReminderAction,
    )

    private companion object {
        fun reminderStatusColor(enabled: Boolean, blocked: Boolean): Int {
            return when {
                blocked -> MainActivityUiSupport.CORAL
                enabled -> MainActivityUiSupport.TEAL
                else -> MainActivityUiSupport.MUTED
            }
        }
    }
}
