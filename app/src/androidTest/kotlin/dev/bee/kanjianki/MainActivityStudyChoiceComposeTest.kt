package dev.bee.kanjianki

import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun rendersSimilarChoiceSessionCardAndInvokesSelection() {
        var selected = ""

        composeRule.setContent {
            SimilarChoiceSessionCard(
                model = SimilarChoiceSessionModel(
                    modeLabel = "Recognise",
                    title = "Choose the kanji",
                    taskLabel = MainActivityBase.LABEL_SIMILAR_KANJI,
                    body = "Pick the kanji that matches the meaning.",
                    reasonLine = "Weak Anki evidence",
                    question = "Which kanji means split?",
                    gridModel = SimilarChoiceGridModel(
                        choices = listOf("裂", "列", "烈"),
                        balanceLastRow = true,
                        onChoice = SimilarChoiceHandler { selected = it }
                    )
                )
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onNodeWithText("Choose the kanji").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_SIMILAR_KANJI).assertIsDisplayed()
        composeRule.onNodeWithText("Pick the kanji that matches the meaning.").assertIsDisplayed()
        composeRule.onNodeWithText("Weak Anki evidence").assertIsDisplayed()
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()

        composeRule.onNodeWithText("烈").performClick()

        assertEquals("烈", selected)
    }

    @Test
    fun rendersMeaningChoiceSessionCardAndRevealsAnswerOnSelection() {
        var selected = ""
        var answerPanel: TextView? = null

        composeRule.setContent {
            val context = LocalContext.current
            val panel = remember {
                TextView(context).apply {
                    text = "Answer detail"
                    visibility = View.GONE
                }
            }
            answerPanel = panel
            MeaningChoiceSessionCard(
                model = MeaningChoiceSessionModel(
                    modeLabel = "Recall",
                    title = "Choose the kanji",
                    taskLabel = "meaning -> kanji",
                    body = "Pick the kanji that matches the meaning.",
                    reasonLine = "",
                    question = "Which kanji means split?",
                    choices = listOf("裂", "列", "烈", "劣"),
                    answerPanel = panel,
                    onChoice = SimilarChoiceHandler { selected = it }
                )
            )
        }

        composeRule.onNodeWithText("Recall").assertIsDisplayed()
        composeRule.onNodeWithText("Choose the kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()
        composeRule.runOnIdle {
            assertNotNull(answerPanel)
            assertEquals(View.GONE, answerPanel?.visibility)
        }

        composeRule.onNodeWithText("裂").performClick()

        composeRule.runOnIdle {
            assertEquals("裂", selected)
            assertEquals(View.VISIBLE, answerPanel?.visibility)
        }
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
