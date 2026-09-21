package dev.bee.kanjianki.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context

interface AlarmManagerGateway {
    fun scheduleWakeup(triggerAtMillis: Long, operation: PendingIntent): Boolean

    fun cancel(operation: PendingIntent): Boolean
}

class AndroidAlarmManagerGateway(
    context: Context,
) : AlarmManagerGateway {
    private val alarmManager =
        context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun scheduleWakeup(triggerAtMillis: Long, operation: PendingIntent): Boolean {
        val manager = alarmManager ?: return false
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
        return true
    }

    override fun cancel(operation: PendingIntent): Boolean {
        val manager = alarmManager
        manager?.cancel(operation)
        operation.cancel()
        return manager != null
    }
}
