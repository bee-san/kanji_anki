package dev.bee.kanjianki.widget

import android.app.AlarmManager
import android.content.Context
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

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(scheduled)
        assertEquals(AlarmManager.RTC, scheduled!!.type)
        assertEquals(refreshAt, scheduled.triggerAtTime)
        assertEquals(refreshAt, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
    }

    @Test
    fun dailyBoundarySchedulesMidnightWhenDueIsOutsideHourlyWindow() {
        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(
            context,
            NOW,
            NOW + KaniWidgetRefreshPolicy.ONE_SHOT_WINDOW_MILLIS + 1L,
            ZoneId.of("UTC"),
        )

        assertEquals(
            KaniWidgetRefreshPolicy.nextLocalMidnightMillis(NOW, ZoneId.of("UTC")),
            shadowOf(alarmManager).nextScheduledAlarm!!.triggerAtTime,
        )
    }

    @Test
    fun laterDailyCandidateDoesNotReplaceEarlierPersistedDueBoundary() {
        val due = NOW + 20 * 60 * 1000L
        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(context, NOW, due, ZoneId.of("UTC"))

        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(due, shadowOf(alarmManager).nextScheduledAlarm!!.triggerAtTime)
        assertEquals(due, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
    }

    @Test
    fun boundaryPendingIntentTargetsPlainRefreshReceiverAndIdentifiesAlarmFire() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        val savedIntent = shadowOf(shadowOf(alarmManager).nextScheduledAlarm!!.operation).savedIntent

        assertEquals(KaniWidgetRefreshReceiver::class.java.name, savedIntent.component?.className)
        assertEquals(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH, savedIntent.action)
        assertTrue(savedIntent.getBooleanExtra(KaniWidgetBoundaryAlarm.EXTRA_BOUNDARY_TRIGGER, false))
    }

    @Test
    fun alarmFireClearsPersistedBoundaryAndScheduledAlarm() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        KaniWidgetBoundaryAlarm.markFired(context)

        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun timeOrZoneResetClearsPersistedBoundaryBeforeRecalculation() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        KaniWidgetBoundaryAlarm.reset(context)

        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun removingOneProviderKeepsBoundaryButRemovingFinalProviderCancelsIt() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))
        val scheduled = KaniWidgetBoundaryAlarm.scheduledAtMillis(context)

        KaniWidgetBoundaryAlarm.onProvidersChanged(context, hasInstalledWidgets = true)
        assertEquals(scheduled, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNotNull(shadowOf(alarmManager).nextScheduledAlarm)

        KaniWidgetBoundaryAlarm.onProvidersChanged(context, hasInstalledWidgets = false)
        assertEquals(0L, KaniWidgetBoundaryAlarm.scheduledAtMillis(context))
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun boundaryUsesInexactRtcWithoutExactAlarmPermission() {
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, NOW, ZoneId.of("UTC"))

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm!!
        assertEquals(AlarmManager.RTC, scheduled.type)
        assertFalse(alarmManager.canScheduleExactAlarms())
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
