package dev.bee.kanjianki

import android.content.Intent
import java.util.Locale
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler

internal class MainActivityStartup(private val activity: MainActivityBase) {
    fun start() {
        val launchIntent = activity.intent

        activity.store = LocalStore(activity)
        activity.gateway = MainActivityRuntimeOverrides.ankiDroidGateway ?: AnkiDroidGateway(activity)

        if (shouldRunBackgroundStartupTasks(launchIntent)) {
            activity.io.execute {
                ReminderScheduler.schedule(activity)
                AutoSyncScheduler.schedule(activity)
                AutoUpdateScheduler.schedule(activity)
                DatabaseBackupScheduler.schedule(activity)
            }
        }
        handleLaunchIntent(launchIntent)
    }

    fun handleLaunchIntent(intent: Intent?) {
        val benchmarkRoute = intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE)?.takeIf { it.isNotBlank() }
        if (benchmarkRoute != null) {
            activity.screenshotThemeChoiceOverride = null
            renderRoute(benchmarkRoute)
            return
        }
        val screenshotRoute = intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)?.takeIf { it.isNotBlank() }
        if (screenshotRoute != null) {
            activity.screenshotLocaleTag()?.let(::applyScreenshotLocale)
            val screenshotThemeChoice = screenshotThemeChoiceOrNull(intent.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_THEME))
            activity.screenshotThemeChoiceOverride = screenshotThemeChoice
            screenshotThemeChoice?.let {
                activity.store.saveAppThemeChoice(it)
            }
            renderRoute(screenshotRoute)
            return
        }
        activity.screenshotThemeChoiceOverride = null
        if (intent != null && intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_UPDATE, false)) {
            activity.renderUpdate()
        } else {
            activity.renderHome()
        }
    }

    internal fun shouldRunBackgroundStartupTasks(intent: Intent?): Boolean {
        return intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE).isNullOrBlank() &&
            intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE).isNullOrBlank()
    }

    private fun renderRoute(route: String) {
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

    private fun applyScreenshotLocale(localeTag: String) {
        Locale.setDefault(Locale.forLanguageTag(localeTag.replace('_', '-')))
    }
}
