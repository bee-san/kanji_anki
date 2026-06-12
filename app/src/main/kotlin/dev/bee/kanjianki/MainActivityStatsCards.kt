package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore

internal interface StatsScreenStatsSource {
    fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot
    fun studyImpactStats(): StudyStatsStore.StudyImpactStats
    fun studyStreak(nowMillis: Long): StudyStatsStore.StudyStreak
    fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake>
    fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats
    fun kanjiRepairEvidence(): List<StudyStatsStore.KanjiRepairEvidence>
}

internal fun MainActivityStats.buildStatsScreenModel(): StatsScreenModel {
    return buildStatsScreenModel(
        object : StatsScreenStatsSource {
            override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                return store.cachedStatsSnapshotOrNull()
            }

            override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                return store.latestStatsSnapshotOrNull()
            }

            override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
                return store.recomputeStatsSnapshotSynchronously(nowMillis)
            }

            override fun studyImpactStats(): StudyStatsStore.StudyImpactStats {
                return store.studyImpactStats()
            }

            override fun studyStreak(nowMillis: Long): StudyStatsStore.StudyStreak {
                return store.studyStreak(nowMillis)
            }

            override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
                return store.recentMistakes(limit)
            }

            override fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
                return store.studyTaskTimeStats(nowMillis)
            }

            override fun kanjiRepairEvidence(): List<StudyStatsStore.KanjiRepairEvidence> {
                return store.kanjiRepairEvidence()
            }
        }
    )
}

internal fun buildStatsScreenModel(
    source: StatsScreenStatsSource,
    nowMillis: Long = System.currentTimeMillis(),
): StatsScreenModel {
    val snapshot = source.cachedStatsSnapshotOrNull()
        ?: source.latestStatsSnapshotOrNull()
        ?: source.recomputeStatsSnapshotSynchronously(nowMillis)
    val needsLiveFallback = snapshot.cacheFormatVersion < STATS_CACHE_FORMAT_VERSION
    val studyImpact = if (needsLiveFallback) source.studyImpactStats() else snapshot.studyImpactStats
    val studyStreak = if (needsLiveFallback) source.studyStreak(nowMillis) else snapshot.studyStreak
    val studyTaskTimeStats = if (needsLiveFallback) source.studyTaskTimeStats(nowMillis) else snapshot.studyTaskTimeStats
    val recentMistakes = if (needsLiveFallback) source.recentMistakes(STATS_RECENT_MISTAKE_LIMIT) else snapshot.recentMistakes
    val repairEvidence = source.kanjiRepairEvidence()
    return statsScreenModel(
        snapshot.outcomeStats,
        snapshot.impactReport,
        studyTaskTimeStats,
        studyImpact,
        studyStreak,
        recentMistakes,
        repairEvidence,
        nowMillis,
    )
}

internal fun statsScreenModel(
    stats: StudyStatsStore.KaniOutcomeStats,
    report: KanjiImpactAnalyzer.Report?,
    studyTime: StudyStatsStore.StudyTaskTimeStats,
    studyImpact: StudyStatsStore.StudyImpactStats,
    studyStreak: StudyStatsStore.StudyStreak,
    recentMistakes: List<StudyStatsStore.RecentMistake>,
    repairEvidence: List<StudyStatsStore.KanjiRepairEvidence> = emptyList(),
    nowMillis: Long,
): StatsScreenModel {
    return StatsScreenModel(
        title = StatsTextCopy.statsTitle(),
        intro = "",
        verdict = statsVerdictCard(stats),
        sections = buildList {
            add(weaknessBurnDownCard(stats))
            add(supportConversionCard(stats))
            repairEvidenceCard(repairEvidence)?.let(::add)
            add(studyStreakCard(studyStreak, nowMillis))
            add(studyImpactCard(studyImpact))
            add(notHelpingCard(report))
            add(ladderHealthCard(stats.ladderHealth))
            add(recentMistakesCard(recentMistakes, nowMillis))
            add(studyTimeCard(studyTime))
        }
    )
}

private fun statsVerdictCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    val working = StatsTextCopy.verdictWorking(
        stats.weakKanjiImproved.improvedCount,
        stats.matureSupportGained.matureSupportGained
    )
    val hasLadder = StatsTextCopy.verdictHasLadder(stats.ladderHealth.totalActiveItems)
    val hasImpactEvidence = working || hasLadder
    val fillColor = when {
        working -> STATS_VERDICT_WORKING_FILL
        hasLadder -> STATS_VERDICT_LADDER_FILL
        else -> STATS_VERDICT_IDLE_FILL
    }
    val strokeColor = when {
        working -> STATS_TEAL_COLOR
        hasLadder -> STATS_GOLD_COLOR
        else -> 0xFFB2B2BA.toInt()
    }
    val body = StatsTextCopy.verdictBody(
        hasImpactEvidence,
        working,
        hasLadder,
        stats.weakKanjiImproved.improvedCount,
        stats.matureSupportGained.matureSupportGained,
        stats.ladderHealth.promotionReadyCount,
        stats.ladderHealth.demotionRiskCount,
        stats.ladderHealth.totalActiveItems
    )
    return StatsCardModel(
        title = StatsTextCopy.verdictTitle(working),
        body = body,
        fillColor = fillColor,
        strokeColor = strokeColor,
        emptyState = !hasImpactEvidence,
        titleColor = if (working) STATS_TEAL_COLOR else STATS_MUTED_COLOR,
        bodyColor = if (working) STATS_INK_COLOR else STATS_MUTED_COLOR,
        titleSizeSp = 24,
        bodySizeSp = 15
    )
}

private fun outcomeCard(
    title: String,
    summary: String,
    body: String,
    lines: List<StatsLineModel>,
    strokeColor: Int,
): StatsCardModel {
    return StatsCardModel(
        title = title,
        summary = summary,
        body = body,
        lines = lines,
        strokeColor = strokeColor,
        titleColor = STATS_MUTED_COLOR,
        summaryColor = STATS_INK_COLOR,
        bodyColor = STATS_MUTED_COLOR,
        titleSizeSp = 18,
        summarySizeSp = 25,
        bodySizeSp = 15
    )
}

private fun weaknessBurnDownCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    return outcomeCard(
        title = StatsTextCopy.weakKanjiTrendTitle(),
        summary = StatsTextCopy.weakKanjiImprovedSummary(stats.weakKanjiImproved.improvedCount),
        body = StatsTextCopy.weaknessImprovementBody(
            stats.weakKanjiImproved.improvedCount,
            stats.weakKanjiImproved.averageBeforeWeakness,
            stats.weakKanjiImproved.averageAfterWeakness
        ),
        lines = weaknessImprovementExamples(stats.weakKanjiImproved).map {
            StatsLineModel(
                text = it,
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 17
            )
        },
        strokeColor = STATS_TEAL_COLOR
    )
}

private fun supportConversionCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    return outcomeCard(
        title = StatsTextCopy.ankiSupportTitle(),
        summary = StatsTextCopy.matureSupportSummary(stats.matureSupportGained.matureSupportGained),
        body = StatsTextCopy.firstMatureSupportSummary(stats.matureSupportGained.firstSupportCount) + ".",
        lines = supportGainExamples(stats.matureSupportGained).map {
            StatsLineModel(
                text = it,
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 17
            )
        },
        strokeColor = STATS_BLUE_COLOR
    )
}

private fun studyStreakCard(
    streak: StudyStatsStore.StudyStreak,
    nowMillis: Long,
): StatsCardModel {
    return outcomeCard(
        title = StatsTextCopy.studyStreakTitle(),
        summary = StatsTextCopy.studyStreakSummary(streak.currentDays),
        body = StatsTextCopy.studyStreakBody(
            streak.bestDays,
            streak.studiedToday,
            streak.reviewsToday,
            streak.lastStudyAtMillis,
            nowMillis
        ),
        lines = emptyList(),
        strokeColor = STATS_TEAL_COLOR
    )
}

private fun studyImpactCard(stats: StudyStatsStore.StudyImpactStats): StatsCardModel {
    return outcomeCard(
        title = StatsTextCopy.studyImpactTitle(),
        summary = StatsTextCopy.studyImpactSummary(stats.totalReviews),
        body = StatsTextCopy.studyImpactBody(
            stats.totalReviews,
            stats.distinctReviewedKanji,
            stats.writingRequired,
            stats.writingPassed,
            stats.writingFailed,
            stats.manualOverrides
        ),
        lines = emptyList(),
        strokeColor = STATS_BLUE_COLOR
    )
}

private fun recentMistakesCard(
    mistakes: List<StudyStatsStore.RecentMistake>,
    nowMillis: Long,
): StatsCardModel {
    val rows = mistakes.take(5)
    return outcomeCard(
        title = StatsTextCopy.recentMistakesTitle(),
        summary = StatsTextCopy.recentMistakesSummary(rows.size),
        body = StatsTextCopy.recentMistakesBody(rows.isNotEmpty()),
        lines = rows.map { mistake ->
            StatsLineModel(
                text = StatsTextCopy.recentMistakeRowText(
                    mistake.kanji,
                    mistake.rating,
                    mistake.reviewedAtMillis,
                    nowMillis
                ),
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 16
            )
        },
        strokeColor = STATS_CORAL_COLOR
    )
}

private fun notHelpingCard(report: KanjiImpactAnalyzer.Report?): StatsCardModel {
    val rows = if (report == null) emptyList() else notHelpingRows(report)
    val details = buildList {
        rows.take(5).forEach { row ->
            add(
                StatsLineModel(
                    text = StatsTextCopy.notHelpingRowText(
                        row.kanji,
                        row.reviewCount,
                        row.sameCardCount,
                        row.retentionDelta,
                        row.difficultyDelta
                    ),
                    color = STATS_INK_COLOR,
                    bold = true,
                    sizeSp = 16
                )
            )
        }
        if (rows.isNotEmpty() && report != null && report.needsMoreCardsCount > 0) {
            add(
                StatsLineModel(
                    text = StatsTextCopy.moreAnkiEvidenceSummary(report.needsMoreCardsCount) + ".",
                    color = STATS_MUTED_COLOR,
                    bold = false,
                    sizeSp = 15
                )
            )
        }
    }
    return StatsCardModel(
        title = StatsTextCopy.needsAttentionTitle(),
        summary = StatsTextCopy.kanjiWithEnoughEvidenceSummary(rows.size),
        body = StatsTextCopy.notHelpingBody(report == null || report.empty(), rows.isNotEmpty()),
        lines = details,
        strokeColor = STATS_CORAL_COLOR,
        titleColor = STATS_MUTED_COLOR,
        summaryColor = STATS_INK_COLOR,
        bodyColor = STATS_MUTED_COLOR
    )
}

private fun ladderHealthCard(metric: StudyStatsStore.LadderHealthMetric): StatsCardModel {
    return outcomeCard(
        title = StatsTextCopy.ladderStatusTitle(),
        summary = StatsTextCopy.activeKanjiOnLadderSummary(metric.totalActiveItems),
        body = StatsTextCopy.ladderHealthBody(
            metric.totalActiveItems,
            metric.promotionReadyCount,
            metric.demotionRiskCount,
            metric.demotionReadyCount,
            metric.ladderPromotionIntervalDays,
            metric.ladderDemotionFailStreak
        ),
        lines = ladderDistributionRows(metric).map {
            StatsLineModel(
                text = it,
                color = STATS_INK_COLOR,
                bold = false,
                sizeSp = 16
            )
        },
        strokeColor = STATS_GOLD_COLOR
    )
}

private fun repairEvidenceCard(evidence: List<StudyStatsStore.KanjiRepairEvidence>): StatsCardModel? {
    if (evidence.isEmpty()) {
        return null
    }
    val rows = evidence.take(3)
    return outcomeCard(
        title = StatsTextCopy.repairEvidenceTitle(),
        summary = StatsTextCopy.repairEvidenceSummary(evidence.size),
        body = StatsTextCopy.repairEvidenceBody(),
        lines = rows.map { row ->
            StatsLineModel(
                text = repairEvidenceLineText(row),
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 16
            )
        },
        strokeColor = STATS_GOLD_COLOR
    )
}

private fun repairEvidenceLineText(item: StudyStatsStore.KanjiRepairEvidence): String {
    val status = StatsTextCopy.repairEvidenceStatusLabel(item.status)
    val delta = when {
        item.beforeWeakness != null && item.afterWeakness != null -> "${item.beforeWeakness} → ${item.afterWeakness}"
        item.beforeMatureSupport != null && item.afterMatureSupport != null -> "${item.beforeMatureSupport} → ${item.afterMatureSupport}"
        else -> item.reason.replace('_', ' ')
    }
    return "${item.kanji} · $status · $delta"
}

private fun notHelpingRows(report: KanjiImpactAnalyzer.Report?): List<KanjiImpactAnalyzer.Row> {
    return KanjiImpactAnalyzer.notHelpingRows(report)
}

private fun ladderDistributionRows(metric: StudyStatsStore.LadderHealthMetric): List<String> {
    return RecordsBase.LadderRung.values().map { rung ->
        StatsTextCopy.ladderDistributionRow(rung, metric.countFor(rung))
    }
}

private fun weaknessImprovementExamples(metric: StudyStatsStore.WeakKanjiImprovedMetric): List<String> {
    return metric.examples.take(3).map { example ->
        StatsTextCopy.weaknessImprovementExample(
            example.kanji,
            example.beforeWeakness,
            example.afterWeakness
        )
    }
}

private fun supportGainExamples(metric: StudyStatsStore.MatureSupportGainedMetric): List<String> {
    return metric.examples.map { example ->
        StatsTextCopy.supportGainExample(
            example.kanji,
            example.beforeMatureSupport,
            example.afterMatureSupport
        )
    }
}

private fun studyTimeCard(stats: StudyStatsStore.StudyTaskTimeStats): StatsCardModel {
    return StatsCardModel(
        title = StatsTextCopy.studyTimeTitle(),
        summary = StatsTextCopy.studyTimeTodayLabel(StatsTextCopy.formatStudyTime(stats.todayMillis)),
        body = StatsTextCopy.studyTimeLast7DaysLabel(StatsTextCopy.formatStudyTime(stats.lastSevenDaysMillis)),
        lines = listOf(
            StatsLineModel(
                text = StatsTextCopy.studyTimeAnsweredTasksLabel(stats.answeredTasks),
                color = STATS_MUTED_COLOR,
                bold = false,
                sizeSp = 16
            ),
            StatsLineModel(
                text = StatsTextCopy.studyTimeAveragePerTaskLabel(StatsTextCopy.formatStudyTime(stats.averageMillisPerTask())),
                color = STATS_MUTED_COLOR,
                bold = false,
                sizeSp = 16
            )
        ),
        strokeColor = STATS_CORAL_COLOR,
        titleColor = STATS_MUTED_COLOR,
        summaryColor = STATS_INK_COLOR,
        bodyColor = STATS_MUTED_COLOR,
        titleSizeSp = 18,
        summarySizeSp = 24,
        bodySizeSp = 16
    )
}
