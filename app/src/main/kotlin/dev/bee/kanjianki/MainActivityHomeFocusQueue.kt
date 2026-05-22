package dev.bee.kanjianki

import android.graphics.Color
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.StudyStatsStore

internal class MainActivityHomeFocusQueue(private val home: MainActivityHome) {
    fun renderFocusQueue() {
        renderFocusQueueScreen(home)
    }

    fun renderRecentMistakes() {
        renderRecentMistakesScreen(home)
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

}
