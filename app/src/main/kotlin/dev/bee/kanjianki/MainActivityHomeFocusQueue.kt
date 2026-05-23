package dev.bee.kanjianki

import android.graphics.Color
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

internal class MainActivityHomeFocusQueue(private val home: MainActivityHome) {
    fun renderFocusQueue() {
        val now = System.currentTimeMillis()
        val rows = home.store.activeDashboardRows()
        val items = home.studyQueue(rows, now, false, null)
        val plan = if (rows.isEmpty()) null else home.adaptivePlan(rows, items, now)
        val entries = if (rows.isEmpty()) {
            emptyList()
        } else {
            home.queuedEntries(rows, items, now, plan)
        }

        val model = HomeFocusQueueScreenModel(
            title = HomeTextCopy.focusQueueTitle(),
            homeLabel = HomeTextCopy.homeLabel(),
            onHome = home::renderHome,
            queue = homeFocusQueuePanelModel(home, rows, entries, now, plan),
            onSync = home::confirmSync
        )
        home.renderHomeRoute {
            HomeFocusQueueScreen(model)
        }
    }

    fun renderRecentMistakes() {
        val mistakes = home.store.recentMistakes(RECENT_MISTAKE_LIMIT)
        val mistakesModel = if (mistakes.isEmpty()) {
            HomeRecentMistakesPanelModel(
                emptyTitle = HomeTextCopy.noRecentMistakesTitle(),
                emptyBody = HomeTextCopy.noRecentMistakesBody(),
                cards = emptyList(),
                emptyStyle = HomeEmptyStateStyle.LegacyBand
            )
        } else {
            homeRecentMistakesPanelModel(home, mistakes, home.store.activeDashboardRows())
        }
        val model = HomeRecentMistakesScreenModel(
            title = HomeTextCopy.recentMistakesTitle(),
            homeLabel = HomeTextCopy.homeLabel(),
            onHome = home::renderHome,
            mistakes = mistakesModel
        )
        home.renderHomeRoute {
            HomeRecentMistakesScreen(model)
        }
    }

    fun streakAccent(streak: StudyStatsStore.StudyStreak?): Int {
        return if (streak != null && streak.studiedToday) {
            Color.rgb(247, 159, 0)
        } else {
            Color.rgb(160, 160, 166)
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
            )
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
            else -> Color.rgb(246, 202, 225)
        }
    }

    private companion object {
        const val RECENT_MISTAKE_LIMIT = 12
    }
}
