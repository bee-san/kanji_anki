package dev.bee.kanjianki.reminders

import android.content.Context
import dev.bee.kanjianki.data.LocalStoreBase

internal class ReminderReceiverDailyActions(
    private val context: Context?,
) : ReminderReceiver.DailyReminderActions {
    override fun showReminderNotification() {
        ReminderScheduler.showReminderNotification(context)
    }

    override fun schedule(settings: LocalStoreBase.ReminderSettings?) {
        ReminderScheduler.schedule(context, settings)
    }
}
