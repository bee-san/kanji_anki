package dev.bee.kanjianki.reminders

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.MainActivityBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val REMINDER_REQUEST_CODE = 2701
private const val REMINDER_NOTIFICATION_ID = 2702
private const val FUTURE_ALARM_AT_MILLIS = 4_102_444_800_000L

@RunWith(AndroidJUnit4::class)
class ReminderReceiverInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        ReminderScheduler.cancel(context)
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @After
    fun tearDown() {
        ReminderScheduler.cancel(context)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(REMINDER_NOTIFICATION_ID)
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @Test
    fun receiverIgnoresNullAndDisabledDailyReminder() {
        val receiver = ReminderReceiver()
        receiver.onReceive(context, null)
        LocalStore(context).use { store ->
            store.saveReminderSettings(LocalStoreBase.ReminderSettings(false, 8, 30))
        }

        receiver.onReceive(context, Intent(ReminderScheduler.ACTION_DAILY_REMINDER))

        LocalStore(context).use { store ->
            assertFalse(store.reminderSettings().enabled)
        }
        assertFalse(waitForPendingReminderIntent(expectedPresent = false))
    }

    @Test
    fun receiverKeepsEnabledReminderAfterDailyNotificationAttempt() {
        LocalStore(context).use { store ->
            store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 9, 45))
        }

        ReminderReceiver().onReceive(context, Intent(ReminderScheduler.ACTION_DAILY_REMINDER))

        LocalStore(context).use { store ->
            val settings = store.reminderSettings()
            assertEquals(9, settings.hour)
            assertEquals(45, settings.minute)
        }
        assertTrue(waitForPendingReminderIntent(expectedPresent = true))
    }

    @Test
    fun receiverSchedulesFromStoredSettingsOnBoot() {
        clearPendingReminderIntent()
        LocalStore(context).use { store ->
            store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 10, 15))
        }

        ReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        LocalStore(context).use { store ->
            val settings = store.reminderSettings()
            assertEquals(10, settings.hour)
            assertEquals(15, settings.minute)
        }
        clearPendingReminderIntent()
    }

    @Test
    fun schedulerNotificationGateReturnsFalseWhenRuntimePermissionIsMissing() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val expected = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            assertEquals(expected, ReminderScheduler.hasRuntimeNotificationPermission(context))
        }
    }

    @Test
    fun schedulerPermissionHelperCoversSdkBranches() {
        val preAndroidThirteen = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int {
                throw AssertionError("pre-Android 13 must not check POST_NOTIFICATIONS")
            }
        }

        assertTrue(ReminderScheduler.hasRuntimeNotificationPermission(preAndroidThirteen, 32))
        assertFalse(
            ReminderScheduler.hasRuntimeNotificationPermission(
                permissionContext(PackageManager.PERMISSION_DENIED),
                33,
            )
        )
        assertTrue(
            ReminderScheduler.hasRuntimeNotificationPermission(
                permissionContext(PackageManager.PERMISSION_GRANTED),
                33,
            )
        )
    }

    @Test
    fun dailyReminderIntentCarriesFallbackSchedule() {
        val intent = ReminderScheduler.dailyReminderIntent(context, 6, 25, true)

        assertEquals(ReminderScheduler.ACTION_DAILY_REMINDER, intent.action)
        assertEquals(6, intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_HOUR, -1))
        assertEquals(25, intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_MINUTE, -1))
        assertTrue(intent.getBooleanExtra(ReminderScheduler.EXTRA_REMINDER_SNOOZE_REPOST, false))
    }

    @Test
    fun reminderOpenIntentRoutesDueWorkToStudy() {
        val intent = ReminderScheduler.reminderOpenIntent(context, "DUE")

        assertTrue(intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_STUDY, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun showReminderNotificationStopsWhenNotificationManagerIsMissing() {
        val services = FakeReminderServices()

        ReminderScheduler.showReminderNotification(NullSystemServiceContext(context), services)

        assertEquals(1, services.ensureCount)
    }

    @Test
    fun showReminderNotificationSkipsChannelWhenNotificationsAreBlocked() {
        val services = FakeReminderServices().apply {
            runtimePermission = false
        }

        ReminderScheduler.showReminderNotification(NullSystemServiceContext(context), services)

        assertEquals(0, services.ensureCount)
    }

    @Test
    fun bootReceiverHandleReadsNonNullIntentAction() {
        val actions = FakeRescheduleActions()

        BootReminderReceiver.handle(context, Intent(Intent.ACTION_TIME_CHANGED), actions)

        assertEquals(1, actions.scheduleCount)
    }

    @Test
    fun bootReceiverHandleIgnoresNullIntent() {
        val actions = FakeRescheduleActions()

        BootReminderReceiver.handle(context, null as Intent?, actions)

        assertEquals(0, actions.scheduleCount)
    }

    @Test
    fun bootReceiverOnReceiveIgnoresSpoofedActionsBeforeDelegating() {
        val actions = FakeRescheduleActions()
        val receiver = BootReminderReceiver(actions)

        receiver.onReceive(context, null)
        receiver.onReceive(context, Intent("dev.bee.kanjianki.SPOOFED_BOOT"))

        assertEquals(0, actions.scheduleCount)

        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertEquals(1, actions.scheduleCount)
    }

    @Test
    fun androidReminderServicesHandlesMissingAndPresentSystemManagers() {
        val missingServices = ReminderScheduler.androidReminderServices(NullSystemServiceContext(context))

        missingServices.scheduleAlarm(FUTURE_ALARM_AT_MILLIS, 8, 30)
        missingServices.cancelAlarm()
        assertFalse(missingServices.areNotificationsEnabled())
        assertEquals(NotificationManager.IMPORTANCE_NONE, missingServices.reminderChannelImportance())
        missingServices.ensureNotificationChannel()

        clearPendingReminderIntent()
        val realServices = ReminderScheduler.androidReminderServices(context)
        realServices.cancelAlarm()
        realServices.scheduleAlarm(FUTURE_ALARM_AT_MILLIS, 8, 30)
        realServices.cancelAlarm()
        realServices.areNotificationsEnabled()
        realServices.ensureNotificationChannel()
        realServices.reminderChannelImportance()
        clearPendingReminderIntent()
    }

    private fun permissionContext(result: Int): Context {
        return object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun checkSelfPermission(permission: String): Int {
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, permission)
                return result
            }
        }
    }

    private fun clearPendingReminderIntent() {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_DAILY_REMINDER),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.cancel()
    }

    private fun waitForPendingReminderIntent(expectedPresent: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        var present: Boolean
        do {
            present = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                Intent(context, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_DAILY_REMINDER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) != null
            if (present == expectedPresent) {
                return present
            }
            SystemClock.sleep(25L)
        } while (SystemClock.elapsedRealtime() < deadline)
        return present
    }

    private class NullSystemServiceContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? {
            return null
        }
    }

    private class FakeReminderServices : ReminderScheduler.ReminderServices {
        var runtimePermission = true
        var notificationsEnabled = true
        var channelImportance: Int? = null
        var ensureCount = 0

        override fun scheduleAlarm(triggerAtMillis: Long, hour: Int, minute: Int) {
            // This fake only tracks notification behavior.
        }

        override fun cancelAlarm() {
            // This fake only tracks notification behavior.
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
        }
    }

    private class FakeRescheduleActions : BootReminderReceiver.RescheduleActions {
        var scheduleCount = 0

        override fun schedule(context: Context?) {
            scheduleCount++
        }
    }
}
