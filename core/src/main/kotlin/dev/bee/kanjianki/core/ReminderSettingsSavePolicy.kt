package dev.bee.kanjianki.core

object ReminderSettingsSavePolicy {
    const val DISABLED_MESSAGE: String = "Reminder turned off."
    const val NOTIFICATIONS_BLOCKED_MESSAGE: String = "Reminder saved, but Android notifications are off."
    const val PERMISSION_DENIED_MESSAGE: String = "Notifications are off, so reminders are disabled."

    @JvmStatic
    fun fields(enabled: Boolean, hour: Int, minute: Int): ReminderFields {
        val normalized = TimeOfDaySettingsPolicy.normalizeReminder(enabled, hour, minute)
        return ReminderFields(normalized.enabled(), normalized.hour(), normalized.minute())
    }

    @JvmStatic
    fun savedMessage(hour: Int, minute: Int, notificationsAllowed: Boolean): String {
        if (!notificationsAllowed) {
            return NOTIFICATIONS_BLOCKED_MESSAGE
        }
        return "Reminder saved for around ${TimeOfDaySettingsPolicy.displayTime(hour, minute)}."
    }

    @JvmRecord
    data class ReminderFields(val enabled: Boolean, val hour: Int, val minute: Int)
}
