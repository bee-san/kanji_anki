package dev.bee.kanjianki.application

import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SyncQueuePlanningSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSyncQueuePlannerTest {
    @Test
    fun emptySnapshotProducesAnEmptyQueueAndEmptyEvidenceMap() {
        val planner = ManualSyncQueuePlanner(ReadingExposureModels.ExposureIndex.EMPTY)
        val snapshot = SyncQueuePlanningSnapshot(
            providerRows = emptyList(),
            rows = emptyList(),
            activeRows = emptyList(),
            currentItems = emptyList(),
            locallySuspendedKanji = emptySet(),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            repairEvidenceInputs = emptyList(),
            studyLadder = RecordsBase.StudyLadderSettings.defaults(),
            schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            schedulerFsrsWeights = null,
            learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
            adaptiveWorkload = AdaptiveWorkloadSnapshot(100, 25, "all"),
            recentReviewStats = RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
            currentStudyStreakDays = 0,
            studiedKanjiToday = emptySet(),
            syncStartedAtMillis = 1_000L,
            nowMillis = 2_000L,
        )

        val result = planner.plan(snapshot)

        assertTrue(result.items.isEmpty())
        assertTrue(
            ManualSyncQueuePlanner.repairEvidenceStatusByKanji(
                emptyList(),
                emptyList(),
            ).isEmpty(),
        )
    }
}
