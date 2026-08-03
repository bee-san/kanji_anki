package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.StudyRouteRender
import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.application.StatsUseCases
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.StatsDashboard
import dev.bee.kanjianki.progress.progressAnalyticsSnapshot

private const val MINUTE_MILLIS = 60_000L

/**
 * The provider facts a route load needs, as the portable projection both hosts share.
 *
 * Desktop derives it from an AnkiConnect handshake and Android from the AnkiDroid
 * gateway status, but the loader only needs three things from either: whether sync is
 * offerable ([isReady]), the onboarding [readiness]/[message], and the capability set.
 * Passing this rather than a host probe type is what lets one loader serve both.
 */
data class HostProviderStatus(
    val readiness: ProviderReadiness,
    val message: String,
    val isReady: Boolean,
    val capabilities: Set<PlatformCapability>,
)

/**
 * Builds one [KaniRouteContent] for a destination from the shared use-case graph.
 *
 * This is the orchestration that was `loadDesktopRoute`, lifted out of the desktop
 * scaffold so the Android host runs the identical assembly instead of a second one —
 * the whole point of Goal 199. It is pure of Compose, dispatchers, and clocks: the
 * caller supplies [nowMillis], the provider [status], and the current Study/Games
 * renders (owned by the host's runtimes), and gets back the portable content the shared
 * shell renders. One snapshot for every route; Browse and Detail do the one extra read
 * their destination parameterizes.
 */
class KaniRouteLoader(
    private val homeUseCases: HomeUseCases,
    private val statsUseCases: StatsUseCases,
    private val settingsUseCases: SettingsUseCases,
    private val deviceSettings: () -> DeviceSettingsReader,
    private val annotateCapabilities: (List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>) -> List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>,
) {
    suspend fun load(
        destination: KaniDestination,
        status: HostProviderStatus,
        nowMillis: Long,
        studyRender: StudyRouteRender?,
        gamesRender: GamesRender?,
    ): KaniRouteContent {
        val snapshot = homeUseCases.loadRoute(nowMillis)
        val study = snapshot.study
        val settings = snapshot.settings.sync
        val streak = streakOf(study)
        val due = ReminderEligibilityPolicy
            .eligibleDueTimes(study.studyItems, study.activeRows, study.studyLadder)
            .count { dueAt -> dueAt <= nowMillis }

        val dailyPlan = DesktopHomeModels.dailyPlan(
            study = study,
            streak = streak,
            latestSuccessfulSyncAtMillis = study.latestSuccessfulSyncAtMillis,
            consecutiveFailedSyncs = snapshot.home.consecutiveFailedSyncs,
            deviceSettings = deviceSettings(),
            nowMillis = nowMillis,
        )
        val adaptivePlan = DesktopHomeModels.adaptivePlan(study, settings, streak, nowMillis)
        val entries = if (study.activeRows.isEmpty()) {
            emptyList()
        } else {
            FocusQueuePolicy.queuedEntries(
                study.activeRows,
                study.studyItems,
                nowMillis,
                study.studyAheadMinutes * MINUTE_MILLIS,
                adaptivePlan,
                study.studyLadder,
            )
        }
        val repairedKanjiCount = snapshot.repairedWriteBackProposal?.repairedKanji?.size ?: 0

        val home = HomeDashboard(
            readiness = status.readiness,
            metrics = DesktopHomeModels.metrics(
                sync = snapshot.home.latestSync,
                streak = streak,
                canSync = status.isReady,
                dailyPlan = dailyPlan,
                plan = adaptivePlan,
            ),
            todayPlan = DesktopHomeModels.todayPlan(dailyPlan),
            deckOverview = DesktopHomeModels.deckOverview(study, nowMillis, study.locallySuspendedKanji),
            focus = DesktopHomeModels.focusQueue(
                rows = study.activeRows,
                entries = entries,
                plan = adaptivePlan,
                nowMillis = nowMillis,
                matureSupportThreshold = settings.matureSupportThreshold,
            ),
            repairedKanjiCount = repairedKanjiCount,
            studyRemainingCount = DesktopHomeModels.studyRemainingCount(
                study = study,
                settings = settings,
                plan = adaptivePlan,
                dueLegacyWritingRepairs = snapshot.home.dueLegacyWritingRepairs,
                annotate = annotateCapabilities,
                nowMillis = nowMillis,
            ),
            // No host observes an in-flight sync engine from this path yet (Goal 202
            // on desktop; Android's sync runs elsewhere), so False is the honest value.
            syncing = false,
        )

        return KaniRouteContent(
            providerMessage = status.message,
            studyItemCount = study.studyItems.size,
            dueCount = due,
            themeChoice = snapshot.settings.themeChoice,
            home = home,
            onboarding = DesktopHomeModels.onboarding(
                readiness = status.readiness,
                guidance = status.message,
                settings = settings,
                latestSync = snapshot.home.latestSync,
                repairedKanjiCount = repairedKanjiCount,
            ),
            browse = loadBrowse(destination),
            detail = loadDetail(destination, settings.matureSupportThreshold, nowMillis),
            study = studyRender?.let {
                DesktopStudyModel.session(it.session, it.routeSnapshot, it.undoable, it.choicePrompt)
            },
            stats = loadStats(destination, nowMillis),
            games = gamesRender?.let(DesktopGamesModel::screen),
            settings = (destination as? KaniDestination.Settings)?.let {
                DesktopSettingsModel.screen(it.section, snapshot.settings)
            },
        )
    }

    private suspend fun loadBrowse(destination: KaniDestination): BrowseResults {
        val browse = destination as? KaniDestination.Browse ?: return BrowseResults()
        val items = homeUseCases.searchStudyInventory(
            query = browse.query,
            onlySimilarKanji = browse.onlySimilarKanji,
            includeLocallySuspended = browse.showSuspended,
        )
        return DesktopHomeModels.browse(
            items = items,
            query = browse.query,
            onlySimilarKanji = browse.onlySimilarKanji,
            allKanjiScope = browse.allKanjiScope,
            showSuspended = browse.showSuspended,
        )
    }

    private suspend fun loadDetail(
        destination: KaniDestination,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): KanjiDetail? {
        val detail = destination as? KaniDestination.Detail ?: return null
        val snapshot = homeUseCases.loadKanjiDetail(detail.kanji, nowMillis)
        return DesktopDetailModel.detail(detail.kanji, snapshot, matureSupportThreshold, nowMillis)
    }

    private suspend fun loadStats(destination: KaniDestination, nowMillis: Long): StatsDashboard? {
        if (destination != KaniDestination.Stats) return null
        val snapshot = statsUseCases.loadForDisplay(nowMillis)
        val ladder = settingsUseCases.load().studyLadder
        return DesktopStatsModel.dashboard(progressAnalyticsSnapshot(snapshot, nowMillis, ladder))
    }

    private fun streakOf(study: StudyQueueSnapshot): StudyStreakPolicy.Streak {
        val streak = study.studyStreak
        return StudyStreakPolicy.Streak(
            currentDays = streak.currentDays,
            bestDays = streak.bestDays,
            studiedToday = streak.studiedToday,
            reviewsToday = streak.reviewsToday,
            lastStudyAtMillis = streak.lastStudyAtMillis,
        )
    }
}
