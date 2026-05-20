package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityStudyChoiceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSimilarChoiceGridAndInvokesSelection() {
        var selected = ""

        composeRule.setContent {
            SimilarChoiceGrid(
                model = SimilarChoiceGridModel(
                    choices = listOf("裂", "列", "烈"),
                    balanceLastRow = true,
                    onChoice = SimilarChoiceHandler { selected = it }
                )
            )
        }

        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("列").assertIsDisplayed()
        composeRule.onNodeWithText("烈").assertIsDisplayed()

        composeRule.onNodeWithText("列").performClick()

        assertEquals("列", selected)
    }
}
