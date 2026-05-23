package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

internal fun MainActivityStats.buildStatsScreenModel(): StatsScreenModel {
    val stats = store.kaniOutcomeStats()
    val report = store.kanjiImpactReport()
    val studyTime = store.studyTaskTimeStats(System.currentTimeMillis())
    return StatsScreenModel(
        title = "Stats",
        intro = "Kani does not replace Anki. It repairs weak kanji from your Anki reviews, then shows whether Anki evidence caught up afterward.",
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

private fun MainActivityStats.statsVerdictCard(stats: StudyStatsStore.KaniOutcomeStats?): StatsCardModel {
    val working = stats != null && StatsTextCopy.verdictWorking(
        stats.weakKanjiImproved.improvedCount,
        stats.matureSupportGained.matureSupportGained
    )
    val hasLadder = stats != null && StatsTextCopy.verdictHasLadder(stats.ladderHealth.totalActiveItems)
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
    val body = if (stats == null) {
        StatsTextCopy.verdictBody(false, working, hasLadder, 0, 0, 0, 0, 0)
    } else {
        StatsTextCopy.verdictBody(
            true,
            working,
            hasLadder,
            stats.weakKanjiImproved.improvedCount,
            stats.matureSupportGained.matureSupportGained,
            stats.ladderHealth.promotionReadyCount,
            stats.ladderHealth.demotionRiskCount,
            stats.ladderHealth.totalActiveItems
        )
    }
    return StatsCardModel(
        title = StatsTextCopy.verdictTitle(working),
        body = body,
        fillColor = fillColor,
        strokeColor = strokeColor,
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

private fun MainActivityStats.weaknessBurnDownCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    return outcomeCard(
        title = "Weakness Burn-Down",
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

private fun MainActivityStats.supportConversionCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    return outcomeCard(
        title = "Anki Support Conversion",
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

private fun MainActivityStats.notHelpingCard(report: KanjiImpactAnalyzer.Report?): StatsCardModel {
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
        if (report != null && report.needsMoreCardsCount > 0) {
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
        title = "Kani Not Helping Yet",
        summary = StudyTextCopy.countText(rows.size, "kanji with enough evidence", "kanji with enough evidence"),
        body = StatsTextCopy.notHelpingBody(report == null || report.empty(), rows.isNotEmpty()),
        lines = details,
        strokeColor = STATS_CORAL_COLOR,
        titleColor = STATS_MUTED_COLOR,
        summaryColor = STATS_INK_COLOR,
        bodyColor = STATS_MUTED_COLOR
    )
}

private fun MainActivityStats.ladderHealthCard(metric: StudyStatsStore.LadderHealthMetric): StatsCardModel {
    return outcomeCard(
        title = "Ladder Health",
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

private fun studyTimeCard(stats: StudyStatsStore.StudyTaskTimeStats): StatsCardModel {
    return StatsCardModel(
        title = "Answered study time",
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
