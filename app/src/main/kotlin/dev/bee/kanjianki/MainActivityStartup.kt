package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler
import dev.bee.kanjianki.fsrs.FsrsFitScheduler

internal fun focusKanjiDetailFromIntent(intent: Intent?): String? = TextUtil.normalizeSingleKanji(
    runCatching { intent?.getStringExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL) }.getOrNull(),
).takeIf(String::isNotEmpty)

internal class MainActivityStartup(private val activity: MainActivityBase) {
    fun start() {
        val launchIntent = activity.intent

        activity.store = AppLocalStoreFactory.create(activity)
        activity.gateway = MainActivityRuntimeOverrides.ankiDroidGateway ?: AnkiDroidGateway(activity)

        val runBackgroundTasks = shouldRunBackgroundStartupTasks(launchIntent)
        // Counted down once the first DB open + any one-time migration has run on io. The
        // maintenance block waits on this before opening its own LocalStore instances so two
        // SQLiteOpenHelper instances never contend for the write lock during the same onUpgrade
        // on an upgrade boot. Route loads on io are NOT gated by this, so the cold-boot win holds.
        val migrationReady = CountDownLatch(1)
        if (runBackgroundTasks) {
            // Warm the in-memory theme cache first on the io executor. Route
            // composition reads the theme non-blocking on the main thread, so this
            // (or the per-route warm in MainActivityHome) must run before the first
            // real route render for the user's theme to apply. Queuing it first also
            // means any pending DB migration runs here, off the main thread, before
            // the home model load that is queued next.
            activity.io.execute {
                try {
                    withUiTrace("kani.startup.db-open-theme-warm") {
                        runCatching { activity.store.appThemeChoice() }
                    }
                } finally {
                    migrationReady.countDown()
                }
            }
            // Heavy asset parsing (9.5 MB stroke TSV + dictionary install/hash) gets
            // its own thread: it used to share the single-threaded io executor and
            // serialized the first home load behind seconds of asset warmup.
            warmHeavyAssetsOnOwnThread()
        } else {
            migrationReady.countDown()
        }
        // Queue the launch route load before the maintenance schedulers so the first
        // screen the user sees is not waiting behind WorkManager/AlarmManager setup.
        handleLaunchIntent(launchIntent)
        if (runBackgroundTasks) {
            // Runs on the maintenance executor, NOT io: this block does first-time WorkManager
            // init and scheduler setup that used to sit in the io queue directly behind the first
            // route load, stalling every screen the user tapped during cold boot. Reminder
            // evaluation is intentionally absent: the lifecycle coordinator runs it only after
            // the accepted launch route settles, reusing the activity store's warmed caches.
            // Each remaining scheduler is traced separately so the debug log shows which is slow.
            activity.maintenance.execute {
                // Ensure the io theme-warm has opened/migrated the DB before these scheduler-owned
                // LocalStore instances open theirs, avoiding a concurrent-migration write-lock race.
                runCatching { migrationReady.await(MIGRATION_WAIT_SECONDS, TimeUnit.SECONDS) }
                withUiTrace("kani.startup.auto-sync-scheduler") { AutoSyncScheduler.schedule(activity) }
                withUiTrace("kani.startup.auto-update-scheduler") { AutoUpdateScheduler.schedule(activity) }
                withUiTrace("kani.startup.backup-scheduler") { DatabaseBackupScheduler.schedule(activity) }
                withUiTrace("kani.startup.fsrs-fit-scheduler") { FsrsFitScheduler.schedule(activity) }
            }
        }
    }

    fun handleLaunchIntent(intent: Intent?) {
        val benchmarkRoute = intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE)?.takeIf { it.isNotBlank() }
        if (benchmarkRoute != null) {
            activity.screenshotThemeChoiceOverride = null
            seedBenchmarkFixtureForDebugBuild()
            renderHarnessRoute(benchmarkRoute)
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
            renderHarnessRoute(screenshotRoute)
            return
        }
        activity.preserveStudyRecoveryForHarnessRoute = false
        activity.screenshotThemeChoiceOverride = null
        val opensKanjiDetail = intent?.hasExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL) == true
        val focusKanji = if (opensKanjiDetail) focusKanjiDetailFromIntent(intent) else null
        val opensHome = intent?.getBooleanExtra(MainActivityBase.EXTRA_OPEN_HOME, false) == true
        val opensStudy = intent?.getBooleanExtra(MainActivityBase.EXTRA_OPEN_STUDY, false) == true
        val opensUpdate = intent?.getBooleanExtra(MainActivityBase.EXTRA_OPEN_UPDATE, false) == true
        val opensStats = intent?.getBooleanExtra(MainActivityBase.EXTRA_OPEN_STATS, false) == true
        val shortcutDestination = launcherShortcutDestination(intent?.action)
        consumeProductionNavigation(intent, shortcutDestination != null)
        val study = activity as? MainActivityStudy
        val restoreStudyRoute = activity.restoreStudyRouteOnCreate
        val recreatedRoute = activity.restoreRouteOnCreate
        val recreatedHomeRoute = activity.restoreHomeRouteOnCreate
        activity.restoreStudyRouteOnCreate = false
        activity.restoreRouteOnCreate = null
        activity.restoreHomeRouteOnCreate = null
        if (restoreStudyRoute && study?.restoreStudyRouteAfterRecreation() == true) {
            return
        }
        if (recreatedRoute != null) {
            renderRoute(recreatedRoute)
            return
        }
        if (recreatedHomeRoute != null && activity is MainActivityHome) {
            activity.renderRestoredHomeRoute(recreatedHomeRoute)
            return
        }
        when {
            opensKanjiDetail -> {
                activity.disableStudyOrdinaryResume()
                if (focusKanji == null || !activity.openFocusKanjiDetail(focusKanji)) {
                    activity.renderHome()
                }
            }

            opensStudy -> {
                activity.renderStudy()
            }

            opensUpdate -> {
                activity.disableStudyOrdinaryResume()
                activity.renderUpdate()
            }

            opensStats -> {
                activity.disableStudyOrdinaryResume()
                if (activity is MainActivityHome) activity.renderStats() else activity.renderHome()
            }

            opensHome -> {
                activity.disableStudyOrdinaryResume()
                activity.renderHome()
            }

            shortcutDestination != null -> renderLauncherShortcut(shortcutDestination)

            study?.shouldResumeStudyOnOrdinaryLaunch() == true -> {
                study.renderStudyRecoveryOnly()
            }

            else -> activity.renderHome()
        }
    }

    private fun consumeProductionNavigation(intent: Intent?, consumedShortcut: Boolean) {
        intent ?: return
        intent.removeExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL)
        intent.removeExtra(MainActivityBase.EXTRA_OPEN_HOME)
        intent.removeExtra(MainActivityBase.EXTRA_OPEN_STUDY)
        intent.removeExtra(MainActivityBase.EXTRA_OPEN_UPDATE)
        intent.removeExtra(MainActivityBase.EXTRA_OPEN_STATS)
        if (consumedShortcut) {
            intent.action = Intent.ACTION_MAIN
        }
    }

    private fun renderLauncherShortcut(destination: LauncherShortcutDestination) {
        when (destination) {
            LauncherShortcutDestination.STUDY -> activity.renderStudy()
            LauncherShortcutDestination.BROWSE -> {
                activity.disableStudyOrdinaryResume()
                if (activity is MainActivityHome) {
                    activity.renderBrowseKanji("")
                } else {
                    activity.renderHome()
                }
            }
            LauncherShortcutDestination.GAMES -> {
                activity.disableStudyOrdinaryResume()
                if (activity is MainActivityHome) {
                    activity.renderGames()
                } else {
                    activity.renderHome()
                }
            }
        }
    }

    private fun renderHarnessRoute(route: String) {
        activity.preserveStudyRecoveryForHarnessRoute = true
        renderRoute(route)
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
            withUiTrace("kani.startup.warm-stroke-guides") { runCatching { activity.warmStrokeGuides() } }
            withUiTrace("kani.startup.warm-dictionary") { runCatching { activity.warmDictionaryLookup() } }
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
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsImportSync() ?: activity.renderSettings()
            MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsStudyBehavior() ?: activity.renderSettings()
            MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsAutomation() ?: activity.renderSettings()
            MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsAppearance() ?: activity.renderSettings()
            MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE ->
                (activity as? MainActivitySettings)?.renderSettingsDisplayData() ?: activity.renderSettings()
            MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE -> activity.renderUpdate()
            MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE ->
                (activity as? MainActivitySettings)?.renderReferenceDataDetails() ?: activity.renderSettings()
            MainActivityBase.NAV_SETTINGS_HOW_IT_WORKS_ROUTE ->
                (activity as? MainActivitySettings)?.renderHowItWorks() ?: activity.renderSettings()
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
        // Upper bound the maintenance block's wait for the io migration. Normal boots count down
        // in milliseconds; the cap only bounds a pathological stall (never observed) so background
        // maintenance still runs rather than hanging the executor forever.
        private const val MIGRATION_WAIT_SECONDS = 20L

        internal fun backgroundStartupTasksAllowed(intent: Intent?): Boolean {
            return intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE).isNullOrBlank() &&
                intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE).isNullOrBlank()
        }
    }
}

internal enum class LauncherShortcutDestination {
    STUDY,
    BROWSE,
    GAMES,
}

internal fun launcherShortcutDestination(action: String?): LauncherShortcutDestination? {
    return when (action) {
        MainActivityBase.ACTION_OPEN_STUDY -> LauncherShortcutDestination.STUDY
        MainActivityBase.ACTION_OPEN_BROWSE -> LauncherShortcutDestination.BROWSE
        MainActivityBase.ACTION_OPEN_GAMES -> LauncherShortcutDestination.GAMES
        else -> null
    }
}
