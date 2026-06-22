package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore

internal data class RecentMistakesRouteData(
    val mistakes: List<StudyStatsStore.RecentMistake>,
    val rowsByKanji: Map<String, RecordsImportModels.DashboardRow>,
)

internal interface RecentMistakesRouteDataSource {
    fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake>
    fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow>
}

internal fun recentMistakesRouteData(source: RecentMistakesRouteDataSource): RecentMistakesRouteData {
    val snapshot = source.cachedStatsSnapshotOrNull() ?: source.latestStatsSnapshotOrNull()
    val mistakes = if (snapshot != null && snapshot.cacheFormatVersion >= STATS_CACHE_FORMAT_VERSION) {
        snapshot.recentMistakes
    } else {
        source.recentMistakes(STATS_RECENT_MISTAKE_LIMIT)
    }
    val rowsByKanji = if (mistakes.isEmpty()) {
        emptyMap()
    } else {
        source.activeDashboardRowsByKanji()
    }
    return RecentMistakesRouteData(mistakes, rowsByKanji)
}

internal class MainActivityHomeFocusQueue(private val home: MainActivityHome) {
    fun renderFocusQueue() {
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.focusQueueTitle(),
            traceName = "focus-queue-route",
            load = {
                val now = System.currentTimeMillis()
                val rows = home.store.activeDashboardRows()
                val items = if (rows.isEmpty()) {
                    emptyList()
                } else {
                    home.store.studyItemsForKanji(rows.map { it.kanji })
                }
                val plan = if (rows.isEmpty()) null else home.adaptivePlan(rows, items, now)
                val entries = if (rows.isEmpty()) {
                    emptyList()
                } else {
                    home.queuedEntries(rows, items, now, plan)
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
                        matureSupportThreshold = if (rows.isEmpty()) 0 else home.settings().matureSupportThreshold,
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

    fun renderRecentMistakes() {
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.recentMistakesTitle(),
            traceName = "recent-mistakes-route",
            load = {
                recentMistakesRouteData(
                    object : RecentMistakesRouteDataSource {
                        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                            return home.store.cachedStatsSnapshotOrNull()
                        }

                        override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                            return home.store.latestStatsSnapshotOrNull()
                        }

                        override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
                            return home.store.recentMistakes(limit)
                        }

                        override fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow> {
                            return home.store.activeDashboardRowsByKanji()
                        }
                    }
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

    fun streakAccent(streak: StudyStatsStore.StudyStreak?): Int {
        return if (streak != null && streak.studiedToday) {
            MainActivityBase.GOLD
        } else {
            MainActivityBase.MUTED
        }
    }

    fun studyAheadMillis(): Long {
        return home.store.studyAheadMinutes() * 60_000L
    }

    fun studyQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        now: Long,
        persist: Boolean,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        currentItems: List<RecordsStudyModels.StudyItem>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        val scheduler = BridgeScheduler()
        return HomeStudyQueueActions.studyQueue(
            HomeStudyQueueActions.StudyQueueRequest(
                rows,
                now,
                persist,
                plan,
                home.store::studyItems,
                home::settings,
                home::startOfDay,
                home::studyLadderSettings,
                home::adaptivePlan,
                scheduler::seedQueue,
                object : HomeStudyQueueActions.StudyItemsWriter {
                    override fun annotateSimilarKanjiAvailability(
                        items: List<RecordsStudyModels.StudyItem>,
                    ): List<RecordsStudyModels.StudyItem> {
                        return home.store.annotateSimilarKanjiAvailability(items)
                    }

                    override fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
                        home.store.replaceStudyItems(items)
                    }
                },
            ),
            currentItems,
        )
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): List<MainActivityBase.QueueEntry> {
        return FocusQueuePolicy.queuedEntries(rows, items, now, studyAheadMillis(), plan, home.studyLadderSettings())
            .map { entry -> MainActivityBase.QueueEntry(entry.row, entry.item) }
    }

    fun rowColor(item: RecordsStudyModels.StudyItem, now: Long): Int {
        return when (FocusQueuePolicy.rowTone(item, now)) {
            FocusQueuePolicy.QueueTone.DUE -> MainActivityBase.CORAL
            FocusQueuePolicy.QueueTone.LEARNING -> MainActivityBase.BLUE
            else -> MainActivityUiSupport.PINK_STROKE
        }
    }

}
