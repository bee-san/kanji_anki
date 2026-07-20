package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StoreResult
import dev.bee.kanjianki.core.StudyTaskTimingPolicy

/** Persistence capabilities owned by the analytics feature. */
interface StatsRepository {
    suspend fun loadCached(nowMillis: Long): StoreResult<StatsSnapshot?>

    suspend fun loadLatest(): StoreResult<StatsSnapshot?>

    suspend fun refresh(nowMillis: Long): StoreResult<StatsSnapshot>
}

data class ReviewDaySummarySnapshot(
    val dayStartMillis: Long,
    val total: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
    val writingRequired: Int,
    val writingFailed: Int,
)

data class TaskTypeDaySummarySnapshot(
    val dayStartMillis: Long,
    val taskType: String,
    val correct: Int,
    val total: Int,
)

data class CumulativeKanjiSnapshot(
    val dayStartMillis: Long,
    val cumulativeCount: Int,
)

data class StudyImpactSnapshot(
    val totalReviews: Int,
    val distinctReviewedKanji: Int,
    val writingRequired: Int,
    val writingPassed: Int,
    val writingFailed: Int,
    val manualOverrides: Int,
)

data class RecentMistakeSnapshot(
    val kanji: String,
    val rating: String,
    val reviewedAtMillis: Long,
)

data class StudyTaskTimeSnapshot(
    val todayMillis: Long,
    val lastSevenDaysMillis: Long,
    val answeredTasks: Int,
) {
    fun averageMillisPerTask(): Long =
        StudyTaskTimingPolicy.summarize(todayMillis, lastSevenDaysMillis, answeredTasks)
            .averageMillisPerTask()
}

data class KaniOutcomeSnapshot(
    val weakKanjiImproved: WeakKanjiImprovedSnapshot,
    val matureSupportGained: MatureSupportGainedSnapshot,
    val ladderHealth: LadderHealthSnapshot,
    val adaptiveHealth: AdaptiveHealthSnapshot,
)

data class WeakKanjiImprovedSnapshot(
    val improvedCount: Int,
    val averageBeforeWeakness: Double,
    val averageAfterWeakness: Double,
    val examples: List<KanjiImprovementSnapshot>,
)

data class KanjiImprovementSnapshot(
    val kanji: String,
    val beforeWeakness: Double,
    val afterWeakness: Double,
)

data class MatureSupportGainedSnapshot(
    val gainedSupportCount: Int,
    val matureSupportGained: Int,
    val firstSupportCount: Int,
    val examples: List<KanjiSupportGainSnapshot>,
)

data class KanjiSupportGainSnapshot(
    val kanji: String,
    val beforeMatureSupport: Int,
    val afterMatureSupport: Int,
)

data class LadderHealthSnapshot(
    val rungCounts: Map<RecordsBase.LadderRung, Int>,
    val totalActiveItems: Int,
    val realDueReviewsToMove: Int,
    val ladderPromotionIntervalDays: Int,
    val ladderDemotionFailStreak: Int,
    val promotionReadyCount: Int,
    val demotionRiskCount: Int,
    val demotionReadyCount: Int,
    val stuckCount: Int,
) {
    fun countFor(rung: RecordsBase.LadderRung?): Int = rungCounts[rung] ?: 0
}

data class AdaptiveHealthSnapshot(
    val coreCounts: Map<CoreSkill, Int>,
    val activeRepairsByTask: Map<String, Int>,
    val activeRepairsByFailure: Map<FailureKind, Int>,
    val totalAdaptiveItems: Int,
    val contextualCompleteCount: Int,
    val activeRepairCount: Int,
    val revalidationPendingCount: Int,
    val recentCoreMissCount: Int,
    val escalationRiskCount: Int,
    val stuckRepairCount: Int,
    val malformedStateCount: Int,
) {
    fun countFor(skill: CoreSkill?): Int = coreCounts[skill] ?: 0

    fun repairCountFor(taskType: String?): Int = activeRepairsByTask[taskType] ?: 0

    fun failureCountFor(kind: FailureKind?): Int = activeRepairsByFailure[kind] ?: 0
}

data class KanjiRepairEvidenceSnapshot(
    val kanji: String,
    val status: KanjiRepairEvidencePolicy.Status,
    val reason: String,
    val explanation: String,
    val beforeWeakness: Int?,
    val afterWeakness: Int?,
    val beforeMatureSupport: Int?,
    val afterMatureSupport: Int?,
    val kaniReviews: Int,
    val writingFailures: Int,
    val lastMistakeAtMillis: Long,
    val lastSyncAtMillis: Long,
    val confidence: Double,
    val confidenceReason: String,
)

/**
 * Stable analytics payload. Cache implementation and source-version details
 * remain inside the data module.
 */
data class StatsSnapshot(
    val outcomeStats: KaniOutcomeSnapshot,
    val impactReport: KanjiImpactAnalyzer.Report,
    val generatedAtMillis: Long,
    val sourceVersion: Long,
    val studyImpactStats: StudyImpactSnapshot,
    val recentMistakes: List<RecentMistakeSnapshot>,
    val studyStreak: StudyStreakSnapshot,
    val studyTaskTimeStats: StudyTaskTimeSnapshot,
    val cacheFormatVersion: Int,
    val reviewDaySummaries: List<ReviewDaySummarySnapshot>,
    val kanjiRepairEvidence: List<KanjiRepairEvidenceSnapshot>,
    val taskTypeDaySummaries: List<TaskTypeDaySummarySnapshot>,
    val cumulativeKanjiPracticed: List<CumulativeKanjiSnapshot>,
    val wrongPickCounts: Map<String, Map<String, Int>>,
    val confusionMeanings: Map<String, String>,
    val ladderForecast: LadderCompletionForecastPolicy.Forecast?,
)
