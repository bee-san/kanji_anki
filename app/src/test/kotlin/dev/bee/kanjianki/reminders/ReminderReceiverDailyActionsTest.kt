package dev.bee.kanjianki.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderReceiverDailyActionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reminderEvaluationRefreshesWidgetsBeforeShowingNotification() {
        val events = mutableListOf<String>()
        val actions = ReminderReceiverDailyActions(
            context = context,
            widgetRefresher = { events += "widget" },
            notificationShower = { _, snoozeRepost, family -> events += "notification:$snoozeRepost:$family" },
        )

        actions.showReminderNotification(true, "DUE")

        assertEquals(listOf("widget", "notification:true:DUE"), events)
    }

    @Test
    fun failedNotificationStillRearmsTheOneShotReminder() {
        val events = mutableListOf<String>()
        val settings = LocalStoreBase.ReminderSettings(true, 8, 30)
        val actions = object : ReminderReceiver.DailyReminderActions {
            override fun showReminderNotification(snoozeRepost: Boolean, family: String) {
                events += "notification:$snoozeRepost:$family"
                throw IllegalStateException("notification service failed")
            }

            override fun schedule(settings: LocalStoreBase.ReminderSettings?) {
                events += "schedule"
            }
        }

        assertThrows(IllegalStateException::class.java) {
            ReminderReceiver.handleDailyReminder(settings, true, "DUE", actions)
        }

        assertEquals(listOf("notification:true:DUE", "schedule"), events)
    }
}
