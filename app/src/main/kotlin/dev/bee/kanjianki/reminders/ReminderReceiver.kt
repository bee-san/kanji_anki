package dev.bee.kanjianki.reminders

import dev.bee.kanjianki.AppLocalStoreFactory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bee.kanjianki.core.ReminderReceiverPolicy
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.receivers.ReceiverAsyncWork

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: ""
        val family = intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER_FAMILY) ?: ""
        val hour = intent?.getIntExtra(
            ReminderScheduler.EXTRA_REMINDER_HOUR,
            TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR,
        ) ?: TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR
        val minute = intent?.getIntExtra(
            ReminderScheduler.EXTRA_REMINDER_MINUTE,
            TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE,
        ) ?: TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE
        // Reads the full dashboard + all study items + streaks; do it off the main
        // thread and keep the broadcast alive until it completes.
        ReceiverAsyncWork.run(this) {
            handle(action, family, hour, minute, AndroidReceiverActions(context))
        }
    }

    interface ReceiverActions {
        fun scheduleFromStoredSettings()

        fun scheduleFallbackDailyReminder(hour: Int, minute: Int)

        fun handleDailyReminder()

        fun handleReminderDismissed(family: String)

        fun handleReminderSnoozed(family: String)
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

        override fun scheduleFallbackDailyReminder(hour: Int, minute: Int) {
            ReminderScheduler.scheduleFallbackDailyReminder(context, hour, minute)
        }

        override fun handleDailyReminder() {
            AppLocalStoreFactory.create(context).use { store ->
                handleDailyReminder(
                    store.reminderSettings(),
                    ReminderReceiverDailyActions(context),
                )
            }
        }

        override fun handleReminderDismissed(family: String) {
            val safeContext = context ?: return
            AppLocalStoreFactory.create(safeContext).use { store ->
                // Swipe-dismiss is the user's strongest anti-spam signal: suppress
                // this family for the rest of the local day, then re-arm from fresh
                // state so the next eligible time reflects the dismissal.
                store.recordReminderDismissed(dev.bee.kanjianki.time.AppClock.systemClock().nowMillis(), family)
            }
            ReminderScheduler.schedule(safeContext)
        }

        override fun handleReminderSnoozed(family: String) {
            val safeContext = context ?: return
            ReminderScheduler.cancelPostedNotification(safeContext)
            ReminderScheduler.schedule(safeContext)
        }
    }

    companion object {
        @JvmStatic
        fun handle(action: String?, actions: ReceiverActions) {
            handle(
                action,
                "",
                TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR,
                TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE,
                actions,
            )
        }

        @JvmStatic
        fun handle(action: String?, family: String, actions: ReceiverActions) {
            handle(
                action,
                family,
                TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR,
                TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE,
                actions,
            )
        }

        @JvmStatic
        fun handle(action: String?, family: String, hour: Int, minute: Int, actions: ReceiverActions) {
            when (
                ReminderReceiverPolicy.commandFor(
                    action,
                    ReminderScheduler.ACTION_DAILY_REMINDER,
                    ReminderScheduler.ACTION_REMINDER_DISMISSED,
                    ReminderScheduler.ACTION_REMINDER_SNOOZED,
                )
            ) {
                ReminderReceiverPolicy.ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS -> {
                    actions.scheduleFromStoredSettings()
                }

                ReminderReceiverPolicy.ReceiverCommand.HANDLE_DAILY_REMINDER -> {
                    actions.scheduleFallbackDailyReminder(hour, minute)
                    actions.handleDailyReminder()
                }

                ReminderReceiverPolicy.ReceiverCommand.HANDLE_REMINDER_DISMISSED -> {
                    actions.handleReminderDismissed(family)
                }

                ReminderReceiverPolicy.ReceiverCommand.HANDLE_REMINDER_SNOOZED -> {
                    actions.handleReminderSnoozed(family)
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
                actions.schedule(settings)
                return
            }
            try {
                actions.showReminderNotification()
            } finally {
                // This is a one-shot alarm. Re-arm it even when notification
                // construction or posting fails so reminders recover tomorrow.
                actions.schedule(settings)
            }
        }
    }
}
