package dev.bee.kanjianki.data.fakes

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.data.AdaptiveHealthSnapshot
import dev.bee.kanjianki.data.KaniOutcomeSnapshot
import dev.bee.kanjianki.data.LadderHealthSnapshot
import dev.bee.kanjianki.data.MatureSupportGainedSnapshot
import dev.bee.kanjianki.data.ReviewDaySummarySnapshot
import dev.bee.kanjianki.data.StatsSnapshot
import dev.bee.kanjianki.data.StudyImpactSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.StudyTaskTimeSnapshot
import dev.bee.kanjianki.data.WeakKanjiImprovedSnapshot

/**
 * An empty-but-valid [StatsSnapshot], for tests of code that consumes one.
 *
 * Shared here rather than rebuilt per test because a snapshot has ~18 nested parts and
 * the progress-analytics computation reads all of them; a fixture keeps a renamed
 * field a one-line fix. [reviewDaySummaries] is the one part a caller usually varies,
 * so it is a parameter; everything else is a zero.
 */
fun emptyStatsSnapshot(
    nowMillis: Long = 0L,
    reviewDaySummaries: List<ReviewDaySummarySnapshot> = emptyList(),
    // The live format-11 cache version (`SqlStatsData.STATS_CACHE_FORMAT_VERSION`),
    // inlined because that constant lives in :data-sql, which :data-api cannot see.
    // The computation reads whatever snapshot it is given regardless of this value.
    cacheFormatVersion: Int = 11,
): StatsSnapshot = StatsSnapshot(
    outcomeStats = KaniOutcomeSnapshot(
        weakKanjiImproved = WeakKanjiImprovedSnapshot(0, 0.0, 0.0, emptyList()),
        matureSupportGained = MatureSupportGainedSnapshot(0, 0, 0, emptyList()),
        ladderHealth = LadderHealthSnapshot(emptyMap(), 0, 0, 0, 0, 0, 0, 0, 0),
        adaptiveHealth = AdaptiveHealthSnapshot(
            coreCounts = emptyMap(),
            activeRepairsByTask = emptyMap(),
            activeRepairsByFailure = emptyMap(),
            totalAdaptiveItems = 0,
            contextualCompleteCount = 0,
            activeRepairCount = 0,
            revalidationPendingCount = 0,
            recentCoreMissCount = 0,
            escalationRiskCount = 0,
            stuckRepairCount = 0,
            malformedStateCount = 0,
        ),
    ),
    impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
    generatedAtMillis = nowMillis,
    sourceVersion = 0L,
    studyImpactStats = StudyImpactSnapshot(0, 0, 0, 0, 0, 0),
    recentMistakes = emptyList(),
    studyStreak = StudyStreakSnapshot(0, 0, false, 0, 0L),
    studyTaskTimeStats = StudyTaskTimeSnapshot(0L, 0L, 0),
    cacheFormatVersion = cacheFormatVersion,
    reviewDaySummaries = reviewDaySummaries,
    kanjiRepairEvidence = emptyList(),
    taskTypeDaySummaries = emptyList(),
    cumulativeKanjiPracticed = emptyList(),
    wrongPickCounts = emptyMap(),
    confusionMeanings = emptyMap(),
    ladderForecast = null,
)
