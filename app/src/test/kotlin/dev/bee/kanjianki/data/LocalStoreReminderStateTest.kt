package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreReminderStateTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private var originalZone: TimeZone? = null

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        originalZone?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun defaultThrottleStateIsEmpty() {
        val state = store.reminderThrottleState(utc(2026, Calendar.MAY, 15, 9, 0))

        assertEquals(0L, state.lastPostedAtMillis)
        assertEquals("", state.lastPostedSignature)
        assertEquals(0, state.dueShownToday)
        assertEquals("", state.dismissedFamilies)
        assertFalse(state.dailyOverrideUsedToday)
    }

    @Test
    fun recordReminderPostedPersistsAndCountsFamily() {
        val now = utc(2026, Calendar.MAY, 15, 9, 0)
        store.recordReminderPosted(now, "DUE", "3:5", false)

        val state = store.reminderThrottleState(now)
        assertEquals(now, state.lastPostedAtMillis)
        assertEquals("3:5", state.lastPostedSignature)
        assertEquals(1, state.dueShownToday)
        assertEquals(0, state.streakShownToday)
    }

    @Test
    fun perDayCountersResetOnNextLocalDay() {
        val day1 = utc(2026, Calendar.MAY, 15, 9, 0)
        store.recordReminderPosted(day1, "STREAK", "1:0", true)
        assertEquals(1, store.reminderThrottleState(day1).streakShownToday)
        assertTrue(store.reminderThrottleState(day1).dailyOverrideUsedToday)

        val day2 = utc(2026, Calendar.MAY, 16, 9, 0)
        val nextDay = store.reminderThrottleState(day2)
        assertEquals(0, nextDay.streakShownToday)
        assertFalse(nextDay.dailyOverrideUsedToday)
        // last-posted-at is not day-scoped and is retained.
        assertEquals(day1, nextDay.lastPostedAtMillis)
    }

    @Test
    fun reviewReminderCounterSaturatesAtMaximumValue() {
        val dayStart = utc(2026, Calendar.MAY, 15, 0, 0)
        val now = utc(2026, Calendar.MAY, 15, 9, 0)
        store.putLongSetting("review_reminder_day_start", dayStart)
        store.putIntSetting("review_reminder_count", Int.MAX_VALUE)

        store.recordReviewReminderNotificationShown(now)

        assertEquals(Int.MAX_VALUE, store.reviewReminderNotificationsToday(now))
    }

    @Test
    fun reminderFamilyCountersSaturateAtMaximumValue() {
        val dayStart = utc(2026, Calendar.MAY, 15, 0, 0)
        val now = utc(2026, Calendar.MAY, 15, 9, 0)
        store.putLongSetting("reminder_state_day_start", dayStart)
        store.putIntSetting("reminder_due_shown_today", Int.MAX_VALUE)
        store.putIntSetting("reminder_streak_shown_today", Int.MAX_VALUE)
        store.putIntSetting("reminder_sync_shown_today", Int.MAX_VALUE)

        store.recordReminderPosted(now, "DUE", "due", false)
        store.recordReminderPosted(now, "STREAK", "streak", false)
        store.recordReminderPosted(now, "SYNC", "sync", false)

        val state = store.reminderThrottleState(now)
        assertEquals(Int.MAX_VALUE, state.dueShownToday)
        assertEquals(Int.MAX_VALUE, state.streakShownToday)
        assertEquals(Int.MAX_VALUE, state.syncShownToday)
    }

    @Test
    fun dismissedFamilyIsSuppressedSameDayThenClearsNextDay() {
        val day1 = utc(2026, Calendar.MAY, 15, 20, 0)
        store.recordReminderDismissed(day1, "DUE")
        store.recordReminderDismissed(day1, "DUE") // idempotent
        store.recordReminderDismissed(day1, "SYNC")

        val families = store.reminderThrottleState(day1).dismissedFamilies
            .split(',').filter { it.isNotBlank() }.toSet()
        assertEquals(setOf("DUE", "SYNC"), families)

        val day2 = utc(2026, Calendar.MAY, 16, 9, 0)
        assertEquals("", store.reminderThrottleState(day2).dismissedFamilies)
    }

    @Test
    fun blankDismissalFamilyIsIgnored() {
        val now = utc(2026, Calendar.MAY, 15, 9, 0)
        store.recordReminderDismissed(now, "  ")
        assertEquals("", store.reminderThrottleState(now).dismissedFamilies)
    }

    @Test
    fun antiSpamSettingsRoundTripWithNormalization() {
        val defaults = store.reminderAntiSpamSettings()
        assertEquals(ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE, defaults.quietStartMinuteOfDay)
        assertEquals(ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE, defaults.quietEndMinuteOfDay)
        assertEquals(ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY, defaults.maxRemindersPerDay)

        store.saveReminderAntiSpamSettings(
            LocalStoreBase.ReminderAntiSpamSettings(23 * 60, 7 * 60, 9),
        )
        val saved = store.reminderAntiSpamSettings()
        assertEquals(23 * 60, saved.quietStartMinuteOfDay)
        assertEquals(7 * 60, saved.quietEndMinuteOfDay)
        // 9 clamps to the 1..3 range.
        assertEquals(ReminderAntiSpamPolicy.MAX_MAX_PER_DAY, saved.maxRemindersPerDay)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
