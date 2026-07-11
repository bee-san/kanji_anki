package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityShellImeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun navActions() = KaniNavActions(
        onHome = {},
        onStudy = {},
        onStats = {},
        onSettings = {},
    )

    @Test
    fun bottomNavHidesWhileKeyboardIsOpen() {
        // With the keyboard up the nav bar is untappable chrome that steals ~90dp of
        // the shrunken viewport, pushing the study prompt off-screen.
        composeRule.setContent {
            MainActivityRouteContent(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                navActions = navActions(),
                imeVisible = true,
            ) {
                Text("route content")
            }
        }

        composeRule.onAllNodesWithTag("kani-bottom-nav").assertCountEquals(0)
    }

    @Test
    fun bottomNavShowsWhileKeyboardIsClosed() {
        composeRule.setContent {
            MainActivityRouteContent(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                navActions = navActions(),
                imeVisible = false,
            ) {
                Text("route content")
            }
        }

        composeRule.onNodeWithTag("kani-bottom-nav").assertIsDisplayed()
    }

    @Test
    fun bottomNavHiddenForUnrevealedTypingCardEvenBeforeImeOpens() {
        // KB1: a typing card is keyboard-resident, so the nav is absent from its
        // first frame — even with the IME inset still zero — instead of vanishing
        // mid-animation when the auto-focus opens the keyboard.
        composeRule.setContent {
            MainActivityRouteContent(
                model = MainActivityShellModel(
                    selectedRoute = MainActivityBase.NAV_STUDY,
                    studyCardKeyboardResident = true,
                ),
                navActions = navActions(),
                imeVisible = false,
            ) {
                Text("route content")
            }
        }

        composeRule.onAllNodesWithTag("kani-bottom-nav").assertCountEquals(0)
    }

    @Test
    fun bottomNavHidesForActiveNonTypingStudyCardWithKeyboardClosed() {
        composeRule.setContent {
            MainActivityRouteContent(
                model = MainActivityShellModel(
                    selectedRoute = MainActivityBase.NAV_STUDY,
                    studyCardKeyboardResident = false,
                    studySessionActive = true,
                ),
                navActions = navActions(),
                imeVisible = false,
            ) {
                Text("route content")
            }
        }

        composeRule.onAllNodesWithTag("kani-bottom-nav").assertCountEquals(0)
    }

    @Test
    fun bottomNavStillShowsForInactiveStudyScreen() {
        composeRule.setContent {
            MainActivityRouteContent(
                model = MainActivityShellModel(
                    selectedRoute = MainActivityBase.NAV_STUDY,
                    studySessionActive = false,
                ),
                navActions = navActions(),
                imeVisible = false,
            ) {
                Text("route content")
            }
        }

        composeRule.onNodeWithTag("kani-bottom-nav").assertIsDisplayed()
    }

    @Test
    fun contentRevisionResetsRouteLocalStateWithoutReinstallingTheShell() {
        var contentKey by mutableIntStateOf(1)
        var createdContentStates = 0

        composeRule.setContent {
            MainActivityRouteContent(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                navActions = navActions(),
                imeVisible = false,
                contentKey = contentKey,
            ) {
                val instance = remember { ++createdContentStates }
                Text("route-instance-$instance")
            }
        }

        composeRule.onNodeWithText("route-instance-1").assertIsDisplayed()
        composeRule.onNodeWithTag("kani-bottom-nav").assertIsDisplayed()

        composeRule.runOnIdle { contentKey += 1 }

        composeRule.onNodeWithText("route-instance-2").assertIsDisplayed()
        composeRule.onNodeWithTag("kani-bottom-nav").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, createdContentStates) }
    }
}
