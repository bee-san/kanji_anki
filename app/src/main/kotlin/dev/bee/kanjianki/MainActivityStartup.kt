package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.host.KaniLaunchIntents
import dev.bee.kanjianki.presentation.KaniLaunchCodec
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler
import dev.bee.kanjianki.fsrs.FsrsFitScheduler

internal fun focusKanjiDetailFromIntent(intent: Intent?): String? = TextUtil.normalizeSingleKanji(
    KaniLaunchIntents.kanjiIn(intent),
).takeIf(String::isNotEmpty)

internal class MainActivityStartup(private val activity: MainActivityBase) {
    fun start() {
        val launchIntent = activity.intent

        activity.attachProcessDependencies(activity.requireKaniContainer())

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
            // the accepted launch route settles, reusing the process store's warmed caches.
            // Each remaining scheduler is traced separately so the debug log shows which is slow.
            activity.maintenance.execute {
                // Ensure the io theme-warm has opened/migrated the process store before scheduler
                // setup reads it or WorkManager can start operation-scoped helpers.
                runCatching { migrationReady.await(MIGRATION_WAIT_SECONDS, TimeUnit.SECONDS) }
                withUiTrace("kani.startup.auto-sync-scheduler") {
                    AutoSyncScheduler.schedule(activity, activity.store, activity.store.autoSyncSettings())
                }
                withUiTrace("kani.startup.auto-update-scheduler") {
                    AutoUpdateScheduler.schedule(activity, activity.store)
                }
                withUiTrace("kani.startup.backup-scheduler") { DatabaseBackupScheduler.schedule(activity) }
                withUiTrace("kani.startup.fsrs-fit-scheduler") {
                    FsrsFitScheduler.schedule(activity, activity.store)
                }
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
        // Which screen an external launch asked for is decided by the shared codec,
        // not by the order of the branches below: the desktop host has the same
        // widgets, notifications, and shortcuts to honor, and two hosts each
        // re-deriving the precedence from the same prose is how they drift.
        val target = KaniLaunchCodec.resolve(launchTargetsPresentIn(intent))
        val focusKanji = if (target == KaniLaunchCodec.Target.KANJI_DETAIL) {
            focusKanjiDetailFromIntent(intent)
        } else {
            null
        }
        consumeProductionNavigation(
            intent,
            consumedShortcut = target != null && launcherShortcutTarget(intent?.action) == target,
        )
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
        if (target != null) {
            // Read off the target, not the branch taken, so the invalid-kanji
            // fallback below suppresses resume exactly like the happy path.
            if (target.suppressesStudyResume) activity.disableStudyOrdinaryResume()
            renderLaunchTarget(target, focusKanji)
            return
        }
        if (study?.shouldResumeStudyOnOrdinaryLaunch() == true) {
            study.renderStudyRecoveryOnly()
            return
        }
        activity.renderHome()
    }

    /**
     * Renders [target], falling back to Home where this activity cannot show it.
     *
     * Still a render-method table rather than shell state: the Android host keeps
     * its Activity-inheritance rendering until Goal 200 replaces it. What moved out
     * is only *which* screen was asked for.
     */
    private fun renderLaunchTarget(
        target: KaniLaunchCodec.Target,
        focusKanji: String?,
    ) {
        when (target) {
            KaniLaunchCodec.Target.KANJI_DETAIL ->
                if (focusKanji == null || !activity.openFocusKanjiDetail(focusKanji)) {
                    activity.renderHome()
                }

            KaniLaunchCodec.Target.STUDY -> activity.renderStudy()
            KaniLaunchCodec.Target.UPDATE -> activity.renderUpdate()
            KaniLaunchCodec.Target.STATS ->
                if (activity is MainActivityHome) activity.renderStats() else activity.renderHome()

            KaniLaunchCodec.Target.BROWSE ->
                if (activity is MainActivityHome) {
                    activity.renderBrowseKanji("")
                } else {
                    activity.renderHome()
                }

            KaniLaunchCodec.Target.GAMES ->
                if (activity is MainActivityHome) activity.renderGames() else activity.renderHome()

            KaniLaunchCodec.Target.HOME -> activity.renderHome()
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
            MainActivityBase.SCREENSHOT_MISSING_KANJI_ROUTE ->
                if (activity is MainActivityHome) activity.renderMissingKanji() else activity.renderHome()
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

        /**
         * The targets [intent] names, for the codec to arbitrate between.
         *
         * Delegates to [KaniLaunchIntents], which owns the durable wire format for both
         * hosts. Kept as a named entry point because the instrumentation suite drives it,
         * and because two readers of the same extras would be free to disagree about which
         * ones count.
         */
        internal fun launchTargetsPresentIn(intent: Intent?): Set<KaniLaunchCodec.Target> =
            KaniLaunchIntents.targetsIn(intent)

        internal fun backgroundStartupTasksAllowed(intent: Intent?): Boolean {
            return intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE).isNullOrBlank() &&
                intent?.getStringExtra(MainActivityBase.EXTRA_BENCHMARK_ROUTE).isNullOrBlank()
        }
    }
}

/**
 * The launch target a launcher-shortcut action names, or `null` for any other
 * action.
 *
 * Still an allowlist, and still deliberately narrow: `Intent.action` is
 * caller-controlled, so only these three actions may pick a screen, and everything
 * else — including `ACTION_MAIN` — falls through to the ordinary launch path.
 */
internal fun launcherShortcutTarget(action: String?): KaniLaunchCodec.Target? =
    KaniLaunchIntents.shortcutTarget(action)
