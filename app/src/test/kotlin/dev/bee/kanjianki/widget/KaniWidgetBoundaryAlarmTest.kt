package dev.bee.kanjianki.widget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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

    @Test
    fun schedulesInexactRtcAlarmAtNextUsefulTimeInsideWindow() {
        val refreshAt = NOW + 30 * 60 * 1000L

        KaniWidgetBoundaryAlarm.scheduleIfUseful(context, NOW, refreshAt)

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(scheduled)
        assertEquals(AlarmManager.RTC, scheduled!!.type)
        assertEquals(refreshAt, scheduled.triggerAtTime)
    }

    @Test
    fun reschedulingReplacesThePreviousAlarm() {
        KaniWidgetBoundaryAlarm.scheduleIfUseful(context, NOW, NOW + 10 * 60 * 1000L)
        KaniWidgetBoundaryAlarm.scheduleIfUseful(context, NOW, NOW + 20 * 60 * 1000L)

        val shadow = shadowOf(alarmManager)
        assertEquals(1, shadow.scheduledAlarms.size)
        assertEquals(NOW + 20 * 60 * 1000L, shadow.nextScheduledAlarm!!.triggerAtTime)
    }

    @Test
    fun uselessNextTimeCancelsAnyPendingAlarm() {
        KaniWidgetBoundaryAlarm.scheduleIfUseful(context, NOW, NOW + 10 * 60 * 1000L)

        KaniWidgetBoundaryAlarm.scheduleIfUseful(context, NOW, 0L)

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
