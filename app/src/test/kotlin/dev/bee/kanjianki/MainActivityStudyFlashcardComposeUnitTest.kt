package dev.bee.kanjianki

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyFlashcardComposeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingMeaningAnswerSubmitsOnImeAction() {
        var submitCount = 0
        val state = TypingAnswerState()

        composeRule.setContent {
            TypingMeaningAnswer(
                label = MainActivityBase.LABEL_MEANING,
                state = state,
                onDone = Runnable { submitCount++ },
            )
        }

        val textField = composeRule.onNode(hasSetTextAction())
        textField.performTextReplacement("prison")
        textField.performImeAction()

        assertTrue(submitCount == 1)
    }

    @Test
    fun typingMeaningSubmitKeyHelperMatchesEnterKeysOnKeyUpOnly() {
        assertTrue(isTypingMeaningSubmitKey(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_ENTER))
        assertTrue(isTypingMeaningSubmitKey(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER))
        assertFalse(isTypingMeaningSubmitKey(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun revealedFlashcardCardShowsAnswerOnlyAfterReveal() {
        val revealState = FlashcardRevealState(false)

        composeRule.setContent {
            FlashcardCard(
                model = FlashcardCardModel(
                    promptHeader = FlashcardPromptHeaderModel(
                        modeLabel = "Type",
                        title = "Meaning",
                        question = "What does it mean?",
                        hiddenHint = "Answer hidden until reveal",
                        reasonLine = "From 宮",
                    ),
                    heroPanel = FlashcardHeroPanelModel(
                        glyph = "獄",
                        glyphSizeSp = 64,
                        typeface = null,
                    ),
                    typingAnswer = null,
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "獄",
                        glyphSizeSp = 76,
                        lines = listOf(
                            StudyAnswerLineModel(
                                text = "split",
                                color = Color(0xFF2E1035),
                                sizeSp = 17,
                                bold = true,
                            )
                        ),
                        helperText = null,
                    ),
                    revealState = revealState,
                )
            )
        }

        composeRule.onAllNodesWithText("split").assertCountEquals(0)

        composeRule.runOnIdle {
            revealState.reveal()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("split").assertIsDisplayed()
    }
}
