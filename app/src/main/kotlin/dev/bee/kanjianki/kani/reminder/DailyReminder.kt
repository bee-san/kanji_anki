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
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** One quiet notification a day, only when something is due. */
object DailyReminder {
    private const val WORK = "kani_daily_reminder"
    private const val CHANNEL = "kani_daily"
    private const val NOTIFICATION_ID = 7001

    fun sync(context: Context, prefs: KaniPrefs = KaniPrefs(context)) {
        val wm = WorkManager.getInstance(context)
        if (!prefs.reminder || !prefs.setupDone) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atStartOfDay().plusMinutes(prefs.reminderMinuteOfDay.toLong())
        if (!at.isAfter(now)) at = at.plusDays(1)
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
            val store = KaniStore(applicationContext)
            val due = SessionPlanner.due(store.allKanji(), emptyMap(), LocalDate.now().toEpochDay().toInt()).size
            store.close()
            if (due > 0) notify(applicationContext, due.coerceAtMost(SessionPlanner.sizeFor(prefs.minutes)))
            sync(applicationContext, prefs)
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
            context, 0, Intent(context, KaniActivity::class.java),
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
