package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
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

    @Test
    fun rendersStudyCtaAndInvokesClick() {
        var clicked = false

        composeRule.setContent {
            HomeStudyCta(
                title = MainActivityBase.LABEL_STUDY_NOW,
                subtitle = HomeTextCopy.studySupportText(),
                onClick = { clicked = true }
            )
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_STUDY_NOW).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.studySupportText()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(MainActivityBase.LABEL_STUDY_NOW).performClick()
        assertTrue(clicked)
    }
}
