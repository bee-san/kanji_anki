package dev.bee.kanjianki

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.notifications.AndroidNotificationGateway
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivitySettingsAutomationReminder(private val activity: MainActivitySettings) {
    fun reminderSettingsPanelModel(
        state: SettingsDeviceState = activity.loadSettingsDeviceState(),
    ): SettingsReminderPanelModel {
        val reminder = state.reminder
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
            onOpenNotificationSettings = notificationSettings?.action,
            antiSpam = if (reminder.enabled) {
                antiSpamModel(state.reminderAntiSpam)
            } else {
                null
            },
        )
    }

    private fun antiSpamModel(
        settings: SettingsReminderAntiSpamState,
    ): SettingsReminderAntiSpamModel {
        return SettingsReminderAntiSpamModel(
            quietHoursLabel = SettingsTextCopy.reminderQuietHoursLabel(
                settings.quietStartMinuteOfDay,
                settings.quietEndMinuteOfDay,
            ),
            quietHoursBody = SettingsTextCopy.reminderQuietHoursBody(),
            quietStartLabel = SettingsTextCopy.reminderQuietStartButtonLabel(settings.quietStartMinuteOfDay),
            quietEndLabel = SettingsTextCopy.reminderQuietEndButtonLabel(settings.quietEndMinuteOfDay),
            maxPerDayLabel = SettingsTextCopy.reminderMaxPerDayLabel(settings.maxRemindersPerDay),
            onPickQuietStart = SettingsReminderAction {
                pickQuietHour(settings.quietStartMinuteOfDay) { minuteOfDay ->
                    saveAntiSpam(settings.copy(quietStartMinuteOfDay = minuteOfDay))
                }
            },
            onPickQuietEnd = SettingsReminderAction {
                pickQuietHour(settings.quietEndMinuteOfDay) { minuteOfDay ->
                    saveAntiSpam(settings.copy(quietEndMinuteOfDay = minuteOfDay))
                }
            },
            onDecreaseMaxPerDay = SettingsReminderAction {
                saveAntiSpam(settings.copy(maxRemindersPerDay = settings.maxRemindersPerDay - 1))
            },
            onIncreaseMaxPerDay = SettingsReminderAction {
                saveAntiSpam(settings.copy(maxRemindersPerDay = settings.maxRemindersPerDay + 1))
            },
        )
    }

    private fun pickQuietHour(currentMinuteOfDay: Int, onSelected: (Int) -> Unit) {
        showReminderTimePicker(currentMinuteOfDay / 60, currentMinuteOfDay % 60) { hour, minute ->
            onSelected(hour * 60 + minute)
        }
    }

    private fun saveAntiSpam(settings: SettingsReminderAntiSpamState) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.reminder.anti-spam.save",
            write = {
                activity.deviceSettingsStore.saveReminderAntiSpam(settings)
            },
        ) {
            // Re-arm so quiet hours / max-per-day take effect immediately.
            ReminderScheduler.schedule(activity)
            activity.renderSettingsAutomation(true)
        }
    }

    fun saveReminderFromSelection(hour: Int, minute: Int, enabled: Boolean) {
        val fields = ReminderSettingsSavePolicy.fields(enabled, hour, minute)
        val reminder = SettingsReminderState(fields.enabled, fields.hour, fields.minute)
        if (!enabled) {
            disableReminder(reminder)
            return
        }
        ReminderScheduler.ensureNotificationChannel(activity)
        if (!activity.hasRuntimeNotificationPermissionForReminder()) {
            activity.setPendingReminderSettings(reminder)
            activity.runSettingsWrite(
                traceSection = "kani.settings.reminder.save-before-permission",
                write = {
                    activity.deviceSettingsStore.saveReminder(reminder)
                    activity.rearmReminderFromProcessStore()
                },
            ) {
                activity.requestPostNotificationPermission()
            }
            return
        }
        val allowed = activity.notificationsAllowedForReminders()
        saveReminderSettings(
            traceSection = "kani.settings.reminder.save",
            reminder = reminder,
            toastMessage = ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed),
            toastLength = if (allowed) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            onSaved = {
                activity.rearmReminderFromProcessStore()
            },
        )
    }

    private fun disableReminder(reminder: SettingsReminderState) {
        val fields = ReminderSettingsSavePolicy.fields(false, reminder.hour, reminder.minute)
        saveReminderSettings(
            traceSection = "kani.settings.reminder.disable",
            reminder = SettingsReminderState(fields.enabled, fields.hour, fields.minute),
            toastMessage = ReminderSettingsSavePolicy.disabledMessage(),
            toastLength = Toast.LENGTH_SHORT,
            onSaved = {
                ReminderScheduler.cancel(activity)
            },
        )
    }

    private fun saveReminderSettings(
        traceSection: String,
        reminder: SettingsReminderState,
        toastMessage: String,
        toastLength: Int,
        onSaved: () -> Unit,
    ) {
        activity.runSettingsWrite(
            traceSection = traceSection,
            write = {
                activity.deviceSettingsStore.saveReminder(reminder)
            },
        ) {
            onSaved()
            Toast.makeText(activity, toastMessage, toastLength).show()
            activity.renderSettingsAutomation(true)
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
        val notifications = AndroidNotificationGateway(activity)
        val channelSpecific = shouldOpenReminderChannelSettings(
            sdkInt = Build.VERSION.SDK_INT,
            hasRuntimePermission = activity.hasRuntimeNotificationPermissionForReminder(),
            appNotificationsEnabled = notifications.areNotificationsEnabled(),
            channelImportance = notifications.channelImportance(ReminderScheduler.REMINDER_CHANNEL_ID),
        )
        return NotificationSettingsAction(
            label = SettingsTextCopy.openNotificationSettingsLabel(),
            action = SettingsReminderAction { openNotificationSettings(channelSpecific) }
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

    private fun openNotificationSettings(channelSpecific: Boolean) {
        activity.reminderNotificationSettingsRefreshPending = true
        activity.startActivity(reminderNotificationSettingsIntent(activity, channelSpecific))
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

internal fun reminderNotificationSettingsIntent(
    context: Context,
    channelSpecific: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Intent {
    return if (channelSpecific && sdkInt >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.REMINDER_CHANNEL_ID)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}

internal fun shouldOpenReminderChannelSettings(
    sdkInt: Int,
    hasRuntimePermission: Boolean,
    appNotificationsEnabled: Boolean,
    channelImportance: Int?,
): Boolean {
    return sdkInt >= Build.VERSION_CODES.O &&
        hasRuntimePermission &&
        appNotificationsEnabled &&
        channelImportance == NotificationManager.IMPORTANCE_NONE
}
