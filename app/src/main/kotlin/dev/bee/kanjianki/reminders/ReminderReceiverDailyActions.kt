package dev.bee.kanjianki.reminders

import android.content.Context
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.widget.KaniWidgetUpdater

internal class ReminderReceiverDailyActions(
    private val context: Context?,
    private val widgetRefresher: (Context?) -> Unit = KaniWidgetUpdater::requestUpdate,
    private val notificationShower: (Context?) -> Unit = ReminderScheduler::showReminderNotification,
) : ReminderReceiver.DailyReminderActions {
    override fun showReminderNotification() {
        widgetRefresher(context)
        notificationShower(context)
    }

    override fun schedule(settings: LocalStoreBase.ReminderSettings?) {
        if (settings == null || !settings.enabled) {
            context?.let(ReminderScheduler::cancel)
            return
        }
        ReminderScheduler.schedule(context, settings)
    }
}
