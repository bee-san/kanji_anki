package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniBottomNavComposeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSubroutesKeepSettingsTabSelected() {
        composeRule.setContent {
            KaniBottomNavBar(
                selectedRoute = MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE,
                actions = KaniNavActions(
                    onHome = {},
                    onStudy = {},
                    onStats = {},
                    onSettings = {},
                ),
            )
        }

        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_SETTINGS_ROUTE))
            .assertIsSelected()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_HOME_ROUTE))
            .assertIsNotSelected()
    }

    @Test
    fun studyBadgeShowsCappedDueCount() {
        composeRule.setContent {
            KaniBottomNavBar(
                selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                studyBadgeCount = 132,
                actions = KaniNavActions(
                    onHome = {},
                    onStudy = {},
                    onStats = {},
                    onSettings = {},
                ),
            )
        }

        composeRule.onAllNodesWithTag("kani-nav-badge", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("99+", useUnmergedTree = true).assertCountEquals(1)
        assertEquals("7", kaniNavBadgeLabel(7))
        assertEquals("99+", kaniNavBadgeLabel(100))
    }

    @Test
    fun studyBadgeIsHiddenWhenDueCountIsZero() {
        composeRule.setContent {
            KaniBottomNavBar(
                selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                studyBadgeCount = 0,
                actions = KaniNavActions(
                    onHome = {},
                    onStudy = {},
                    onStats = {},
                    onSettings = {},
                ),
            )
        }

        composeRule.onAllNodesWithTag("kani-nav-badge", useUnmergedTree = true).assertCountEquals(0)
    }
}
