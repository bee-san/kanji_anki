package dev.bee.kanjianki.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.ZoneId

/** One durable inexact alarm shared by every installed Kani widget provider. */
internal object KaniWidgetBoundaryAlarm {
    const val EXTRA_BOUNDARY_TRIGGER = "dev.bee.kanjianki.widget.extra.BOUNDARY_TRIGGER"

    private const val REQUEST_CODE = 7201
    private const val PREFS_NAME = "kani_widget_boundary"
    private const val KEY_DUE_AT = "due_at"
    private const val KEY_MIDNIGHT_AT = "midnight_at"
    private const val KEY_SCHEDULED_AT = "scheduled_at"

    fun scheduleStudyBoundary(
        context: Context,
        nowMillis: Long,
        nextUsefulAtMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val dueAt = KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(nowMillis, nextUsefulAtMillis)
        val midnightAt = KaniWidgetRefreshPolicy.nextLocalMidnightMillis(nowMillis, zoneId)
        val preferences = preferences(context)
        // `commit()`, not `apply()`: the reschedule below reads these back, so an async
        // write could arm the alarm from the previous values. Plain SharedPreferences rather
        // than `androidx.core`'s `edit` extension, so the module needs no core-ktx dependency.
        preferences.edit()
            .putOrRemove(KEY_DUE_AT, dueAt)
            .putLong(KEY_MIDNIGHT_AT, midnightAt)
            .commit()
        reschedulePersisted(context, nowMillis)
    }

    fun scheduleDailyBoundary(
        context: Context,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        preferences(context).edit()
            .putLong(
                KEY_MIDNIGHT_AT,
                KaniWidgetRefreshPolicy.nextLocalMidnightMillis(nowMillis, zoneId),
            )
            .commit()
        reschedulePersisted(context, nowMillis)
    }

    fun markFired(context: Context) {
        reset(context)
    }

    fun reset(context: Context) {
        cancelAlarm(context)
        preferences(context).edit().clear().commit()
    }

    fun onProvidersChanged(context: Context, hasInstalledWidgets: Boolean) {
        if (!hasInstalledWidgets) reset(context)
    }

    fun scheduledAtMillis(context: Context): Long =
        preferences(context).getLong(KEY_SCHEDULED_AT, 0L)

    private fun reschedulePersisted(context: Context, nowMillis: Long) {
        val preferences = preferences(context)
        val dueAt = preferences.getLong(KEY_DUE_AT, 0L).takeIf { it > nowMillis } ?: 0L
        val midnightAt = preferences.getLong(KEY_MIDNIGHT_AT, 0L).takeIf { it > nowMillis } ?: 0L
        val triggerAt = listOf(dueAt, midnightAt).filter { it > 0L }.minOrNull() ?: 0L
        if (triggerAt <= 0L) {
            cancelAlarm(context)
            preferences.edit().remove(KEY_SCHEDULED_AT).commit()
            return
        }
        val alarmManager = try {
            context.getSystemService(AlarmManager::class.java)
        } catch (error: RuntimeException) {
            warn("Could not access AlarmManager.", error)
            null
        }
        if (alarmManager == null) {
            preferences.edit().remove(KEY_SCHEDULED_AT).commit()
            return
        }
        try {
            alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent(context))
        } catch (error: RuntimeException) {
            warn("Could not schedule widget boundary alarm.", error)
            try {
                alarmManager.cancel(pendingIntent(context))
            } catch (cancelError: RuntimeException) {
                warn("Could not cancel failed widget boundary alarm.", cancelError)
            }
            preferences.edit().remove(KEY_SCHEDULED_AT).commit()
            return
        }
        preferences.edit().putLong(KEY_SCHEDULED_AT, triggerAt).commit()
    }

    private fun cancelAlarm(context: Context) {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        } catch (error: RuntimeException) {
            warn("Could not cancel widget boundary alarm.", error)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, KaniWidgetRefreshReceiver::class.java)
            .setAction(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH)
            .setPackage(context.packageName)
            .putExtra(EXTRA_BOUNDARY_TRIGGER, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun android.content.SharedPreferences.Editor.putOrRemove(key: String, value: Long) =
        if (value > 0L) putLong(key, value) else remove(key)

    private fun warn(message: String, error: Throwable) {
        try {
            Log.w("KaniWidgetBoundary", message, error)
        } catch (_: RuntimeException) {
            // Android Log is unavailable in local JVM tests.
        }
    }
}
