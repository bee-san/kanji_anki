package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

    @Test
    fun preservesLegacyChoiceGridSpacingAndOddRowBalance() {
        composeRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                SimilarChoiceGrid(
                    model = SimilarChoiceGridModel(
                        choices = listOf("裂", "列", "烈"),
                        balanceLastRow = true,
                        onChoice = SimilarChoiceHandler { }
                    )
                )
            }
        }

        composeRule.onNodeWithText("裂")
            .assertLeftPositionInRootIsEqualTo(4.dp)
            .assertTopPositionInRootIsEqualTo(8.dp)
            .assertWidthIsEqualTo(92.dp)
            .assertHeightIsEqualTo(82.dp)
        composeRule.onNodeWithText("列")
            .assertLeftPositionInRootIsEqualTo(104.dp)
            .assertTopPositionInRootIsEqualTo(8.dp)
            .assertWidthIsEqualTo(92.dp)
        composeRule.onNodeWithText("烈")
            .assertLeftPositionInRootIsEqualTo(4.dp)
            .assertTopPositionInRootIsEqualTo(98.dp)
            .assertWidthIsEqualTo(92.dp)
    }
}
