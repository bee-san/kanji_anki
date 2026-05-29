package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
                    onChoice = KanjiChoiceHandler { selected = it }
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
                        onChoice = KanjiChoiceHandler { selected = it }
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

        composeRule.setContent {
            MeaningChoiceSessionCard(
                model = MeaningChoiceSessionModel(
                    modeLabel = "Recall",
                    title = "Choose the kanji",
                    taskLabel = "meaning -> kanji",
                    body = "Pick the kanji that matches the meaning.",
                    reasonLine = "",
                    question = "Which kanji means split?",
                    choices = listOf("裂", "列", "烈", "劣"),
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "裂",
                        glyphSizeSp = 76,
                        lines = listOf(
                            StudyAnswerLineModel(
                                text = "Answer detail",
                                color = Color(0xFF2D1635),
                                sizeSp = 15,
                                bold = false
                            )
                        ),
                        helperText = null
                    ),
                    onChoice = KanjiChoiceHandler { selected = it }
                )
            )
        }

        composeRule.onNodeWithText("Recall").assertIsDisplayed()
        composeRule.onNodeWithText("Choose the kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Answer detail").assertCountEquals(0)

        composeRule.onNodeWithText("裂").performClick()

        composeRule.runOnIdle {
            assertEquals("裂", selected)
        }
        composeRule.onNodeWithText("Answer detail").assertIsDisplayed()
        composeRule.onNodeWithTag(similarChoiceTestTag("裂")).assertIsNotEnabled()
        composeRule.onNodeWithTag(similarChoiceTestTag("列")).assertIsNotEnabled()
    }

    @Test
    fun meaningChoiceOptionClickRecordsSelectedAnswer() {
        var selected = ""

        composeRule.setContent {
            MeaningChoiceSessionCard(
                model = meaningChoiceModel(
                    question = "Which kanji means split?",
                    choices = listOf("裂", "列", "烈", "劣"),
                    answerGlyph = "裂",
                    answerDetail = "Answer detail",
                    onChoice = { selected = it },
                )
            )
        }

        composeRule.onNodeWithText("列").performClick()

        assertEquals("列", selected)
    }

    @Test
    fun meaningChoiceResultNextActionRecordsSelectedAnswer() {
        var selected = ""

        composeRule.setContent {
            MeaningChoiceSessionCard(
                model = meaningChoiceModel(
                    question = "Which kanji means split?",
                    choices = listOf("裂", "列", "烈", "劣"),
                    answerGlyph = "裂",
                    answerDetail = "Answer detail",
                    onChoice = { selected = it },
                    resultResolver = MeaningChoiceResultResolver { glyph ->
                        MeaningChoiceResultModel(
                            status = "Selected: $glyph",
                            statusColor = MainActivityBase.TEAL,
                            actionLabel = MainActivityBase.LABEL_PASS,
                        )
                    },
                )
            )
        }

        composeRule.onNodeWithText("列").performClick()

        composeRule.onNodeWithText("Selected: 列").assertIsDisplayed()
        composeRule.onNodeWithTag(similarChoiceTestTag("裂")).assertIsNotEnabled()
        composeRule.onNodeWithTag(similarChoiceTestTag("烈")).assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals("", selected) }

        composeRule.onAllNodesWithText("Next").assertCountEquals(0)
        composeRule.onNodeWithText(MainActivityBase.LABEL_PASS).performClick()

        assertEquals("列", selected)
    }

    @Test
    fun meaningChoiceResultActionUsesFailLabelForWrongChoice() {
        composeRule.setContent {
            MeaningChoiceSessionCard(
                model = meaningChoiceModel(
                    question = "Which kanji means split?",
                    choices = listOf("裂", "列", "烈", "劣"),
                    answerGlyph = "裂",
                    answerDetail = "Answer detail",
                    onChoice = {},
                    resultResolver = MeaningChoiceResultResolver { glyph ->
                        MeaningChoiceResultModel(
                            status = "Selected: $glyph",
                            statusColor = MainActivityBase.CORAL,
                            actionLabel = MainActivityBase.LABEL_FAIL,
                        )
                    },
                )
            )
        }

        composeRule.onNodeWithText("列").performClick()

        composeRule.onNodeWithText("Selected: 列").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_FAIL).assertIsDisplayed()
        composeRule.onAllNodesWithText("Next").assertCountEquals(0)
    }

    @Test
    fun meaningChoiceSessionClearsRevealedAnswerWhenModelChanges() {
        var selected = ""
        val first = meaningChoiceModel(
            question = "Which kanji means split?",
            choices = listOf("裂", "列", "烈", "劣"),
            answerGlyph = "裂",
            answerDetail = "First answer detail",
            onChoice = { selected = it },
        )
        val second = meaningChoiceModel(
            question = "Which kanji means basics?",
            choices = listOf("基", "収", "保", "似"),
            answerGlyph = "基",
            answerDetail = "Second answer detail",
            onChoice = { selected = it },
        )
        var model by mutableStateOf(first)

        composeRule.setContent {
            MeaningChoiceSessionCard(model = model)
        }

        composeRule.onNodeWithText("裂").performClick()
        composeRule.onNodeWithText("First answer detail").assertIsDisplayed()

        composeRule.runOnIdle { model = second }

        composeRule.onNodeWithText("Which kanji means basics?").assertIsDisplayed()
        composeRule.onAllNodesWithText("First answer detail").assertCountEquals(0)
        composeRule.onAllNodesWithText("Second answer detail").assertCountEquals(0)
        composeRule.onNodeWithTag(similarChoiceTestTag("基")).assertIsEnabled()
        assertEquals("裂", selected)
    }

    @Test
    fun usesLegacyChoiceGridSpacingConstants() {
        assertEquals(4.dp, SimilarChoiceCellHorizontalPadding)
        assertEquals(8.dp, SimilarChoiceCellTopPadding)
        assertEquals(82.dp, SimilarChoiceButtonHeight)
    }

    @Test
    fun rendersMeaningChoiceResultActionBarAndInvokesNext() {
        var nextClicks = 0

        composeRule.setContent {
            MeaningChoiceResultActionBar(
                status = "Correct: 裂",
                statusColor = MainActivityUiSupport.TEAL,
                actionLabel = MainActivityBase.LABEL_PASS,
                onNext = { nextClicks++ }
            )
        }

        composeRule.onNodeWithText("Correct: 裂").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_PASS).assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_PASS).performClick()

        assertEquals(1, nextClicks)
    }

    @Test
    fun preservesOddRowBalanceWithInsetCells() {
        composeRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                SimilarChoiceGrid(
                    model = SimilarChoiceGridModel(
                        choices = listOf("裂", "列", "烈"),
                        balanceLastRow = true,
                        onChoice = KanjiChoiceHandler { }
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

        private fun meaningChoiceModel(
            question: String,
            choices: List<String>,
            answerGlyph: String,
            answerDetail: String,
            onChoice: (String) -> Unit,
            resultResolver: MeaningChoiceResultResolver? = null,
        ): MeaningChoiceSessionModel {
            return MeaningChoiceSessionModel(
                modeLabel = "Recall",
                title = "Choose the kanji",
                taskLabel = "meaning -> kanji",
                body = "Pick the kanji that matches the meaning.",
                reasonLine = "",
                question = question,
                choices = choices,
                answerPanel = StudyAnswerPanelModel(
                    title = "Answer",
                    glyph = answerGlyph,
                    glyphSizeSp = 76,
                    lines = listOf(
                        StudyAnswerLineModel(
                            text = answerDetail,
                            color = Color(0xFF2D1635),
                            sizeSp = 15,
                            bold = false,
                        )
                    ),
                    helperText = null,
                ),
                onChoice = KanjiChoiceHandler { onChoice(it) },
                resultResolver = resultResolver,
            )
        }
    }
}
