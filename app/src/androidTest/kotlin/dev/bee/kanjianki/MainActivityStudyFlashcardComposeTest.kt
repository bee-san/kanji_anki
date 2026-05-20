package dev.bee.kanjianki

import android.widget.EditText
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun rendersRecognitionPill() {
        composeRule.setContent {
            RecognitionPill("Recognise")
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
    }

    @Test
    fun rendersFlashcardPromptHeaderWithReason() {
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
        composeRule.onNodeWithText("Weak Anki evidence").assertIsDisplayed()
    }

    @Test
    fun omitsFlashcardPromptHeaderReasonWhenEmpty() {
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
    fun rendersTypingMeaningAnswerWithNativeInput() {
        var inputRef: EditText? = null

        composeRule.setContent {
            val context = LocalContext.current
            val input = remember {
                EditText(context).apply {
                    setText("split")
                }
            }
            inputRef = input
            TypingMeaningAnswer(label = MainActivityBase.LABEL_MEANING, input = input)
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_MEANING).assertIsDisplayed()
        composeRule.runOnIdle {
            assertNotNull(inputRef)
            assertEquals("split", inputRef?.text.toString())
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
