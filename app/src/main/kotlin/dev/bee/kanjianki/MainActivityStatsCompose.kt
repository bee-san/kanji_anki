@file:JvmName("MainActivityStatsCompose")

package dev.bee.kanjianki

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

private const val INK_COLOR = 0xFF2D1635.toInt()
private const val MUTED_COLOR = 0xFF6C5674.toInt()
private const val CORAL_COLOR = 0xFFFF4C76.toInt()
private const val TEAL_COLOR = 0xFF00AEB5.toInt()
private const val GOLD_COLOR = 0xFFFFD640.toInt()
private const val BLUE_COLOR = 0xFF6E5CE6.toInt()
private const val VERDICT_WORKING_FILL = 0xFFEEFCFA.toInt()
private const val VERDICT_LADDER_FILL = 0xFFFFFAE2.toInt()
private const val VERDICT_IDLE_FILL = 0xFFF6F6F8.toInt()
private const val WHITE_COLOR = 0xFFFFFFFF.toInt()

data class StatsScreenModel(
    val title: String,
    val intro: String,
    val verdict: StatsCardModel,
    val sections: List<StatsCardModel>,
)

data class StatsCardModel(
    val title: String,
    val summary: String? = null,
    val body: String? = null,
    val lines: List<StatsLineModel> = emptyList(),
    val fillColor: Int = WHITE_COLOR,
    val strokeColor: Int,
    val titleColor: Int = MUTED_COLOR,
    val summaryColor: Int = INK_COLOR,
    val bodyColor: Int = MUTED_COLOR,
    val titleSizeSp: Int = 18,
    val summarySizeSp: Int = 25,
    val bodySizeSp: Int = 15,
)

data class StatsLineModel(
    val text: String,
    val color: Int = INK_COLOR,
    val bold: Boolean = true,
    val sizeSp: Int = 16,
)

fun statsScreenView(activity: Activity): View {
    val statsActivity = activity as MainActivityStats
    val model = statsActivity.buildStatsScreenModel()
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                StatsScreen(model = model)
            }
        }
    }
}

@Composable
fun StatsScreen(model: StatsScreenModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = model.title,
            style = statsTextStyle(sizeSp = 34, bold = true),
            color = ComposeColor(INK_COLOR)
        )
        Spacer(modifier = Modifier.height(7.dp))
        StatsCard(model.verdict)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = model.intro,
            style = statsTextStyle(sizeSp = 16, bold = false),
            color = ComposeColor(MUTED_COLOR)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            model.sections.forEach { card ->
                StatsCard(card)
            }
        }
    }
}

@Composable
private fun StatsCard(model: StatsCardModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = ComposeColor(model.fillColor),
        border = BorderStroke(1.dp, ComposeColor(model.strokeColor))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = model.title,
                style = statsTextStyle(sizeSp = model.titleSizeSp, bold = true),
                color = ComposeColor(model.titleColor)
            )
            model.summary?.let { summary ->
                Text(
                    text = summary,
                    style = statsTextStyle(sizeSp = model.summarySizeSp, bold = true),
                    color = ComposeColor(model.summaryColor)
                )
            }
            model.body?.let { body ->
                Text(
                    text = body,
                    style = statsTextStyle(sizeSp = model.bodySizeSp, bold = false),
                    color = ComposeColor(model.bodyColor)
                )
            }
            model.lines.forEach { line ->
                Text(
                    text = line.text,
                    style = statsTextStyle(sizeSp = line.sizeSp, bold = line.bold),
                    color = ComposeColor(line.color)
                )
            }
        }
    }
}

private fun statsTextStyle(sizeSp: Int, bold: Boolean): TextStyle {
    return TextStyle(
        fontSize = sizeSp.sp,
        lineHeight = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}

private fun MainActivityStats.buildStatsScreenModel(): StatsScreenModel {
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
        working -> VERDICT_WORKING_FILL
        hasLadder -> VERDICT_LADDER_FILL
        else -> VERDICT_IDLE_FILL
    }
    val strokeColor = when {
        working -> TEAL_COLOR
        hasLadder -> GOLD_COLOR
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
        titleColor = if (working) TEAL_COLOR else MUTED_COLOR,
        bodyColor = if (working) INK_COLOR else MUTED_COLOR,
        titleSizeSp = 24,
        bodySizeSp = 15
    )
}

private fun MainActivityStats.outcomeCard(
    title: String,
    summary: String,
    body: String,
    lines: List<StatsLineModel>,
    strokeColor: Int
): StatsCardModel {
    return StatsCardModel(
        title = title,
        summary = summary,
        body = body,
        lines = lines,
        strokeColor = strokeColor,
        titleColor = MUTED_COLOR,
        summaryColor = INK_COLOR,
        bodyColor = MUTED_COLOR,
        titleSizeSp = 18,
        summarySizeSp = 25,
        bodySizeSp = 15
    )
}

private fun MainActivityStats.weaknessBurnDownCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    return outcomeCard(
        title = "Weakness Burn-Down",
        summary = StudyTextCopy.countText(stats.weakKanjiImproved.improvedCount, "weak kanji improved", "weak kanji improved"),
        body = StatsTextCopy.weaknessImprovementBody(
            stats.weakKanjiImproved.improvedCount,
            stats.weakKanjiImproved.averageBeforeWeakness,
            stats.weakKanjiImproved.averageAfterWeakness
        ),
        lines = weaknessImprovementExamples(stats.weakKanjiImproved).map {
            StatsLineModel(
                text = it,
                color = INK_COLOR,
                bold = true,
                sizeSp = 17
            )
        },
        strokeColor = TEAL_COLOR
    )
}

private fun MainActivityStats.supportConversionCard(stats: StudyStatsStore.KaniOutcomeStats): StatsCardModel {
    return outcomeCard(
        title = "Anki Support Conversion",
        summary = StudyTextCopy.countText(stats.matureSupportGained.matureSupportGained, "mature card gained", "mature cards gained"),
        body = StudyTextCopy.countText(
            stats.matureSupportGained.firstSupportCount,
            "kanji gained first mature support",
            "kanji gained first mature support"
        ) + ".",
        lines = supportGainExamples(stats.matureSupportGained).map {
            StatsLineModel(
                text = it,
                color = INK_COLOR,
                bold = true,
                sizeSp = 17
            )
        },
        strokeColor = BLUE_COLOR
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
                    color = INK_COLOR,
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
                    color = MUTED_COLOR,
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
        strokeColor = CORAL_COLOR,
        titleColor = MUTED_COLOR,
        summaryColor = INK_COLOR,
        bodyColor = MUTED_COLOR
    )
}

private fun MainActivityStats.ladderHealthCard(metric: StudyStatsStore.LadderHealthMetric): StatsCardModel {
    return outcomeCard(
        title = "Ladder Health",
        summary = StudyTextCopy.countText(metric.totalActiveItems, "active kanji on the ladder", "active kanji on the ladder"),
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
                color = INK_COLOR,
                bold = false,
                sizeSp = 16
            )
        },
        strokeColor = GOLD_COLOR
    )
}

private fun MainActivityStats.studyTimeCard(stats: StudyStatsStore.StudyTaskTimeStats): StatsCardModel {
    return StatsCardModel(
        title = "Answered study time",
        summary = "Today: " + StatsTextCopy.formatStudyTime(stats.todayMillis),
        body = "Last 7 days: " + StatsTextCopy.formatStudyTime(stats.lastSevenDaysMillis),
        lines = listOf(
            StatsLineModel(
                text = "Answered tasks: " + stats.answeredTasks,
                color = MUTED_COLOR,
                bold = false,
                sizeSp = 16
            ),
            StatsLineModel(
                text = "Avg / task: " + StatsTextCopy.formatStudyTime(stats.averageMillisPerTask()),
                color = MUTED_COLOR,
                bold = false,
                sizeSp = 16
            )
        ),
        strokeColor = CORAL_COLOR,
        titleColor = MUTED_COLOR,
        summaryColor = INK_COLOR,
        bodyColor = MUTED_COLOR,
        titleSizeSp = 18,
        summarySizeSp = 24,
        bodySizeSp = 16
    )
}
