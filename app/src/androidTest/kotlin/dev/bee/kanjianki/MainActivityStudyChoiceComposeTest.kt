package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun usesLegacyChoiceGridSpacingConstants() {
        assertEquals(4.dp, SimilarChoiceCellHorizontalPadding)
        assertEquals(8.dp, SimilarChoiceCellTopPadding)
        assertEquals(82.dp, SimilarChoiceButtonHeight)
    }

    @Test
    fun preservesOddRowBalanceWithInsetCells() {
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

        val first = boundsForChoice("裂")
        val second = boundsForChoice("列")
        val third = boundsForChoice("烈")

        assertTrue(first.left > 0f)
        assertTrue(first.top > 0f)
        assertTrue(second.left > first.right)
        assertTrue(third.top > first.bottom)
        assertEquals(first.left, third.left, POSITION_TOLERANCE_PX)
        assertEquals(first.width, second.width, SIZE_TOLERANCE_PX)
        assertEquals(first.width, third.width, SIZE_TOLERANCE_PX)
        assertEquals(first.height, third.height, SIZE_TOLERANCE_PX)
    }

    private fun boundsForChoice(glyph: String): Rect {
        return composeRule.onNodeWithTag(similarChoiceTestTag(glyph))
            .fetchSemanticsNode()
            .boundsInRoot
    }

    private companion object {
        private const val POSITION_TOLERANCE_PX = 1.0f
        private const val SIZE_TOLERANCE_PX = 1.0f
    }
}
