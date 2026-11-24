package dev.bee.kanjianki.kani.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.R
import dev.bee.kanjianki.kani.KaniActivity
import dev.bee.kanjianki.kani.core.SessionPlanner
import dev.bee.kanjianki.kani.data.KaniPrefs
import dev.bee.kanjianki.kani.data.KaniStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** One quiet notification a day, only when something is due. */
object DailyReminder {
    private const val WORK = "kani_daily_reminder"
    private const val CHANNEL = "kani_daily"
    private const val NOTIFICATION_ID = 7001
    private const val MINUTES_PER_DAY = 24 * 60

    /** Request codes keep the reminder's and the widget's PendingIntents apart (extras don't). */
    const val REQUEST_REMINDER = 1
    const val REQUEST_WIDGET = 2

    fun sync(context: Context, prefs: KaniPrefs = KaniPrefs(context)) {
        val wm = WorkManager.getInstance(context)
        if (!prefs.reminder || !prefs.setupDone) {
            wm.cancelUniqueWork(WORK)
            return
        }
        // Zoned, so the reminder keeps its clock time on the days the clocks change.
        val now = ZonedDateTime.now()
        val time = LocalTime.ofSecondOfDay(prefs.reminderMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) * 60L)
        var at = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!at.isAfter(now)) at = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        val delay = Duration.between(now, at).toMillis()
        wm.enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val prefs = KaniPrefs(applicationContext)
            try {
                val store = KaniStore(applicationContext)
                val due = try {
                    // The model's cached curves: solid kanji it says are slipping count as due too.
                    SessionPlanner.count(
                        store.allKanji(), emptyMap(), LocalDate.now().toEpochDay().toInt(), prefs.minutes,
                        store.recall(System.currentTimeMillis()),
                    )
                } finally {
                    store.close()
                }
                if (due > 0) notify(applicationContext, due)
            } catch (error: Exception) {
                // A locked database today must not end tomorrow's reminder: skip this one only.
                android.util.Log.w("KaniReminder", "Daily reminder skipped.", error)
            } finally {
                sync(applicationContext, prefs)
            }
            return Result.success()
        }
    }

    private fun notify(context: Context, due: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily session", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, REQUEST_REMINDER, Intent(context, KaniActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_kani_seal_mono)
            .setContentTitle(if (due == 1) "1 kanji you keep missing" else "$due kanji you keep missing")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }
}
