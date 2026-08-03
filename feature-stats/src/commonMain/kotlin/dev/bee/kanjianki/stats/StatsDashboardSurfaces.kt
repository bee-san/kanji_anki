package dev.bee.kanjianki.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.StatsAccuracy
import dev.bee.kanjianki.presentation.StatsByLevel
import dev.bee.kanjianki.presentation.StatsDashboard
import dev.bee.kanjianki.presentation.StatsForecast
import dev.bee.kanjianki.presentation.StatsOverview
import dev.bee.kanjianki.presentation.StatsReviews
import dev.bee.kanjianki.presentation.StatsWeakness
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val STATS_DASHBOARD_TEST_TAG: String = "kani-stats-dashboard"
const val STATS_FORECAST_TEST_TAG: String = "kani-stats-forecast"
const val STATS_OVERVIEW_TEST_TAG: String = "kani-stats-overview"
const val STATS_REVIEWS_TEST_TAG: String = "kani-stats-reviews"
const val STATS_ACCURACY_TEST_TAG: String = "kani-stats-accuracy"
const val STATS_LEVEL_TEST_TAG: String = "kani-stats-level"
const val STATS_WEAKNESS_TEST_TAG: String = "kani-stats-weakness"

/** One most-missed kanji row, tagged by the kanji it opens. */
fun statsMissedKanjiTestTag(kanji: String): String = "kani-stats-missed-$kanji"

/**
 * The progress-analytics dashboard, from one portable [StatsDashboard].
 *
 * The same six sections the Android host rendered — forecast, overview, reviews,
 * accuracy, by-level, weakness — from the shared model rather than an `:app`-internal
 * one. The analytics are computed in `:core`/`:application`; this only lays them out,
 * which is the checkable form of "both hosts show the same stats from the same facts".
 *
 * A most-missed kanji opens its browse detail through [dispatch]; nothing else here is
 * interactive, because a dashboard is a report. Range switching (the reviews and
 * accuracy sections carry their available ranges) is a follow-up: the portable model
 * already holds `selectedRange`/`availableRanges`, so the chips are additive.
 */
@Composable
fun StatsDashboardScreen(
    dashboard: StatsDashboard,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATS_DASHBOARD_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        dashboard.forecast?.let { ForecastSection(it) }
        OverviewSection(dashboard.overview)
        ReviewsSection(dashboard.reviews)
        AccuracySection(dashboard.accuracy)
        LevelSection(dashboard.progressByLevel)
        WeaknessSection(dashboard.weakness, dispatch)
    }
}

@Composable
private fun StatsSection(tag: String, title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun ForecastSection(forecast: StatsForecast) {
    StatsSection(STATS_FORECAST_TEST_TAG, forecast.headline) {
        StatsLineChartView(forecast.burnDown, listOf(KaniTheme.colors.primary))
        Text(
            text = forecast.assumption,
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
    }
}

@Composable
private fun OverviewSection(overview: StatsOverview) {
    StatsSection(STATS_OVERVIEW_TEST_TAG, overview.title) {
        Text(text = overview.subtitle, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroMetric("", overview.streakValue, Modifier.weight(1f))
            HeroMetric("", overview.accuracyValue, Modifier.weight(1f))
            HeroMetric("", overview.reviewsTodayValue, Modifier.weight(1f))
        }
        StatsLineChartView(overview.reviewsOverTime, listOf(KaniTheme.colors.primary))
        StatsDonutChartView(
            overview.cardTypeBreakdown,
            listOf(KaniTheme.colors.primary, KaniTheme.colors.teal, KaniTheme.colors.blue, KaniTheme.colors.coral),
        )
        StatsDonutChartView(
            overview.correctIncorrectBreakdown,
            listOf(KaniTheme.colors.teal, KaniTheme.colors.coral),
        )
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = value, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp, fontWeight = FontWeight.Bold)
        if (label.isNotBlank()) {
            Text(text = label, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
    }
}

@Composable
private fun ReviewsSection(reviews: StatsReviews) {
    StatsSection(STATS_REVIEWS_TEST_TAG, reviews.title) {
        StatsBarChartView(reviews.reviewsPerDay, KaniTheme.colors.primary)
        Text(text = reviews.tip, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        reviews.heatmap?.let { StatsHeatmapView(it, KaniTheme.colors.primary) }
    }
}

@Composable
private fun AccuracySection(accuracy: StatsAccuracy) {
    StatsSection(STATS_ACCURACY_TEST_TAG, accuracy.title) {
        StatsLineChartView(accuracy.accuracyTrend, listOf(KaniTheme.colors.primary))
        for (row in accuracy.retentionByCardType) {
            LabeledPercentRow(row.label, row.valueLabel)
        }
        Text(text = accuracy.retentionSummary, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
    }
}

@Composable
private fun LevelSection(level: StatsByLevel) {
    StatsSection(STATS_LEVEL_TEST_TAG, level.title) {
        Text(text = level.overallLearnedLabel, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
        for (row in level.levelRows) {
            LabeledPercentRow(row.level, "${row.learned}/${row.total}")
        }
        StatsLineChartView(level.cumulativeProgress, listOf(KaniTheme.colors.primary))
    }
}

@Composable
private fun WeaknessSection(weakness: StatsWeakness, dispatch: (KaniAction) -> Unit) {
    StatsSection(STATS_WEAKNESS_TEST_TAG, weakness.title) {
        if (weakness.focusScoreAvailable) {
            LabeledPercentRow(weakness.focusScoreStatus, weakness.focusScoreValue)
        }
        for (row in weakness.weaknessRows) {
            LabeledPercentRow(row.label, "${row.accuracyPercent}%")
        }
        for (missed in weakness.mostMissedKanji) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(statsMissedKanjiTestTag(missed.kanji))
                    .semantics { contentDescription = "${missed.kanji} ${missed.misses}" },
                shape = KaniUiTokens.StudyShapeSmall,
                color = KaniTheme.colors.panelSoft,
                border = BorderStroke(1.dp, KaniTheme.colors.coral.copy(alpha = ROW_STROKE_ALPHA)),
                onClick = { dispatch(missed.action) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = missed.kanji, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp)
                    Text(text = missed.misses.toString(), color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
                }
            }
        }
    }
}

@Composable
private fun LabeledPercentRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
        Text(text = value, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
    }
}

private const val ROW_STROKE_ALPHA = 0.45f
