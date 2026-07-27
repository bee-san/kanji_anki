package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels

/**
 * Temporary host adapter for Study's pre-repository queue path. Goal 172
 * removes this after Study moves onto StudyRepository.
 */
internal class StudyQueueCompatibilityAccess(
    private val activity: MainActivityHome,
) {
    fun studyAheadMillis(): Long = activity.store.studyAheadMinutes() * 60_000L

    fun studyQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        now: Long,
        persist: Boolean,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        currentItems: List<RecordsStudyModels.StudyItem>?,
    ): List<RecordsStudyModels.StudyItem> {
        val scheduler = BridgeScheduler.withWeights(activity.store.schedulerFsrsWeights())
        return HomeStudyQueueActions.studyQueue(
            HomeStudyQueueActions.StudyQueueRequest(
                rows,
                now,
                persist,
                plan,
                activity.store::studyItems,
                activity::settings,
                activity::startOfDay,
                activity::studyLadderSettings,
                activity::adaptivePlan,
                scheduler::seedQueue,
                object : HomeStudyQueueActions.StudyItemsWriter {
                    override fun annotateSimilarKanjiAvailability(
                        items: List<RecordsStudyModels.StudyItem>,
                    ): List<RecordsStudyModels.StudyItem> =
                        activity.store.annotateSimilarKanjiAvailability(items)

                    override fun replaceStudyItems(
                        items: List<RecordsStudyModels.StudyItem>,
                        baseline: List<RecordsStudyModels.StudyItem>,
                    ) {
                        activity.store.replaceStudyItems(items, null, 0L, null, baseline)
                    }
                },
            ),
            currentItems,
        )
    }
}
