package dev.bee.kanjianki

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        composeRule.onNodeWithText(StudyReviewButtonCopy.revealLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.revealLabel()))
            .assertIsDisplayed()
            .performClick()

        assertTrue(revealed)
    }

    @Test
    fun rendersAgainAndGoodButtonsAndInvokesActions() {
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

        composeRule.onNodeWithText(StudyReviewButtonCopy.againLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyReviewButtonCopy.goodLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.againLabel()))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.goodLabel()))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription(StudyReviewButtonCopy.againContentDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(StudyReviewButtonCopy.goodContentDescription()).assertIsDisplayed()

        assertTrue(failed)
        assertTrue(passed)
    }

    @Test
    fun rendersRecognitionPill() {
        composeRule.setContent {
            RecognitionPill("Recognise")
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
    }

    @Test
    fun hidesFlashcardPromptHeaderReason() {
        composeRule.setContent {
            FlashcardPromptHeader(
                model = FlashcardPromptHeaderModel(
                    modeLabel = "Recognise",
                    title = "What does this kanji mean?",
                    question = "Recall the meaning",
                    hiddenHint = "Answer hidden until reveal",
                    reasonLine = "Weak Anki evidence"
                )
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onNodeWithText("What does this kanji mean?").assertIsDisplayed()
        composeRule.onNodeWithText("Recall the meaning").assertIsDisplayed()
        composeRule.onNodeWithText("Answer hidden until reveal").assertIsDisplayed()
        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
    }

    @Test
    fun keepsFlashcardPromptHeaderCleanWhenReasonEmpty() {
        composeRule.setContent {
            FlashcardPromptHeader(
                model = FlashcardPromptHeaderModel(
                    modeLabel = "Recognise",
                    title = "What does this kanji mean?",
                    question = "Recall the meaning",
                    hiddenHint = "Answer hidden until reveal",
                    reasonLine = ""
                )
            )
        }

        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
    }

    @Test
    fun rendersFlashcardHeroPanel() {
        composeRule.setContent {
            FlashcardHeroPanel(
                model = FlashcardHeroPanelModel(
                    glyph = "裂",
                    glyphSizeSp = 116,
                    typeface = null
                )
            )
        }

        composeRule.onNodeWithText("裂").assertIsDisplayed()
    }

    @Test
    fun rendersFlashcardCardShell() {
        val revealState = FlashcardRevealState(false)
        composeRule.setContent {
            FlashcardCard(
                model = FlashcardCardModel(
                    promptHeader = FlashcardPromptHeaderModel(
                        modeLabel = "Recognise",
                        title = "Name this kanji",
                        question = "What does this kanji mean?",
                        hiddenHint = "Answer hidden until reveal",
                        reasonLine = ""
                    ),
                    heroPanel = FlashcardHeroPanelModel(
                        glyph = "裂",
                        glyphSizeSp = 116,
                        typeface = null
                    ),
                    typingAnswer = null,
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "裂",
                        glyphSizeSp = 76,
                        lines = listOf(StudyAnswerLineModel("split", Color(0xFF4B2552), 17, true)),
                        helperText = null
                    ),
                    revealState = revealState
                )
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onNodeWithText("Name this kanji").assertIsDisplayed()
        composeRule.onNodeWithText("What does this kanji mean?").assertIsDisplayed()
        composeRule.onAllNodesWithText("split").assertCountEquals(0)

        composeRule.runOnIdle {
            revealState.reveal()
        }

        composeRule.onNodeWithText("split").assertIsDisplayed()
    }

    @Test
    fun rendersTypingMeaningAnswerWithComposeInput() {
        var stateRef: TypingAnswerState? = null

        composeRule.setContent {
            val state = remember { TypingAnswerState("split") }
            stateRef = state
            TypingMeaningAnswer(label = MainActivityBase.LABEL_MEANING, state = state)
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_MEANING).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("split")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("split open")
        composeRule.runOnIdle {
            assertNotNull(stateRef)
            assertEquals("split open", stateRef?.getText().toString())
        }
    }

    @Test
    fun rendersStudyAnswerPanel() {
        composeRule.setContent {
            StudyAnswerPanel(
                model = StudyAnswerPanelModel(
                    title = "Answer",
                    glyph = "裂",
                    glyphSizeSp = 76,
                    lines = listOf(
                        StudyAnswerLineModel("split", Color(0xFF4B2552), 17, true),
                        StudyAnswerLineModel("Reading: レツ", Color(0xFFDA3A7A), 15, true)
                    ),
                    helperText = "Trace it below, then check."
                )
            )
        }

        composeRule.onNodeWithText("Answer").assertIsDisplayed()
        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("Reading: レツ").assertIsDisplayed()
        composeRule.onNodeWithText("Trace it below, then check.").assertIsDisplayed()
    }
}
