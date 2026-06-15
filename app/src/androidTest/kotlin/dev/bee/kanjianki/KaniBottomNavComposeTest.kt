package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.NavigationCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KaniBottomNavComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun navActions(calls: MutableList<String>): KaniNavActions {
        return KaniNavActions(
            onHome = { calls += "home" },
            onStudy = { calls += "study" },
            onStats = { calls += "stats" },
            onSettings = { calls += "settings" },
        )
    }

    @Test
    fun showsAllFourTabsWithSelectionState() {
        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_HOME_ROUTE),
                navActions = navActions(mutableListOf()),
            ) {
                Text("Home content")
            }
        }

        composeRule.onNodeWithTag("kani-bottom-nav").assertIsDisplayed()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_HOME_ROUTE))
            .assertIsSelected()
            .assert(
                hasContentDescription(
                    NavigationCopy.navItemContentDescription(NavigationCopy.homeLabel(), true)
                )
            )
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_STUDY)).assertIsNotSelected()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_STATS_ROUTE)).assertIsNotSelected()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_SETTINGS_ROUTE)).assertIsNotSelected()
    }

    @Test
    fun tappingTabsInvokesNavigationCallbacks() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_HOME_ROUTE),
                navActions = navActions(calls),
            ) {
                Text("Home content")
            }
        }

        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_STUDY)).performClick()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_STATS_ROUTE)).performClick()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_SETTINGS_ROUTE)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("study", "stats", "settings"), calls)
        }
    }

    @Test
    fun tappingSelectedTabIsANoOp() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_HOME_ROUTE),
                navActions = navActions(calls),
            ) {
                Text("Home content")
            }
        }

        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_HOME_ROUTE)).performClick()
        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), calls)
        }
    }

    @Test
    fun actionBarRouteKeepsNavBelowActions() {
        composeRule.setContent {
            MainActivityComposeRouteWithActionBar(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                navActions = navActions(mutableListOf()),
                content = { Text("Study content") },
                actionBar = { Text("Action bar") },
            )
        }

        composeRule.onNodeWithTag("kani-bottom-nav").assertIsDisplayed()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_STUDY)).assertIsSelected()
    }
}
