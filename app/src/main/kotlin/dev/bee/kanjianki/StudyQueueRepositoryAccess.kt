package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.StudyQueueSnapshot
import kotlinx.coroutines.runBlocking

/** Host adapter that supplies repository snapshots to the existing queue planner. */
internal class StudyQueueRepositoryAccess(
    private val activity: MainActivityHome,
) {
    fun studyAheadMillis(): Long =
        runBlocking { activity.studyUseCases.loadQueue(System.currentTimeMillis()) }
            .studyAheadMinutes * 60_000L

    fun studyQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        now: Long,
        persist: Boolean,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        currentItems: List<RecordsStudyModels.StudyItem>?,
        queueSnapshot: StudyQueueSnapshot? = null,
    ): List<RecordsStudyModels.StudyItem> {
        val queue = queueSnapshot ?: runBlocking { activity.studyUseCases.loadQueue(now) }
        val scheduler = BridgeScheduler.withWeights(queue.schedulerFsrsWeights?.toDoubleArray())
        return HomeStudyQueueActions.studyQueue(
            HomeStudyQueueActions.StudyQueueRequest(
                rows,
                now,
                persist,
                plan,
                { runBlocking { activity.studyUseCases.loadAllItems() } },
                { queue.syncSettings },
                activity::startOfDay,
                { queue.studyLadder },
                { planRows, planItems, planNow ->
                    activity.adaptivePlan(planRows, planItems, planNow, queue)
                },
                scheduler::seedQueue,
                object : HomeStudyQueueActions.StudyItemsWriter {
                    override fun annotateSimilarKanjiAvailability(
                        items: List<RecordsStudyModels.StudyItem>,
                    ): List<RecordsStudyModels.StudyItem> = runBlocking {
                        activity.studyUseCases.annotateCapabilities(items)
                    }

                    override fun replaceStudyItems(
                        items: List<RecordsStudyModels.StudyItem>,
                        baseline: List<RecordsStudyModels.StudyItem>,
                    ) {
                        runBlocking {
                            activity.studyUseCases.replaceQueue(items, baseline)
                        }
                    }
                },
            ),
            currentItems,
        )
    }
}
