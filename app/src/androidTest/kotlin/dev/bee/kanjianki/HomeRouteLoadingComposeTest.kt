package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRouteLoadingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersLoadingScreenCopyAndSpinnerAtPhoneWidth() {
        var homeClicked = false

        composeRule.setContent {
            Box(modifier = Modifier.size(360.dp, 720.dp)) {
                HomeRouteLoadingScreen(
                    title = "Reviews analytics",
                    homeLabel = HomeTextCopy.homeLabel(),
                    onHome = { homeClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Reviews analytics").assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.loadingLabel()).assertIsDisplayed()
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        composeRule.onNodeWithText("${HomeTextCopy.homeLabel()} >").performClick()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
        }
    }
}
