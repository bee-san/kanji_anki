package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}
