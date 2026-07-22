package dev.bee.kanjianki.reminders

import android.content.Context
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.widget.KaniWidgetUpdater

internal class ReminderReceiverDailyActions(
    private val context: Context?,
    private val widgetRefresher: (Context?) -> Unit = KaniWidgetUpdater::requestUpdate,
    private val notificationShower: (Context?, Boolean, String) -> Unit = { target, snoozeRepost, family ->
        ReminderScheduler.showReminderNotification(target, snoozeRepost, family)
    },
) : ReminderReceiver.DailyReminderActions {
    override fun showReminderNotification(snoozeRepost: Boolean, family: String) {
        widgetRefresher(context)
        notificationShower(context, snoozeRepost, family)
    }

    override fun schedule(settings: LocalStoreBase.ReminderSettings?) {
        if (settings == null || !settings.enabled) {
            context?.let(ReminderScheduler::cancel)
            return
        }
        ReminderScheduler.schedule(context, settings)
    }
}
