package dev.bee.kanjianki

import android.content.Context
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

        val runBackgroundTasks = shouldRunBackgroundStartupTasks(launchIntent)
        if (runBackgroundTasks) {
            // Warm the in-memory theme cache first on the io executor. Route
            // composition reads the theme non-blocking on the main thread, so this
            // (or the per-route warm in MainActivityHome) must run before the first
            // real route render for the user's theme to apply. Queuing it first also
            // means any pending DB migration runs here, off the main thread, before
            // the home model load that is queued next.
            activity.io.execute { runCatching { activity.store.appThemeChoice() } }
            // Heavy asset parsing (9.5 MB stroke TSV + dictionary install/hash) gets
            // its own thread: it used to share the single-threaded io executor and
            // serialized the first home load behind seconds of asset warmup.
            warmHeavyAssetsOnOwnThread()
        }
        // Queue the launch route load before the maintenance schedulers so the first
        // screen the user sees is not waiting behind WorkManager/AlarmManager setup.
        handleLaunchIntent(launchIntent)
        if (runBackgroundTasks) {
            activity.io.execute {
                ReminderScheduler.schedule(activity)
                AutoSyncScheduler.schedule(activity)
                AutoUpdateScheduler.schedule(activity)
                DatabaseBackupScheduler.schedule(activity)
            }
        }
    }

    fun handleLaunchIntent(intent: Intent?) {
        val benchmarkRoute = intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE)?.takeIf { it.isNotBlank() }
        if (benchmarkRoute != null) {
            activity.screenshotThemeChoiceOverride = null
            seedBenchmarkFixtureForDebugBuild()
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
        return backgroundStartupTasksAllowed(intent)
    }

    /**
     * Warm heavy assets on a dedicated daemon thread so the first writing card and
     * first flashcard reveal do not parse them synchronously on tap, and so the
     * single-threaded io executor stays free for the first route load. First use
     * blocks on the same thread-safe init if warmup has not finished.
     */
    private fun warmHeavyAssetsOnOwnThread() {
        Thread({
            runCatching { activity.warmStrokeGuides() }
            runCatching { activity.warmDictionaryLookup() }
        }, "kani-asset-warmup").apply {
            isDaemon = true
            start()
        }
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

    private fun seedBenchmarkFixtureForDebugBuild() {
        val fixtureSeederClass = try {
            Class.forName("dev.bee.kanjianki.ButtonLatencyBenchmarkFixtureSeeder")
        } catch (_: ClassNotFoundException) {
            return
        }
        val seedResult = runCatching {
            fixtureSeederClass
                .getMethod("seedIfNeeded", Context::class.java, LocalStore::class.java)
                .invoke(null, activity, activity.store)
        }
        if (seedResult.isFailure) {
            throw IllegalStateException(
                "Unable to seed button latency benchmark fixture",
                seedResult.exceptionOrNull(),
            )
        }
    }

    private fun applyScreenshotLocale(localeTag: String) {
        Locale.setDefault(Locale.forLanguageTag(localeTag.replace('_', '-')))
    }

    companion object {
        internal fun backgroundStartupTasksAllowed(intent: Intent?): Boolean {
            return intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE).isNullOrBlank() &&
                intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE).isNullOrBlank()
        }
    }
}
