package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import org.junit.Assert.assertTrue
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
                actionBar = {
                    ProgressAnalyticsBottomNav(
                        selectedTab = ProgressAnalyticsBottomNavTab.Progress,
                        onHome = {},
                        onStudy = {},
                        onProgress = {},
                        onProfile = {},
                    )
                }
            )
        }

        composeRule.onNodeWithText("Stats overview").assertIsDisplayed()
        composeRule.onNodeWithText("Reviews analytics").assertIsDisplayed()
        composeRule.onNodeWithText("Accuracy & retention").assertIsDisplayed()
        composeRule.onNodeWithText("Progress by level").assertIsDisplayed()
        composeRule.onNodeWithText("Weakness insights").assertIsDisplayed()
        composeRule.onNodeWithText("2,842").assertIsDisplayed()
        composeRule.onNodeWithText("1,066").assertIsDisplayed()
        composeRule.onNodeWithText("126 / 1,026").assertIsDisplayed()
        composeRule.onNodeWithText("Focus score 72 out of 100. Needs improvement.").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationHomeItemInvokesCallback() {
        var homeClicked = false

        composeRule.setContent {
            ProgressAnalyticsBottomNav(
                selectedTab = ProgressAnalyticsBottomNavTab.Progress,
                onHome = { homeClicked = true },
                onStudy = {},
                onProgress = {},
                onProfile = {},
            )
        }

        composeRule.onNodeWithTag(progressAnalyticsBottomNavItemTestTag(ProgressAnalyticsBottomNavTab.Home))
            .performClick()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
        }
    }
}
