package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore

internal interface StatsScreenStatsSource {
    fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot
    fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats
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

            override fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
                return store.studyTaskTimeStats(nowMillis)
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
    return statsScreenModel(
        snapshot.outcomeStats,
        snapshot.impactReport,
        source.studyTaskTimeStats(nowMillis),
    )
}

internal fun statsScreenModel(
    stats: StudyStatsStore.KaniOutcomeStats,
    report: KanjiImpactAnalyzer.Report?,
    studyTime: StudyStatsStore.StudyTaskTimeStats,
): StatsScreenModel {
    return StatsScreenModel(
        title = "Stats",
        intro = "",
        verdict = statsVerdictCard(stats),
        sections = listOf(
            weaknessBurnDownCard(stats),
            supportConversionCard(stats),
            notHelpingCard(report),
            ladderHealthCard(stats.ladderHealth),
            studyTimeCard(studyTime)
        )
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
        title = "Weak kanji trend",
        summary = StudyTextCopy.countText(
            stats.weakKanjiImproved.improvedCount,
            "weak kanji improved",
            "weak kanji improved"
        ),
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
        title = "Anki support",
        summary = StudyTextCopy.countText(
            stats.matureSupportGained.matureSupportGained,
            "mature card gained",
            "mature cards gained"
        ),
        body = StudyTextCopy.countText(
            stats.matureSupportGained.firstSupportCount,
            "kanji gained first mature support",
            "kanji gained first mature support"
        ) + ".",
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
                    text = StudyTextCopy.countText(
                        report.needsMoreCardsCount,
                        "kanji still needs more Anki evidence",
                        "kanji still need more Anki evidence"
                    ) + ".",
                    color = STATS_MUTED_COLOR,
                    bold = false,
                    sizeSp = 15
                )
            )
        }
    }
    return StatsCardModel(
        title = "Needs attention",
        summary = StudyTextCopy.countText(rows.size, "kanji with enough evidence", "kanji with enough evidence"),
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
        title = "Ladder status",
        summary = StudyTextCopy.countText(
            metric.totalActiveItems,
            "active kanji on the ladder",
            "active kanji on the ladder"
        ),
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
        title = "Study time",
        summary = "Today: " + StatsTextCopy.formatStudyTime(stats.todayMillis),
        body = "Last 7 days: " + StatsTextCopy.formatStudyTime(stats.lastSevenDaysMillis),
        lines = listOf(
            StatsLineModel(
                text = "Answered tasks: " + stats.answeredTasks,
                color = STATS_MUTED_COLOR,
                bold = false,
                sizeSp = 16
            ),
            StatsLineModel(
                text = "Avg / task: " + StatsTextCopy.formatStudyTime(stats.averageMillisPerTask()),
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
