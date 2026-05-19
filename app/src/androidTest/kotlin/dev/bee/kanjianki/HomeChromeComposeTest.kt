package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersActionButtons() {
        composeRule.setContent {
            HomeActionGrid(
                actions = listOf(
                    HomeActionModel("Browse", R.drawable.ic_book_24) {},
                    HomeActionModel("Stats", R.drawable.ic_stats_24) {},
                    HomeActionModel("Settings", R.drawable.ic_settings_24) {},
                )
            )
        }

        composeRule.onNodeWithText("Browse").assertIsDisplayed()
        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun rendersSectionHeaderAndInvokesAction() {
        var clicked = false

        composeRule.setContent {
            HomeSectionHeader(
                title = "Focus queue",
                actionLabel = "View all",
                onAction = { clicked = true }
            )
        }

        composeRule.onNodeWithText("Focus queue").assertIsDisplayed()
        composeRule.onNodeWithText("View all >").assertIsDisplayed()
        composeRule.onNodeWithText("View all >").performClick()
        assertTrue(clicked)
    }

    @Test
    fun rendersHomeButtonAndInvokesAction() {
        var clicked = false

        composeRule.setContent {
            HomeFullWidthHomeButton(
                label = HomeTextCopy.homeLabel(),
                onClick = { clicked = true }
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).performClick()
        assertTrue(clicked)
    }
}
