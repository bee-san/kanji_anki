package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.DailyReminderDecision
import dev.bee.kanjianki.core.ReminderFamily
import dev.bee.kanjianki.platform.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderNotificationPresenterTest {
    @Test
    fun aDueDecisionBecomesAReminderNotificationKeyedByFamily() {
        val request = ReminderNotificationPresenter.present(
            decision(family = ReminderFamily.DUE, title = "Time to study", body = "5 kanji are due."),
        )

        requireNotNull(request)
        assertEquals("kani-reminder-due", request.id)
        assertEquals(NotificationCategory.REMINDER, request.category)
        assertEquals("Time to study", request.title)
        assertEquals("5 kanji are due.", request.body)
    }

    @Test
    fun everyFamilyMapsToACategoryAndAStableId() {
        assertEquals(NotificationCategory.REMINDER, categoryOf(ReminderFamily.DUE))
        assertEquals(NotificationCategory.REMINDER, categoryOf(ReminderFamily.STREAK))
        assertEquals(NotificationCategory.SYNC, categoryOf(ReminderFamily.SYNC))
        // Ids are distinct so different families do not coalesce onto each other.
        val ids = ReminderFamily.entries.map(ReminderNotificationPresenter::idFor)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun aDecisionThatShouldNotScheduleProducesNothing() {
        assertNull(
            ReminderNotificationPresenter.present(
                decision(family = ReminderFamily.DUE, title = "x", body = "y").copy(shouldSchedule = false),
            ),
        )
    }

    @Test
    fun aScheduledDecisionWithNoFamilyOrBlankCopyProducesNothing() {
        assertNull(ReminderNotificationPresenter.present(decision(family = null, title = "x", body = "y")))
        assertNull(ReminderNotificationPresenter.present(decision(family = ReminderFamily.DUE, title = " ", body = "y")))
        assertNull(ReminderNotificationPresenter.present(decision(family = ReminderFamily.SYNC, title = "x", body = " ")))
    }

    private fun categoryOf(family: ReminderFamily): NotificationCategory =
        ReminderNotificationPresenter.present(decision(family = family, title = "t", body = "b"))!!.category

    private fun decision(family: ReminderFamily?, title: String, body: String) = DailyReminderDecision(
        shouldSchedule = true,
        family = family,
        triggerAtMillis = 0L,
        title = title,
        body = body,
        reasonIds = emptyList(),
        humanReason = "",
    )
}
