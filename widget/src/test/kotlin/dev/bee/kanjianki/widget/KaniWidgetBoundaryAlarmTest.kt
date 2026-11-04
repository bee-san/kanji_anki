package dev.bee.kanjianki.widget

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetBoundaryAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun setUp() {
        KaniWidgetBoundaryAlarm.reset(context)
    }

    @After
    fun tearDown() {
        KaniWidgetBoundaryAlarm.reset(context)
    }

    @Test
    fun studyBoundaryPrefersDueInsideWindowOverMidnight() {
        val refreshAt = NOW + 30 * 60 * 1000L

        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(context, NOW, refreshAt, ZoneId.of("UTC"))

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()
        assertNotNull(scheduled)
        assertEquals(AlarmManager.RTC, scheduled!!.getType())
        assertEquals(refreshAt, scheduled.getTriggerAtMs())
        assertEquals(refreshAt, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
    }

    @Test
    fun futureDueBoundarySchedulesBeforeMidnightWithoutPeriodicFallback() {
        val due = NOW + 3 * 60 * 60 * 1000L
        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(
            context,
            NOW,
            due,
            ZoneId.of("UTC"),
        )

        assertEquals(due, shadowOf(alarmManager).peekNextScheduledAlarm()!!.getTriggerAtMs())
    }

    @Test
    fun laterDailyCandidateDoesNotReplaceEarlierPersistedDueBoundary() {
        val due = NOW + 20 * 60 * 1000L
        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(context, NOW, due, ZoneId.of("UTC"))

        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(due, shadowOf(alarmManager).peekNextScheduledAlarm()!!.getTriggerAtMs())
        assertEquals(due, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
    }

    @Test
    fun boundaryPendingIntentTargetsPlainRefreshReceiverAndIdentifiesAlarmFire() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        val savedIntent = shadowOf(scheduled.pendingIntent()).savedIntent

        assertEquals(KaniWidgetRefreshReceiver::class.java.name, savedIntent.component?.className)
        assertEquals(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH, savedIntent.action)
        assertTrue(savedIntent.getBooleanExtra(KaniWidgetBoundaryAlarm.EXTRA_BOUNDARY_TRIGGER, false))
    }

    @Test
    fun alarmFireClearsPersistedBoundaryAndScheduledAlarm() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        KaniWidgetBoundaryAlarm.markFired(context)

        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun timeOrZoneResetClearsPersistedBoundaryBeforeRecalculation() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        KaniWidgetBoundaryAlarm.reset(context)

        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun removingOneProviderKeepsBoundaryButRemovingFinalProviderCancelsIt() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))
        val scheduled = KaniWidgetBoundaryAlarm.scheduledAtMillis(context)

        KaniWidgetBoundaryAlarm.onProvidersChanged(context, hasInstalledWidgets = true)
        assertEquals(scheduled, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNotNull(shadowOf(alarmManager).peekNextScheduledAlarm())

        KaniWidgetBoundaryAlarm.onProvidersChanged(context, hasInstalledWidgets = false)
        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun boundaryUsesInexactRtcWithoutExactAlarmPermission() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(AlarmManager.RTC, scheduled.getType())
        assertFalse(alarmManager.canScheduleExactAlarms())
    }

    @Test
    fun missingAlarmManagerClearsStaleScheduledMetadata() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))
        assertTrue(KaniWidgetBoundaryAlarm.scheduledAtMillis(context) > 0L)

        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(
            NoAlarmManagerContext(context),
            NOW,
            ZoneId.of("UTC"),
        )

        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
    }

    @Test
    fun alarmManagerFailureDoesNotEscapeAndClearsStaleScheduledMetadata() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))
        assertTrue(KaniWidgetBoundaryAlarm.scheduledAtMillis(context) > 0L)

        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(
            ThrowingAlarmManagerContext(context),
            NOW,
            ZoneId.of("UTC"),
        )

        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
    }

    @Suppress("DEPRECATION")
    private fun ScheduledAlarm.pendingIntent() = operation

    private class NoAlarmManagerContext(base: Context) : ContextWrapper(base) {
        override fun getSystemService(name: String): Any? {
            return if (name == Context.ALARM_SERVICE) null else super.getSystemService(name)
        }
    }

    private class ThrowingAlarmManagerContext(base: Context) : ContextWrapper(base) {
        override fun getSystemService(name: String): Any? {
            if (name == Context.ALARM_SERVICE) {
                throw IllegalStateException("alarm service unavailable")
            }
            return super.getSystemService(name)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
