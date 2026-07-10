package dev.bee.kanjianki.reminders

import android.content.Context
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.widget.KaniWidgetUpdater

internal class ReminderReceiverDailyActions(
    private val context: Context?,
) : ReminderReceiver.DailyReminderActions {
    override fun showReminderNotification() {
        KaniWidgetUpdater.requestUpdate(context)
        ReminderScheduler.showReminderNotification(context)
    }

    override fun schedule(settings: LocalStoreBase.ReminderSettings?) {
        ReminderScheduler.schedule(context, settings)
    }
}
