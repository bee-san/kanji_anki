package dev.bee.kanjianki

import android.content.Intent
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler

internal class MainActivityStartup(private val activity: MainActivityBase) {
    fun start() {
        activity.store = LocalStore(activity)
        activity.gateway = MainActivityRuntimeOverrides.ankiDroidGateway ?: AnkiDroidGateway(activity)
        activity.requestAnkiPermissionIfNeeded()
        ReminderScheduler.schedule(activity)
        AutoSyncScheduler.schedule(activity)
        AutoUpdateScheduler.schedule(activity)
        DatabaseBackupScheduler.schedule(activity)
        handleLaunchIntent(activity.intent)
    }

    fun handleLaunchIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_UPDATE, false)) {
            activity.renderUpdate()
        } else {
            activity.renderHome()
        }
    }
}
