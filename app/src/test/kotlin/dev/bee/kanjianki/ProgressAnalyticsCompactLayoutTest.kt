package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressAnalyticsCompactLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactPhoneUsesHeroSummaryAndTwoColumnOverviewGrid() {
        composeRule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                ProgressAnalyticsDashboardScreen(
                    state = progressAnalyticsDemoSnapshot(1_747_000_000_000L),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(ProgressOverviewHeroSummaryTag).assertCountEquals(1)
        composeRule.onAllNodesWithTag(ProgressOverviewMetricsCompactTag).assertCountEquals(1)
        composeRule.onAllNodesWithTag(ProgressDistributionCardCompactLayoutTag).assertCountEquals(2)
    }

    @Test
    fun layoutPolicyHelpersSwitchAtCompactBreakpoint() {
        assertEquals(2, progressAnalyticsOverviewMetricColumns(360.dp))
        assertEquals(3, progressAnalyticsOverviewMetricColumns(420.dp))
        assertTrue(progressAnalyticsDistributionUsesStackedLegendLayout(360.dp))
        assertFalse(progressAnalyticsDistributionUsesStackedLegendLayout(420.dp))
    }
}
