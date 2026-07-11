package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyNowCountPolicy

/** Dry-runs the same seed, annotate, replan, and select pipeline as the Study route. */
internal object StudyNowCountCoordinator {
    fun count(request: Request): Result {
        val queue = request.queue
        val mode = request.mode
        val timing = request.timing
        val pipeline = request.pipeline
        if (mode.initialPlan == null || queue.rows.isEmpty()) {
            return Result(0, mode.initialPlan)
        }
        val seeded = pipeline.scheduler.seedQueue(
            queue.rows,
            queue.currentItems,
            queue.settings,
            timing.nowMillis,
            timing.startOfDayMillis,
            mode.initialPlan,
            queue.ladder,
        )
        val annotated = pipeline.annotator.annotate(seeded)
        val effectivePlan = if (StudyItemComparators.sameStudyQueue(queue.currentItems, annotated)) {
            mode.initialPlan
        } else {
            pipeline.replanner.planForSeededItems(annotated)
        }
        return Result(
            StudyNowCountPolicy.countSeeded(
                StudyNowCountPolicy.SeededCountRequest(
                    annotated,
                    queue.rows,
                    queue.settings,
                    StudyNowCountPolicy.SelectionContext(
                        timing.nowMillis,
                        timing.studyAheadMillis,
                        effectivePlan,
                        mode.continueAllKanjiSession,
                        queue.ladder,
                    ),
                ),
            ),
            effectivePlan,
        )
    }

    data class Request(
        val queue: QueueInput,
        val timing: Timing,
        val mode: Mode,
        val pipeline: Pipeline,
    )

    data class QueueInput(
        val rows: List<RecordsImportModels.DashboardRow>,
        val currentItems: List<RecordsStudyModels.StudyItem>,
        val settings: RecordsSyncModels.Settings,
        val ladder: RecordsBase.StudyLadderSettings,
    )

    data class Timing(
        val nowMillis: Long,
        val startOfDayMillis: Long,
        val studyAheadMillis: Long,
    )

    data class Mode(
        val initialPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        val continueAllKanjiSession: Boolean,
    )

    data class Pipeline(
        val scheduler: BridgeScheduler,
        val annotator: QueueAnnotator,
        val replanner: SeededPlanProvider,
    )

    fun interface QueueAnnotator {
        fun annotate(items: List<RecordsStudyModels.StudyItem>): List<RecordsStudyModels.StudyItem>
    }

    fun interface SeededPlanProvider {
        fun planForSeededItems(
            items: List<RecordsStudyModels.StudyItem>,
        ): RecordsSchedulerModels.AdaptiveLoadPlan
    }

    data class Result(
        val studyItemCount: Int,
        val effectivePlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    )
}
