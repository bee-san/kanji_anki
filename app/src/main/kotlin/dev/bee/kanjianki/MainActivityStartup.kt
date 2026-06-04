package dev.bee.kanjianki

import android.content.Intent
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler

internal class MainActivityStartup(private val activity: MainActivityHome) {
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
        val screenshotRoute = intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)?.takeIf { it.isNotBlank() }
        if (screenshotRoute != null) {
            renderScreenshotRoute(screenshotRoute)
            return
        }
        if (intent != null && intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_UPDATE, false)) {
            activity.renderUpdate()
        } else {
            activity.renderHome()
        }
    }

    private fun renderScreenshotRoute(route: String) {
        when (route) {
            MainActivityBase.NAV_HOME_ROUTE, "launcher-home", "narrow", "wide" -> activity.renderHome()
            MainActivityBase.NAV_STUDY -> activity.renderStudy()
            MainActivityBase.NAV_STATS_ROUTE -> if (activity is MainActivityHome) activity.renderStats() else activity.renderHome()
            MainActivityBase.NAV_SETTINGS_ROUTE -> activity.renderSettings()
            "games" -> if (activity is MainActivityHome) activity.renderGames() else activity.renderHome()
            "update" -> activity.renderUpdate()
            else -> activity.renderHome()
        }
    }
}
