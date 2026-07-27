package dev.bee.kanjianki

import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyProjectionEligibilityPolicy
import dev.bee.kanjianki.data.RecentMistakeSnapshot
import dev.bee.kanjianki.data.StatsSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import kotlinx.coroutines.runBlocking

internal data class RecentMistakesRouteData(
    val mistakes: List<RecentMistakeSnapshot>,
    val rowsByKanji: Map<String, RecordsImportModels.DashboardRow>,
)

internal fun recentMistakesRouteData(
    snapshot: StatsSnapshot,
    rows: List<RecordsImportModels.DashboardRow>,
    items: List<RecordsStudyModels.StudyItem>,
    settings: RecordsSyncModels.Settings,
): RecentMistakesRouteData {
    val historicalMistakes = snapshot.recentMistakes
    if (historicalMistakes.isEmpty()) {
        return RecentMistakesRouteData(emptyList(), emptyMap())
    }
    val rowsByKanji = rows.associateBy { it.kanji }
    val evidenceStatusByKanji = snapshot.kanjiRepairEvidence.associate {
        it.kanji to it.status
    }
    val eligibleKanji = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
        rows,
        items,
        settings,
        evidenceStatusByKanji,
    )
    val mistakes = historicalMistakes.filter { eligibleKanji.contains(it.kanji) }
    return RecentMistakesRouteData(mistakes, if (mistakes.isEmpty()) emptyMap() else rowsByKanji)
}

internal class MainActivityHomeFocusQueue(private val home: MainActivityHome) {
    private val studyQueueCompatibility by lazy {
        StudyQueueCompatibilityAccess(home)
    }
    fun renderFocusQueue() {
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.focusQueueTitle(),
            traceName = "focus-queue-route",
            load = {
                val now = System.currentTimeMillis()
                val snapshot = runBlocking { home.homeUseCases.loadRoute(now) }
                val rows = snapshot.home.activeRows
                val items = snapshot.home.studyItems
                val plan = if (rows.isEmpty()) {
                    null
                } else {
                    homeStudyPlan(snapshot, rows, items, now)
                }
                val entries = if (rows.isEmpty()) {
                    emptyList()
                } else {
                    queuedEntries(
                        rows,
                        items,
                        now,
                        plan,
                        snapshot.study.studyAheadMinutes,
                        snapshot.study.studyLadder,
                    )
                }

                HomeFocusQueueScreenModel(
                    title = HomeTextCopy.focusQueueTitle(),
                    homeLabel = HomeTextCopy.homeLabel(),
                    onHome = home::renderHome,
                    queue = homeFocusQueuePanelModel(
                        rows = rows,
                        entries = entries,
                        nowMillis = now,
                        plan = plan,
                        matureSupportThreshold = if (rows.isEmpty()) {
                            0
                        } else {
                            snapshot.settings.sync.matureSupportThreshold
                        },
                    ) { kanji -> home.renderDetail(kanji, false, "") },
                    onSync = home::confirmSync
                )
            },
            render = { model ->
                home.renderHomeRoute(backAction = Runnable { home.renderHome() }) {
                    HomeFocusQueueScreen(model)
                }
            },
        )
    }

    private fun homeStudyPlan(
        snapshot: dev.bee.kanjianki.application.HomeRouteSnapshot,
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan = MainActivityStudyPlanProvider(home).adaptivePlan(
        rows = rows,
        items = items,
        now = now,
        streakDays = snapshot.study.studyStreak.currentDays,
        settings = snapshot.settings.sync,
        reviewStats = snapshot.study.recentReviewStats,
        studiedKanji = snapshot.study.studiedKanjiToday,
        workload = snapshot.study.adaptiveWorkload,
    )

    fun renderRecentMistakes() {
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.recentMistakesTitle(),
            traceName = "recent-mistakes-route",
            load = {
                val now = System.currentTimeMillis()
                val homeSnapshot = runBlocking { home.homeUseCases.loadRoute(now) }
                val statsSnapshot = runBlocking { home.statsUseCases.loadForDisplay(now) }
                recentMistakesRouteData(
                    statsSnapshot,
                    homeSnapshot.home.activeRows,
                    homeSnapshot.home.studyItems,
                    homeSnapshot.settings.sync,
                )
            },
            render = { data ->
                val mistakesModel = if (data.mistakes.isEmpty()) {
                    HomeRecentMistakesPanelModel(
                        emptyTitle = HomeTextCopy.noRecentMistakesTitle(),
                        emptyBody = HomeTextCopy.noRecentMistakesBody(),
                        cards = emptyList(),
                        emptyStyle = HomeEmptyStateStyle.LegacyBand
                    )
                } else {
                    homeRecentMistakesPanelModel(
                        mistakes = data.mistakes,
                        rowsByKanji = data.rowsByKanji,
                    ) { kanji -> home.renderDetail(kanji, false, "") }
                }
                val model = HomeRecentMistakesScreenModel(
                    title = HomeTextCopy.recentMistakesTitle(),
                    homeLabel = HomeTextCopy.homeLabel(),
                    onHome = home::renderHome,
                    mistakes = mistakesModel
                )
                home.renderHomeRoute(backAction = Runnable { home.renderHome() }) {
                    HomeRecentMistakesScreen(model)
                }
            },
        )
    }

    fun streakAccent(streak: StudyStreakSnapshot?): Int {
        return if (streak != null && streak.studiedToday) {
            MainActivityBase.GOLD
        } else {
            MainActivityBase.MUTED
        }
    }

    fun studyAheadMillis(): Long {
        return studyQueueCompatibility.studyAheadMillis()
    }

    fun studyQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        now: Long,
        persist: Boolean,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        currentItems: List<RecordsStudyModels.StudyItem>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        return studyQueueCompatibility.studyQueue(rows, now, persist, plan, currentItems)
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): List<MainActivityBase.QueueEntry> {
        return FocusQueuePolicy.queuedEntries(
            rows,
            items,
            now,
            studyAheadMillis(),
            plan,
            home.studyLadderSettings(),
        )
            .map { entry -> MainActivityBase.QueueEntry(entry.row, entry.item) }
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        studyAheadMinutes: Int,
        ladder: dev.bee.kanjianki.core.RecordsBase.StudyLadderSettings,
    ): List<MainActivityBase.QueueEntry> {
        return FocusQueuePolicy.queuedEntries(
            rows,
            items,
            now,
            studyAheadMinutes * 60_000L,
            plan,
            ladder,
        ).map { entry -> MainActivityBase.QueueEntry(entry.row, entry.item) }
    }

    fun rowColor(item: RecordsStudyModels.StudyItem, now: Long): Int {
        return when (FocusQueuePolicy.rowTone(item, now)) {
            FocusQueuePolicy.QueueTone.DUE -> MainActivityBase.CORAL
            FocusQueuePolicy.QueueTone.LEARNING -> MainActivityBase.BLUE
            else -> MainActivityUiSupport.PINK_STROKE
        }
    }

}
