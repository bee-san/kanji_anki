package dev.bee.kanjianki.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
            notificationShower = { events += "notification" },
        )

        actions.showReminderNotification()

        assertEquals(listOf("widget", "notification"), events)
    }
}
