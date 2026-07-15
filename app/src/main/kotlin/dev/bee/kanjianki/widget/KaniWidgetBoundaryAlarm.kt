package dev.bee.kanjianki.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * One-shot inexact alarm that re-renders the widget at the next useful time
 * (see [KaniWidgetRefreshPolicy.oneShotRefreshAtMillis]). Uses an explicit
 * broadcast to [KaniWidgetReceiver], `AlarmManager.set` (inexact, no
 * exact-alarm permission), and a fixed request code so rescheduling replaces
 * the previous alarm instead of accumulating.
 */
internal object KaniWidgetBoundaryAlarm {
    private const val REQUEST_CODE = 7201

    fun scheduleIfUseful(context: Context, nowMillis: Long, nextUsefulAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, KaniWidgetReceiver::class.java)
                .setAction(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val refreshAtMillis = KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(nowMillis, nextUsefulAtMillis)
        if (refreshAtMillis <= 0L) {
            alarmManager.cancel(pendingIntent)
            return
        }
        alarmManager.set(AlarmManager.RTC, refreshAtMillis, pendingIntent)
    }
}
