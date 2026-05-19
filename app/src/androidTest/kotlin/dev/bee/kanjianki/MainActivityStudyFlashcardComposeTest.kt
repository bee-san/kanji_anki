package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyFlashcardComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRevealButtonAndInvokesAction() {
        var revealed = false

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = false,
                onReveal = { revealed = true },
                onFail = {},
                onPass = {}
            )
        }

        composeRule.onNodeWithText("Reveal").assertIsDisplayed()
        composeRule.onNodeWithText("Reveal").performClick()

        assertTrue(revealed)
    }

    @Test
    fun rendersFailAndPassButtonsAndInvokesActions() {
        var failed = false
        var passed = false

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = { failed = true },
                onPass = { passed = true }
            )
        }

        composeRule.onNodeWithText("Fail").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_PASS).assertIsDisplayed()
        composeRule.onNodeWithText("Fail").performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_PASS).performClick()

        assertTrue(failed)
        assertTrue(passed)
    }
}
