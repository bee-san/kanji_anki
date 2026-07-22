package dev.bee.kanjianki.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.ReminderCopyPolicy
import dev.bee.kanjianki.core.ReminderFamily
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
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
            assertEquals(8, services.scheduledHour)
            assertEquals(30, services.scheduledMinute)
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
        val now = utc(2026, Calendar.MAY, 15, 9, 0)
        seedDailyReminderState(context, now, utc(2026, Calendar.MAY, 15, 14, 0))

        ReminderScheduler.showReminderNotification(context, services, AppClock { now })

        assertEquals(1, services.ensureCount)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, notificationManager.activeNotifications.size)
        val posted = notificationManager.activeNotifications.first().notification
        // In-place replacement of the single slot must not re-buzz (D1/D7 fix).
        assertTrue(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.CATEGORY_REMINDER, posted.category)
        assertTrue(
            shadowOf(posted.contentIntent).savedIntent
                .getBooleanExtra(dev.bee.kanjianki.MainActivityBase.EXTRA_OPEN_STUDY, false),
        )
    }

    @Test
    fun reminderBodyDestinationMatchesTheReminderFamily() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val due = ReminderScheduler.reminderOpenIntent(context, ReminderFamily.DUE.name)
        val streak = ReminderScheduler.reminderOpenIntent(context, ReminderFamily.STREAK.name)
        val sync = ReminderScheduler.reminderOpenIntent(context, ReminderFamily.SYNC.name)

        assertTrue(due.getBooleanExtra(dev.bee.kanjianki.MainActivityBase.EXTRA_OPEN_STUDY, false))
        assertTrue(streak.getBooleanExtra(dev.bee.kanjianki.MainActivityBase.EXTRA_OPEN_STUDY, false))
        assertFalse(sync.hasExtra(dev.bee.kanjianki.MainActivityBase.EXTRA_OPEN_STUDY))
        assertTrue(due.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun overdueDoubleFireIsThrottledAndReArmsAfterMinGap() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val context = ApplicationProvider.getApplicationContext<Context>()
            val services = FakeReminderServices(context)
            val now = utc(2026, Calendar.MAY, 15, 15, 0)
            // Studied today with three overdue reviews (meets the default min batch).
            seedStudiedTodayWithOverdue(context, now, dueAtMillis = now - HOUR)

            // First fire posts once.
            ReminderScheduler.showReminderNotification(context, services, AppClock { now })
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertEquals(1, manager.activeNotifications.size)

            // Receiver re-arms seconds later; the same overdue set is still due, but
            // the throttle must push the next alarm at least the min gap out instead
            // of firing again immediately (D1).
            val secondsLater = now + 5_000L
            ReminderScheduler.schedule(context, LocalStoreBase.ReminderSettings(true, 8, 30), services, secondsLater)
            assertTrue(services.scheduledAtMillis >= now + MIN_GAP_MILLIS)

            // A second post attempt within the gap is suppressed (still one visible card).
            ReminderScheduler.showReminderNotification(context, services, AppClock { secondsLater })
            assertEquals(1, manager.activeNotifications.size)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun showReminderNotificationSkipsWhenDailyPlanHasNothingUseful() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val services = FakeReminderServices(context)
        val now = utc(2026, Calendar.MAY, 15, 9, 0)
        seedSuccessfulSync(context, now)

        ReminderScheduler.showReminderNotification(context, services, AppClock { now })

        assertEquals(0, services.ensureCount)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, notificationManager.activeNotifications.size)
    }

    @Test
    fun ensureNotificationChannelUsesJapaneseMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        withLocale(Locale.JAPANESE) {
            ReminderScheduler.ensureNotificationChannel(context)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(ReminderScheduler.REMINDER_CHANNEL_ID)

            assertTrue(channel != null)
            assertEquals(ReminderCopyPolicy.notificationChannelName(), channel!!.name.toString())
            assertEquals(ReminderCopyPolicy.notificationChannelDescription(), channel.description?.toString())
        }
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
        ReminderReceiver.handle(ReminderScheduler.ACTION_DAILY_REMINDER, "", 6, 25, actions)
        ReminderReceiver.handle("dev.bee.kanjianki.OTHER", actions)

        assertEquals("boot,fallback:6:25,daily", actions.events.joined)
    }

    @Test
    fun reminderReceiverDispatchesDismissedActionWithFamily() {
        val actions = FakeReceiverActions()

        ReminderReceiver.handle(ReminderScheduler.ACTION_REMINDER_DISMISSED, "DUE", actions)

        assertEquals("dismiss:DUE", actions.events.joined)
    }

    @Test
    fun dailyReminderShowsAndReschedulesOnlyWhenEnabled() {
        val actions = FakeDailyReminderActions()
        val disabled = LocalStoreBase.ReminderSettings(false, 8, 30)
        val enabled = LocalStoreBase.ReminderSettings(true, 9, 45)

        ReminderReceiver.handleDailyReminder(disabled, actions)
        assertEquals("schedule", actions.events.joined)
        assertSame(disabled, actions.scheduledSettings)

        actions.events.joined = ""

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
    fun scheduleWithActivityStoreUsesFreshStateWithoutClosingTheSharedStore() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = utc(2026, Calendar.MAY, 15, 9, 0)
            val dueAt = utc(2026, Calendar.MAY, 15, 14, 0)
            seedReminderState(context, now, dueAt)
            val services = FakeReminderServices()

            LocalStore(context).use { activityStore ->
                activityStore.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 8, 30))
                // Warm the exact cache that Home populates before the lifecycle re-arm runs.
                assertEquals(3, activityStore.activeDashboardRows().size)

                ReminderScheduler.schedule(activityStore, services, now)

                assertEquals(dueAt, services.scheduledAtMillis)
                // The overload borrows rather than owns the activity store.
                assertTrue(activityStore.reminderSettings().enabled)
            }
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
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        notificationManager.deleteNotificationChannel(ReminderScheduler.REMINDER_CHANNEL_ID)
    }

    private fun seedDailyReminderState(context: Context, reviewedAt: Long, dueAtMillis: Long) {
        LocalStore(context).use { store ->
            store.saveRows(
                store.writableDatabase,
                listOf(
                    RecordsImportModels.DashboardRow(
                        "裂",
                        120,
                        "裂",
                        "裂",
                        "裂",
                        0,
                        "",
                        "",
                        0,
                        0,
                        0,
                        listOf<RecordsImportModels.Example>(),
                    ),
                ),
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

    private fun seedSuccessfulSync(context: Context, finishedAt: Long) {
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                emptyList<RecordsImportModels.SuspendedImport>(),
                emptyList<RecordsImportModels.DashboardRow>(),
                RecordsSyncModels.Settings.kikuDefaults(),
                finishedAt,
                finishedAt,
                null,
            )
        }
    }

    private fun seedReminderState(context: Context, reviewedAt: Long, dueAtMillis: Long) {
        // Seed a real review batch: three distinct kanji all due at dueAtMillis so
        // the default minimum batch size (3) is met and the cluster trigger stays
        // at dueAtMillis. A single item would be suppressed as a learning-step tail.
        val kanji = listOf("裂", "包", "風")
        LocalStore(context).use { store ->
            store.saveRows(
                store.writableDatabase,
                kanji.map {
                    RecordsImportModels.DashboardRow(
                        it,
                        120,
                        it,
                        it,
                        it,
                        0,
                        "",
                        "",
                        0,
                        0,
                        0,
                        listOf<RecordsImportModels.Example>(),
                    )
                },
                reviewedAt,
            )
            store.saveReview(
                RecordsSchedulerModels.ReviewRequest("裂", "seed-token", "good", false, false, false, 0),
                "good",
                reviewedAt,
            )
            kanji.forEach {
                store.saveStudyItem(
                    RecordsStudyModels.StudyItem(
                        it,
                        "review",
                        dueAtMillis,
                        1.0,
                        5.0,
                        1,
                        0,
                        0,
                        0,
                        "seed-token-$it",
                        reviewedAt,
                    )
                )
            }
        }
    }

    private fun seedStudiedTodayWithOverdue(context: Context, reviewedAt: Long, dueAtMillis: Long) {
        val kanji = listOf("裂", "包", "風")
        LocalStore(context).use { store ->
            store.saveRows(
                store.writableDatabase,
                kanji.map {
                    RecordsImportModels.DashboardRow(
                        it, 120, it, it, it, 0, "", "", 0, 0, 0, listOf<RecordsImportModels.Example>(),
                    )
                },
                reviewedAt,
            )
            // A saved review marks studiedToday and sets lastStudyAtMillis; place it
            // well before now so the activity grace window does not suppress the post.
            store.saveReview(
                RecordsSchedulerModels.ReviewRequest("裂", "seed-token", "good", false, false, false, 0),
                "good",
                reviewedAt - 2 * HOUR,
            )
            kanji.forEach {
                store.saveStudyItem(
                    RecordsStudyModels.StudyItem(
                        it, "review", dueAtMillis, 1.0, 5.0, 2, 0, 0, 0, "seed-token-$it", reviewedAt - 2 * HOUR,
                    )
                )
            }
        }
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    private class FakeReminderServices(private val channelContext: Context? = null) : ReminderScheduler.ReminderServices {
        var cancelCount = 0
        var scheduledAtMillis = -1L
        var scheduledHour = -1
        var scheduledMinute = -1
        var runtimePermission = true
        var notificationsEnabled = true
        var channelImportance: Int? = null
        var ensureCount = 0

        override fun scheduleAlarm(triggerAtMillis: Long, hour: Int, minute: Int) {
            scheduledAtMillis = triggerAtMillis
            scheduledHour = hour
            scheduledMinute = minute
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
                    ReminderScheduler.REMINDER_CHANNEL_ID,
                    ReminderCopyPolicy.notificationChannelName(),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = ReminderCopyPolicy.notificationChannelDescription()
                }
            )
        }
    }

    private class FakeReceiverActions : ReminderReceiver.ReceiverActions {
        val events = Events()

        override fun scheduleFromStoredSettings() {
            events.append("boot")
        }

        override fun scheduleFallbackDailyReminder(hour: Int, minute: Int) {
            events.append("fallback:$hour:$minute")
        }

        override fun handleDailyReminder() {
            events.append("daily")
        }

        override fun handleReminderDismissed(family: String) {
            events.append("dismiss:$family")
        }

        override fun handleReminderSnoozed(family: String) {
            events.append("snooze:$family")
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

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val MIN_GAP_MILLIS = 90L * 60L * 1000L
    }
}
