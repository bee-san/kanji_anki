package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Rule
import org.junit.Test

class MainActivityHomeOverviewComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAppTitleAndSubtitle() {
        composeRule.setContent {
            HomeHeader(
                title = HomeTextCopy.appTitle(),
                subtitle = HomeTextCopy.appSubtitle()
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.appTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.appSubtitle()).assertIsDisplayed()
    }
}
