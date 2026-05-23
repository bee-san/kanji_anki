package dev.bee.kanjianki.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bee.kanjianki.core.ReminderReceiverPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: ""
        handle(action, AndroidReceiverActions(context))
    }

    interface ReceiverActions {
        fun scheduleFromStoredSettings()

        fun handleDailyReminder()
    }

    interface DailyReminderActions {
        fun showReminderNotification()

        fun schedule(settings: LocalStoreBase.ReminderSettings?)
    }

    private class AndroidReceiverActions(
        private val context: Context?,
    ) : ReceiverActions {
        override fun scheduleFromStoredSettings() {
            ReminderScheduler.schedule(context)
        }

        override fun handleDailyReminder() {
            LocalStore(context).use { store ->
                handleDailyReminder(
                    store.reminderSettings(),
                    ReminderReceiverDailyActions(context),
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun handle(action: String?, actions: ReceiverActions) {
            when (ReminderReceiverPolicy.commandFor(action, ReminderScheduler.ACTION_DAILY_REMINDER)) {
                ReminderReceiverPolicy.ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS -> {
                    actions.scheduleFromStoredSettings()
                }

                ReminderReceiverPolicy.ReceiverCommand.HANDLE_DAILY_REMINDER -> {
                    actions.handleDailyReminder()
                }

                ReminderReceiverPolicy.ReceiverCommand.NONE -> Unit
            }
        }

        @JvmStatic
        fun handleDailyReminder(
            settings: LocalStoreBase.ReminderSettings,
            actions: DailyReminderActions,
        ) {
            if (!ReminderReceiverPolicy.shouldHandleDailyReminder(settings.enabled)) {
                return
            }
            actions.showReminderNotification()
            actions.schedule(settings)
        }
    }
}
