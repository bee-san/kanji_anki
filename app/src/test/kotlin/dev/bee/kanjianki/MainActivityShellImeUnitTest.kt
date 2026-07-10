package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
}
