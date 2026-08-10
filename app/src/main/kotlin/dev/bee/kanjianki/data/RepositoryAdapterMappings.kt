package dev.bee.kanjianki.data

internal fun LocalStoreBase.SyncStatus.toRepositorySnapshot(): SyncStatusSnapshot =
    SyncStatusSnapshot(
        status = status,
        activeNotes = activeNotes,
        activeCards = activeCards,
        suspendedCards = suspendedCards,
        importedKanji = importedKanji,
        finishedAtMillis = finishedAt,
        errorMessage = errorMessage,
        removalMessage = removalMessage,
    )

internal fun StudyStatsStore.StudyStreak.toRepositorySnapshot(): StudyStreakSnapshot =
    StudyStreakSnapshot(
        currentDays = currentDays,
        bestDays = bestDays,
        studiedToday = studiedToday,
        reviewsToday = reviewsToday,
        lastStudyAtMillis = lastStudyAtMillis,
    )

internal fun StatsCacheStore.Snapshot.toRepositorySnapshot(): StatsSnapshot =
    StatsSnapshot(
        outcomeStats = outcomeStats.toRepositorySnapshot(),
        impactReport = impactReport,
        generatedAtMillis = generatedAtMillis,
        sourceVersion = sourceVersion,
        studyImpactStats = studyImpactStats.toRepositorySnapshot(),
        recentMistakes = recentMistakes.map { it.toRepositorySnapshot() },
        studyStreak = studyStreak.toRepositorySnapshot(),
        studyTaskTimeStats = studyTaskTimeStats.toRepositorySnapshot(),
        cacheFormatVersion = cacheFormatVersion,
        reviewDaySummaries = reviewDaySummaries.map {
            ReviewDaySummarySnapshot(
                it.dayStartMillis,
                it.total,
                it.again,
                it.hard,
                it.good,
                it.easy,
                it.writingRequired,
                it.writingFailed,
            )
        },
        kanjiRepairEvidence = kanjiRepairEvidence.map { it.toRepositorySnapshot() },
        taskTypeDaySummaries = taskTypeDaySummaries.map {
            TaskTypeDaySummarySnapshot(it.dayStartMillis, it.taskType, it.correct, it.total)
        },
        cumulativeKanjiPracticed = cumulativeKanjiPracticed.map {
            CumulativeKanjiSnapshot(it.dayStartMillis, it.cumulativeCount)
        },
        wrongPickCounts = wrongPickCounts.mapValues { (_, values) -> values.toMap() },
        confusionMeanings = confusionMeanings.toMap(),
        ladderForecast = ladderForecast,
    )

private fun StudyStatsStore.StudyImpactStats.toRepositorySnapshot(): StudyImpactSnapshot =
    StudyImpactSnapshot(
        totalReviews,
        distinctReviewedKanji,
        writingRequired,
        writingPassed,
        writingFailed,
        manualOverrides,
    )

private fun StudyStatsStore.RecentMistake.toRepositorySnapshot(): RecentMistakeSnapshot =
    RecentMistakeSnapshot(kanji, rating, reviewedAtMillis)

private fun StudyStatsStore.StudyTaskTimeStats.toRepositorySnapshot(): StudyTaskTimeSnapshot =
    StudyTaskTimeSnapshot(todayMillis, lastSevenDaysMillis, answeredTasks)

private fun StudyStatsStore.KaniOutcomeStats.toRepositorySnapshot(): KaniOutcomeSnapshot =
    KaniOutcomeSnapshot(
        weakKanjiImproved = weakKanjiImproved.toRepositorySnapshot(),
        matureSupportGained = matureSupportGained.toRepositorySnapshot(),
        ladderHealth = ladderHealth.toRepositorySnapshot(),
        adaptiveHealth = adaptiveHealth.toRepositorySnapshot(),
    )

internal fun StudyStatsStore.WeakKanjiImprovedMetric.toRepositorySnapshot(): WeakKanjiImprovedSnapshot =
    WeakKanjiImprovedSnapshot(
        improvedCount,
        averageBeforeWeakness,
        averageAfterWeakness,
        examples.map {
            KanjiImprovementSnapshot(it.kanji, it.beforeWeakness, it.afterWeakness)
        },
    )

internal fun StudyStatsStore.MatureSupportGainedMetric.toRepositorySnapshot(): MatureSupportGainedSnapshot =
    MatureSupportGainedSnapshot(
        gainedSupportCount,
        matureSupportGained,
        firstSupportCount,
        examples.map {
            KanjiSupportGainSnapshot(
                it.kanji,
                it.beforeMatureSupport,
                it.afterMatureSupport,
            )
        },
    )

private fun StudyStatsStore.LadderHealthMetric.toRepositorySnapshot(): LadderHealthSnapshot =
    LadderHealthSnapshot(
        rungCounts = rungCounts.toMap(),
        totalActiveItems = totalActiveItems,
        realDueReviewsToMove = realDueReviewsToMove,
        ladderPromotionIntervalDays = ladderPromotionIntervalDays,
        ladderDemotionFailStreak = ladderDemotionFailStreak,
        promotionReadyCount = promotionReadyCount,
        demotionRiskCount = demotionRiskCount,
        demotionReadyCount = demotionReadyCount,
        stuckCount = stuckCount,
    )

private fun StudyStatsStore.AdaptiveHealthMetric.toRepositorySnapshot(): AdaptiveHealthSnapshot =
    AdaptiveHealthSnapshot(
        coreCounts = coreCounts.toMap(),
        activeRepairsByTask = activeRepairsByTask.toMap(),
        activeRepairsByFailure = activeRepairsByFailure.toMap(),
        totalAdaptiveItems = totalAdaptiveItems,
        contextualCompleteCount = contextualCompleteCount,
        activeRepairCount = activeRepairCount,
        revalidationPendingCount = revalidationPendingCount,
        recentCoreMissCount = recentCoreMissCount,
        escalationRiskCount = escalationRiskCount,
        stuckRepairCount = stuckRepairCount,
        malformedStateCount = malformedStateCount,
    )

private fun StudyStatsStore.KanjiRepairEvidence.toRepositorySnapshot(): KanjiRepairEvidenceSnapshot =
    KanjiRepairEvidenceSnapshot(
        kanji = kanji,
        status = status,
        reason = reason,
        explanation = explanation,
        beforeWeakness = beforeWeakness,
        afterWeakness = afterWeakness,
        beforeMatureSupport = beforeMatureSupport,
        afterMatureSupport = afterMatureSupport,
        kaniReviews = kaniReviews,
        writingFailures = writingFailures,
        lastMistakeAtMillis = lastMistakeAtMillis,
        lastSyncAtMillis = lastSyncAtMillis,
        confidence = confidence,
        confidenceReason = confidenceReason,
    )
