package dev.bee.kanjianki

import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler

internal class MainActivityStartup(private val activity: MainActivityBase) {
    fun start() {
        activity.store = LocalStore(activity)
        activity.gateway = MainActivityBase.ankiDroidGatewayForTests ?: AnkiDroidGateway(activity)
        activity.requestAnkiPermissionIfNeeded()
        ReminderScheduler.schedule(activity)
        AutoSyncScheduler.schedule(activity)
        AutoUpdateScheduler.schedule(activity)
        DatabaseBackupScheduler.schedule(activity)
        activity.handleLaunchIntent(activity.intent)
    }
}
