package dev.bee.kanjianki.core

object ReminderReceiverPolicy {
    private const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    private const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
    private const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"
    private const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"

    enum class ReceiverCommand {
        NONE,
        SCHEDULE_FROM_STORED_SETTINGS,
        HANDLE_DAILY_REMINDER,
        HANDLE_REMINDER_DISMISSED,
        HANDLE_REMINDER_SNOOZED,
    }

    @JvmStatic
    fun shouldReschedule(action: String?): Boolean {
        return ACTION_BOOT_COMPLETED == action ||
            ACTION_MY_PACKAGE_REPLACED == action ||
            ACTION_TIME_CHANGED == action ||
            ACTION_TIMEZONE_CHANGED == action
    }

    @JvmStatic
    fun commandFor(action: String?, dailyReminderAction: String?): ReceiverCommand {
        return commandFor(action, dailyReminderAction, null, null)
    }

    @JvmStatic
    fun commandFor(action: String?, dailyReminderAction: String?, dismissedAction: String?): ReceiverCommand {
        return commandFor(action, dailyReminderAction, dismissedAction, null)
    }

    @JvmStatic
    fun commandFor(action: String?, dailyReminderAction: String?, dismissedAction: String?, snoozedAction: String?): ReceiverCommand {
        if (ACTION_BOOT_COMPLETED == action) {
            return ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS
        }
        if (dailyReminderAction != null && dailyReminderAction == action) {
            return ReceiverCommand.HANDLE_DAILY_REMINDER
        }
        if (dismissedAction != null && dismissedAction == action) {
            return ReceiverCommand.HANDLE_REMINDER_DISMISSED
        }
        if (snoozedAction != null && snoozedAction == action) {
            return ReceiverCommand.HANDLE_REMINDER_SNOOZED
        }
        return ReceiverCommand.NONE
    }

    @JvmStatic
    fun shouldHandleDailyReminder(reminderEnabled: Boolean): Boolean {
        return reminderEnabled
    }
}
