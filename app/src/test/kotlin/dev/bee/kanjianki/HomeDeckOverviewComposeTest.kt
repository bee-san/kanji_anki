package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDeckOverviewComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersDeckOverviewRows() {
        composeRule.setContent {
            HomeDeckOverview(listOf("Due 2", "New 1"))
        }

        composeRule.onNodeWithText("Deck overview").assertIsDisplayed()
        composeRule.onNodeWithText("Due 2").assertIsDisplayed()
        composeRule.onNodeWithText("New 1").assertIsDisplayed()
    }

    @Test
    fun skipsRenderingWhenRowsAreEmpty() {
        composeRule.setContent {
            HomeDeckOverview(emptyList())
        }

        composeRule.onAllNodesWithText("Deck overview").assertCountEquals(0)
        composeRule.onAllNodesWithText("Due 2").assertCountEquals(0)
    }
}
