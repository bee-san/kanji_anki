package dev.bee.kanjianki.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.time.AppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
class ReminderSchedulerTest {
    @Before
    fun setUp() {
        clearReminderState()
    }

    @After
    fun tearDown() {
        clearReminderState()
    }

    @Test
    fun scheduleDelegatesToServicesAndCancelsWhenDisabled() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val services = FakeReminderServices()
            val now = utc(2026, Calendar.MAY, 15, 7, 15)

            ReminderScheduler.schedule(null, services, { now })
            assertEquals(1, services.cancelCount)

            ReminderScheduler.schedule(LocalStoreBase.ReminderSettings(false, 8, 30), services, { now })
            assertEquals(2, services.cancelCount)

            ReminderScheduler.schedule(LocalStoreBase.ReminderSettings(true, 8, 30), services, { now })

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), services.scheduledAtMillis)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun notificationsAllowedAppliesPlatformFallbackGates() {
        val services = FakeReminderServices()
        services.runtimePermission = false
        assertFalse(ReminderScheduler.notificationsAllowed(services))

        services.runtimePermission = true
        services.notificationsEnabled = false
        assertFalse(ReminderScheduler.notificationsAllowed(services))

        services.notificationsEnabled = true
        services.channelImportance = android.app.NotificationManager.IMPORTANCE_NONE
        assertFalse(ReminderScheduler.notificationsAllowed(services))

        services.channelImportance = null
        assertTrue(ReminderScheduler.notificationsAllowed(services))

        services.channelImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT
        assertTrue(ReminderScheduler.notificationsAllowed(services))

        services.ensureNotificationChannel()
        assertEquals(1, services.ensureCount)
    }

    @Test
    fun showReminderNotificationStopsBeforeBuildingWhenNotificationsAreBlocked() {
        val services = FakeReminderServices()
        services.runtimePermission = false

        ReminderScheduler.showReminderNotification(null, services)

        assertEquals(0, services.ensureCount)
    }

    @Test
    fun showReminderNotificationPostsNotificationWhenAllowed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val services = FakeReminderServices(context)
        val notificationClock = AppClock { utc(2026, Calendar.MAY, 15, 9, 0) }

        ReminderScheduler.showReminderNotification(context, services, notificationClock)

        assertEquals(1, services.ensureCount)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, notificationManager.activeNotifications.size)
    }

    @Test
    fun notificationStatusHelperHandlesMissingDisabledAndEnabledStates() {
        assertFalse(ReminderScheduler.areNotificationsEnabled(null))
        assertFalse(ReminderScheduler.areNotificationsEnabled { false })
        assertTrue(ReminderScheduler.areNotificationsEnabled { true })
    }

    @Test
    fun bootReceiverReschedulesOnlyForSystemRecoveryActions() {
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_TIME_CHANGED))
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_TIMEZONE_CHANGED))

        assertFalse(BootReminderReceiver.shouldReschedule(null))
        assertFalse(BootReminderReceiver.shouldReschedule("dev.bee.kanjianki.OTHER"))
    }

    @Test
    fun bootReceiverHandleIgnoresNullIntent() {
        val actions = FakeRescheduleActions()

        BootReminderReceiver.handle(null, null as android.content.Intent?, actions)

        assertEquals(0, actions.scheduleCount)
    }

    @Test
    fun bootReceiverActionOrEmptyReadsOnlyPresentSources() {
        assertEquals("", BootReminderReceiver.actionOrEmpty<String>(null) {
            throw AssertionError("Null sources must not be read")
        })
        assertEquals(
            android.content.Intent.ACTION_TIME_CHANGED,
            BootReminderReceiver.actionOrEmpty("present") { android.content.Intent.ACTION_TIME_CHANGED },
        )
    }

    @Test
    fun bootReceiverHandleSchedulesForBootIntent() {
        val actions = FakeRescheduleActions()

        BootReminderReceiver.handle(null, android.content.Intent.ACTION_BOOT_COMPLETED, actions)

        assertEquals(1, actions.scheduleCount)
    }

    @Test
    fun reminderReceiverDispatchesBootDailyAndIgnoresOtherActions() {
        val actions = FakeReceiverActions()

        ReminderReceiver.handle(android.content.Intent.ACTION_BOOT_COMPLETED, actions)
        ReminderReceiver.handle(ReminderScheduler.ACTION_DAILY_REMINDER, actions)
        ReminderReceiver.handle("dev.bee.kanjianki.OTHER", actions)

        assertEquals("boot,daily", actions.events.joined)
    }

    @Test
    fun dailyReminderShowsAndReschedulesOnlyWhenEnabled() {
        val actions = FakeDailyReminderActions()
        val disabled = LocalStoreBase.ReminderSettings(false, 8, 30)
        val enabled = LocalStoreBase.ReminderSettings(true, 9, 45)

        ReminderReceiver.handleDailyReminder(disabled, actions)
        assertEquals("", actions.events.joined)

        ReminderReceiver.handleDailyReminder(enabled, actions)

        assertEquals("show,schedule", actions.events.joined)
        assertSame(enabled, actions.scheduledSettings)
    }

    @Test
    fun adaptiveNextTriggerUsesLatestReviewTimeAfterStudy() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 9, 0)

            val trigger = ReminderScheduler.nextTriggerMillis(
                LocalStoreBase.ReminderSettings(true, 8, 30),
                now,
                true,
                listOf(
                    utc(2026, Calendar.MAY, 15, 12, 0),
                    utc(2026, Calendar.MAY, 15, 13, 0),
                    utc(2026, Calendar.MAY, 15, 14, 0),
                ),
            )

            assertEquals(utc(2026, Calendar.MAY, 15, 14, 0), trigger)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun adaptiveNextTriggerFallsBackToTomorrowWhenReviewsAreAlreadyDue() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 9, 0)

            val trigger = ReminderScheduler.nextTriggerMillis(
                LocalStoreBase.ReminderSettings(true, 8, 30),
                now,
                true,
                listOf(
                    utc(2026, Calendar.MAY, 15, 7, 30),
                    utc(2026, Calendar.MAY, 15, 8, 0),
                ),
            )

            assertEquals(utc(2026, Calendar.MAY, 16, 8, 30), trigger)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun adaptiveNextTriggerSkipsLateReviewTimesUntilTomorrow() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 21, 0)

            val trigger = ReminderScheduler.nextTriggerMillis(
                LocalStoreBase.ReminderSettings(true, 8, 30),
                now,
                true,
                listOf(utc(2026, Calendar.MAY, 15, 23, 0)),
            )

            assertEquals(utc(2026, Calendar.MAY, 16, 8, 30), trigger)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun scheduleWithContextUsesStoredStudyStateAndCancelsWhenDisabled() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = utc(2026, Calendar.MAY, 15, 9, 0)
            val dueAt = utc(2026, Calendar.MAY, 15, 14, 0)
            seedReminderState(context, now, dueAt)
            val services = FakeReminderServices()

            ReminderScheduler.schedule(context, LocalStoreBase.ReminderSettings(false, 8, 30), services, now)
            assertEquals(1, services.cancelCount)

            ReminderScheduler.schedule(context, LocalStoreBase.ReminderSettings(true, 8, 30), services, now)

            assertEquals(dueAt, services.scheduledAtMillis)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun scheduleWithNullContextDoesNothing() {
        val services = FakeReminderServices()
        val now = utc(2026, Calendar.MAY, 15, 9, 0)

        ReminderScheduler.schedule(null, LocalStoreBase.ReminderSettings(true, 8, 30), services, now)

        assertEquals(0, services.cancelCount)
        assertEquals(-1L, services.scheduledAtMillis)
    }

    @Test
    fun nextTriggerWrapperUsesInjectedClock() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 7, 15)

            val trigger = ReminderScheduler.nextTriggerMillis(LocalStoreBase.ReminderSettings(true, 8, 30), { now })

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), trigger)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun clearReminderState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("kanji_anki_simple.db")
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    private fun seedReminderState(context: Context, reviewedAt: Long, dueAtMillis: Long) {
        LocalStore(context).use { store ->
            store.saveReview(
                RecordsSchedulerModels.ReviewRequest("裂", "seed-token", "good", false, false, false, 0),
                "good",
                reviewedAt,
            )
            store.saveStudyItem(
                RecordsStudyModels.StudyItem(
                    "裂",
                    "review",
                    dueAtMillis,
                    1.0,
                    5.0,
                    1,
                    0,
                    0,
                    0,
                    "seed-token",
                    reviewedAt,
                )
            )
        }
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private class FakeReminderServices(private val channelContext: Context? = null) : ReminderScheduler.ReminderServices {
        var cancelCount = 0
        var scheduledAtMillis = -1L
        var runtimePermission = true
        var notificationsEnabled = true
        var channelImportance: Int? = null
        var ensureCount = 0

        override fun scheduleAlarm(triggerAtMillis: Long) {
            scheduledAtMillis = triggerAtMillis
        }

        override fun cancelAlarm() {
            cancelCount++
        }

        override fun hasRuntimeNotificationPermission(): Boolean {
            return runtimePermission
        }

        override fun areNotificationsEnabled(): Boolean {
            return notificationsEnabled
        }

        override fun reminderChannelImportance(): Int? {
            return channelImportance
        }

        override fun ensureNotificationChannel() {
            ensureCount++
            val context = channelContext ?: return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    "kani_study_reminders",
                    "Study reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    private class FakeReceiverActions : ReminderReceiver.ReceiverActions {
        val events = Events()

        override fun scheduleFromStoredSettings() {
            events.append("boot")
        }

        override fun handleDailyReminder() {
            events.append("daily")
        }
    }

    private class FakeRescheduleActions : BootReminderReceiver.RescheduleActions {
        var scheduleCount = 0

        override fun schedule(context: Context?) {
            scheduleCount++
        }
    }

    private class FakeDailyReminderActions : ReminderReceiver.DailyReminderActions {
        val events = Events()
        var scheduledSettings: LocalStoreBase.ReminderSettings? = null

        override fun showReminderNotification() {
            events.append("show")
        }

        override fun schedule(settings: LocalStoreBase.ReminderSettings?) {
            scheduledSettings = settings
            events.append("schedule")
        }
    }

    private class Events {
        var joined = ""

        fun append(event: String) {
            if (joined.isNotEmpty()) {
                joined += ","
            }
            joined += event
        }
    }
}
