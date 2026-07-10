package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import org.junit.Rule
import org.junit.Test

class ProgressAnalyticsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersProgressAnalyticsRouteWithAllFiveSections() {
        composeRule.setContent {
            MainActivityComposeRouteWithActionBar(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STATS_ROUTE),
                content = {
                    ProgressAnalyticsDashboardScreen(
                        state = progressAnalyticsDemoSnapshot(1_747_000_000_000L)
                    )
                },
                actionBar = {}
            )
        }

        composeRule.onNodeWithText("Stats overview").assertIsDisplayed()
        composeRule.onNodeWithText("Reviews analytics").assertExists()
        composeRule.onNodeWithText("Accuracy by rung group").assertExists()
        composeRule.onNodeWithText("Ladder rung distribution").assertExists()
        composeRule.onNodeWithText("Weakness insights").assertExists()
        composeRule.onNodeWithText("2,842").assertExists()
        composeRule.onNodeWithText("1,066").assertExists()
        composeRule.onNodeWithText("126 active items").assertExists()
        composeRule.onNodeWithText("72 / 100").assertExists()
    }

    @Test
    fun compactPhoneStacksDistributionChartsVertically() {
        composeRule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                ProgressAnalyticsDashboardScreen(
                    state = progressAnalyticsDemoSnapshot(1_747_000_000_000L)
                )
            }
        }

        composeRule.onNodeWithTag(ProgressDistributionCardCompactLayoutTag).assertExists()
    }
}
