package dev.bee.kanjianki.application

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SyncQueuePlan
import dev.bee.kanjianki.data.SyncQueuePlanner
import dev.bee.kanjianki.data.SyncQueuePlanningSnapshot

/** Pure queue derivation used inside the repository's staged publication. */
class ManualSyncQueuePlanner(
    private val readingExposure: ReadingExposureModels.ExposureIndex,
) : SyncQueuePlanner {
    override fun plan(snapshot: SyncQueuePlanningSnapshot): SyncQueuePlan {
        val activeKanji = snapshot.activeRows.mapTo(HashSet()) { it.kanji }
        val activeItems = snapshot.currentItems.filter { it.kanji in activeKanji }
        val adaptivePlan = adaptivePlan(
            rows = snapshot.activeRows,
            items = activeItems,
            settings = snapshot.settings,
            workload = snapshot.adaptiveWorkload,
            recentReviewStats = snapshot.recentReviewStats,
            currentStudyStreakDays = snapshot.currentStudyStreakDays,
            studiedKanjiToday = snapshot.studiedKanjiToday,
            nowMillis = snapshot.nowMillis,
        )
        val evidenceStatusByKanji = repairEvidenceStatusByKanji(
            rows = snapshot.providerRows,
            inputs = snapshot.repairEvidenceInputs,
            currentSyncAtMillis = snapshot.syncStartedAtMillis,
        )
        val seeded = BridgeScheduler.withWeights(
            snapshot.schedulerFsrsWeights?.toDoubleArray(),
        ).seedQueue(
            snapshot.rows,
            snapshot.activeRows,
            snapshot.currentItems,
            snapshot.settings,
            snapshot.nowMillis,
            LocalDayPolicy.localDayStart(snapshot.nowMillis),
            adaptivePlan,
            snapshot.studyLadder,
            evidenceStatusByKanji,
        )
        return SyncQueuePlan(seeded, adaptivePlan)
    }

    fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        workload: AdaptiveWorkloadSnapshot,
        recentReviewStats: RecordsSchedulerModels.ReviewStats,
        currentStudyStreakDays: Int,
        studiedKanjiToday: Set<String>,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan =
        AdaptiveLoadPlanner().plan(
            AdaptiveLoadPlanner.PlanRequest.builder(
                rows,
                items,
                recentReviewStats,
                currentStudyStreakDays,
                studiedKanjiToday,
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                    workload.workPercent,
                    workload.mode,
                    workload.maxItems,
                ),
                nowMillis,
            )
                .settings(settings)
                .readingExposure(readingExposure)
                .build(),
        )

    companion object {
        @JvmStatic
        fun repairEvidenceStatusByKanji(
            rows: List<RecordsImportModels.DashboardRow>,
            inputs: List<KanjiRepairEvidencePolicy.Input>,
            currentSyncAtMillis: Long? = null,
        ): Map<String, KanjiRepairEvidencePolicy.Status> {
            if (rows.isEmpty()) {
                return emptyMap()
            }
            val rowsByKanji = rows.associateBy { it.kanji }
            return inputs.asSequence()
                .filter { it.kanji() in rowsByKanji }
                .associate { input ->
                    val row = rowsByKanji.getValue(input.kanji())
                    input.kanji() to if (currentSyncAtMillis == null) {
                        KanjiRepairEvidencePolicy.summarize(input).status()
                    } else {
                        currentEvidenceStatus(input, row, currentSyncAtMillis)
                    }
                }
        }

        private fun currentEvidenceStatus(
            input: KanjiRepairEvidencePolicy.Input,
            row: RecordsImportModels.DashboardRow,
            currentSyncAtMillis: Long,
        ): KanjiRepairEvidencePolicy.Status {
            val isPostReviewSample = currentSyncAtMillis > input.lastReviewAtMillis()
            val currentSnapshot = KanjiRepairEvidencePolicy.Snapshot(
                row.weaknessScore,
                row.matureSupportCount,
                currentSyncAtMillis,
                row.activeExampleCount,
                row.suspendedExampleCount,
                row.reasonCode,
            )
            val updated = KanjiRepairEvidencePolicy.Input(
                input.kanji(),
                input.before(),
                if (isPostReviewSample) currentSnapshot else input.after(),
                input.kaniReviews(),
                input.postReviewSamples() + if (isPostReviewSample) 1 else 0,
                input.writingFailures(),
                input.lastMistakeAtMillis(),
                input.firstReviewAtMillis(),
                input.lastReviewAtMillis(),
                maxOf(input.lastSyncAtMillis(), currentSyncAtMillis),
                input.ladder(),
            )
            return KanjiRepairEvidencePolicy.summarize(updated).status()
        }
    }
}
