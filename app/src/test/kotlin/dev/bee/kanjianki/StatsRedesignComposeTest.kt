package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.charts.KaniDonutChartTag
import dev.bee.kanjianki.charts.KaniHeatmapChartTag
import dev.bee.kanjianki.charts.KaniHeatmapWeekTag
import dev.bee.kanjianki.charts.KaniLineChartTag
import dev.bee.kanjianki.progress.ProgressStreakMetricState
import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsRedesignComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun heroForecastHeatmapAndRealChartSemanticsRender() {
        val state = progressAnalyticsDemoSnapshot(1_747_000_000_000L)
        composeRule.setContent { ProgressAnalyticsDashboardScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ProgressOverviewHeroSummaryTag).assertExists()
        composeRule.onNodeWithText("Streak").assertExists()
        composeRule.onNodeWithText("Accuracy").assertExists()
        composeRule.onNodeWithText("Reviews today").assertExists()
        composeRule.onNodeWithTag(ProgressStreakSummaryTag).assertExists()
        composeRule.onNodeWithText("Current streak").assertExists()
        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onNodeWithText("14 days").assertExists()
        composeRule.onNodeWithContentDescription(
            progressAnalyticsStreakSummaryText(state.reviewsAnalytics.currentStreak, state.reviewsAnalytics.tip),
        ).assertExists()
        composeRule.onNodeWithTag(ProgressForecastCardTag).assertExists()
        composeRule.onNodeWithTag(KaniHeatmapChartTag).assertExists()
        composeRule.onAllNodesWithTag(KaniHeatmapWeekTag).assertCountEquals(state.reviewsAnalytics.heatmap!!.weeks.size)
        composeRule.onNodeWithText("Sun", substring = true).assertExists()
        assertTrue(composeRule.onAllNodesWithText("Jul").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithContentDescription(state.reviewsAnalytics.heatmap!!.accessibilitySummary).assertExists()
        // The per-day bar chart is gone (Review calendar heatmap replaces it) but the
        // range summary metrics still render for the selected range.
        composeRule.onAllNodesWithTag("kani-bar-chart").assertCountEquals(0)
        assertTrue(composeRule.onAllNodesWithText("Total reviews", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Best day", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun emptyDataUsesHomeEmptyStatesAndRendersNoCharts() {
        val sample = progressAnalyticsDemoSnapshot(1_747_000_000_000L)
        val emptyLine = sample.overview.reviewsOverTime.copy(series = emptyList())
        val state = sample.copy(
            forecast = null,
            overview = sample.overview.copy(
                reviewsOverTime = emptyLine,
                cardTypeBreakdown = sample.overview.cardTypeBreakdown.copy(segments = emptyList()),
                correctIncorrectBreakdown = sample.overview.correctIncorrectBreakdown.copy(segments = emptyList()),
            ),
            reviewsAnalytics = sample.reviewsAnalytics.copy(
                heatmap = null,
                reviewsPerDay = sample.reviewsAnalytics.reviewsPerDay.copy(values = emptyList()),
                rangeData = emptyMap(),
                currentStreak = ProgressStreakMetricState(0, 0, "0 days"),
                tip = "Start a short review session today to build momentum.",
            ),
            accuracyRetention = sample.accuracyRetention.copy(
                accuracyTrend = sample.accuracyRetention.accuracyTrend.copy(series = emptyList()),
                retentionByCardType = emptyList(),
                rangeData = emptyMap(),
            ),
            progressByLevel = sample.progressByLevel.copy(
                levelRows = emptyList(),
                cumulativeProgress = sample.progressByLevel.cumulativeProgress.copy(series = emptyList()),
            ),
            weaknessInsights = sample.weaknessInsights.copy(
                weaknessRows = emptyList(), mostMissedKanji = emptyList(), supportNeeded = emptyList(), confusionPairs = emptyList(),
            ),
        )
        composeRule.setContent { ProgressAnalyticsDashboardScreen(state) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Your story starts here 🦀").assertCountEquals(5)
        composeRule.onAllNodesWithTag(ProgressStreakSummaryTag).assertCountEquals(1)
        composeRule.onNodeWithText("Current streak").assertExists()
        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onAllNodesWithText("0 days", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithContentDescription(
            progressAnalyticsStreakSummaryText(state.reviewsAnalytics.currentStreak, state.reviewsAnalytics.tip),
        ).assertExists()
        composeRule.onAllNodesWithTag(KaniLineChartTag).assertCountEquals(0)
        composeRule.onAllNodesWithTag(KaniDonutChartTag).assertCountEquals(0)
        composeRule.onAllNodesWithTag(KaniHeatmapChartTag).assertCountEquals(0)
    }

    @Test fun compactRangeChipsWrap() {
        composeRule.setContent {
            Box(Modifier.width(100.dp)) {
                ProgressRangeChips(
                    ranges = listOf(
                        dev.bee.kanjianki.progress.AnalyticsRange.SEVEN_DAYS,
                        dev.bee.kanjianki.progress.AnalyticsRange.THIRTY_DAYS,
                        dev.bee.kanjianki.progress.AnalyticsRange.NINETY_DAYS,
                    ),
                    selected = dev.bee.kanjianki.progress.AnalyticsRange.SEVEN_DAYS,
                    onSelect = {},
                    compact = true,
                    scope = "reviews",
                )
            }
        }
        composeRule.waitForIdle()
        val first = composeRule.onNodeWithTag(ProgressRangeChipTagPrefix + "reviews-SEVEN_DAYS").fetchSemanticsNode().boundsInRoot
        val last = composeRule.onNodeWithTag(ProgressRangeChipTagPrefix + "reviews-NINETY_DAYS").fetchSemanticsNode().boundsInRoot
        assertTrue("range chips should wrap on narrow width: first=$first, last=$last", last.top > first.top)
    }

    @Test fun confusionRowRoutesToBrowseTarget() {
        val pairs = progressAnalyticsDemoSnapshot(1_747_000_000_000L).weaknessInsights.confusionPairs
        var browsed = ""
        composeRule.setContent { ProgressConfusionCard(pairs, onBrowseKanji = { browsed = it }) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProgressConfusionRowTagPrefix + "徴").performClick()
        composeRule.runOnIdle { assertEquals("徴", browsed) }
    }

    @Test fun metricCardAnnouncesLabelOnceWithoutIconDuplication() {
        composeRule.setContent {
            KaniMetricCard(R.drawable.ic_stats_24, "Reviews", "42", "+3", KaniTheme.colors.primary)
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Reviews").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Reviews", substring = true).assertCountEquals(1)
    }
}
