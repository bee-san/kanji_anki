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
    fun count(
        rows: List<RecordsImportModels.DashboardRow>,
        currentItems: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        nowMillis: Long,
        startOfDayMillis: Long,
        studyAheadMillis: Long,
        initialPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        continueAllKanjiSession: Boolean,
        ladder: RecordsBase.StudyLadderSettings,
        scheduler: BridgeScheduler,
        annotator: QueueAnnotator,
        replanner: SeededPlanProvider,
    ): Result {
        if (initialPlan == null || rows.isEmpty()) {
            return Result(0, initialPlan)
        }
        val seeded = scheduler.seedQueue(
            rows,
            currentItems,
            settings,
            nowMillis,
            startOfDayMillis,
            initialPlan,
            ladder,
        )
        val annotated = annotator.annotate(seeded)
        val effectivePlan = if (StudyItemComparators.sameStudyQueue(currentItems, annotated)) {
            initialPlan
        } else {
            replanner.planForSeededItems(annotated)
        }
        return Result(
            StudyNowCountPolicy.countSeeded(
                annotated,
                rows,
                settings,
                nowMillis,
                studyAheadMillis,
                effectivePlan,
                continueAllKanjiSession,
                ladder,
            ),
            effectivePlan,
        )
    }

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
