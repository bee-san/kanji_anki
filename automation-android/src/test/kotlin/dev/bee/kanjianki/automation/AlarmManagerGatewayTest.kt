package dev.bee.kanjianki.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmManagerGatewayTest {
    @Test
    fun schedulesAndCancelsWakeupAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = PendingIntent.getBroadcast(
            context,
            91,
            Intent("dev.bee.kanjianki.TEST_ALARM").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val gateway = AndroidAlarmManagerGateway(context)

        assertTrue(gateway.scheduleWakeup(123_456L, operation))
        val shadow = shadowOf(alarmManager)
        assertEquals(1, shadow.scheduledAlarms.size)
        val scheduled = requireNotNull(shadow.peekNextScheduledAlarm())
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.getType())
        assertEquals(123_456L, scheduled.getTriggerAtMs())

        assertTrue(gateway.cancel(operation))
        assertTrue(shadow.scheduledAlarms.isEmpty())
    }

    @Test
    fun missingAlarmManagerFailsClosed() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this

            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) null else super.getSystemService(name)
        }
        val operation = PendingIntent.getBroadcast(
            base,
            92,
            Intent("dev.bee.kanjianki.TEST_MISSING_ALARM").setPackage(base.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        assertFalse(AndroidAlarmManagerGateway(context).scheduleWakeup(1L, operation))
        assertFalse(AndroidAlarmManagerGateway(context).cancel(operation))
        assertNull(
            PendingIntent.getBroadcast(
                base,
                92,
                Intent("dev.bee.kanjianki.TEST_MISSING_ALARM").setPackage(base.packageName),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}
