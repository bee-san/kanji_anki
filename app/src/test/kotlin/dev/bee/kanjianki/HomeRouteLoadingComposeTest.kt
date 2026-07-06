package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.HomeTextCopy
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
class HomeRouteLoadingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingScreenShowsTitleProgressAndHomeAction() {
        var homeTapped = false

        composeRule.setContent {
            HomeRouteLoadingScreen(
                title = "Kani",
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = { homeTapped = true },
            )
        }

        composeRule.onNodeWithText("Kani").assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.loadingLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("${HomeTextCopy.homeLabel()} >").performClick()
        assertTrue(homeTapped)
    }

    @Test
    fun errorScreenOffersRetryAndHomeWithoutCrashing() {
        var retries = 0
        var homeTapped = false

        composeRule.setContent {
            HomeRouteErrorScreen(
                title = HomeTextCopy.routeLoadErrorTitle(),
                retryLabel = HomeTextCopy.retryLabel(),
                onRetry = { retries += 1 },
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = { homeTapped = true },
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.routeLoadErrorTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.routeLoadErrorBody()).assertIsDisplayed()

        composeRule.onNodeWithText(HomeTextCopy.retryLabel()).performClick()
        assertEquals(1, retries)
        assertFalse(homeTapped)

        composeRule.onNodeWithText("${HomeTextCopy.homeLabel()} >").performClick()
        assertTrue(homeTapped)
    }
}
