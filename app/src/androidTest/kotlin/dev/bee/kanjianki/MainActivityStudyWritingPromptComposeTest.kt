package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingPromptComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRecallWritingPromptHeader() {
        composeRule.setContent {
            WritingPromptHeader(
                model = WritingPromptHeaderModel(
                    modeLabel = "Practice",
                    title = "Draw this kanji",
                    taskLabel = "Write kanji",
                    reasonLine = "Weak Anki evidence",
                    detailLines = listOf(
                        WritingPromptLineModel(
                            text = "Prompt: split, rend",
                            sizeSp = 17,
                            color = MainActivityUiSupport.STUDY_PLUM,
                            bold = true
                        ),
                        WritingPromptLineModel(
                            text = "Reading: レツ",
                            sizeSp = 15,
                            color = MainActivityUiSupport.STUDY_MUTED,
                            bold = false
                        ),
                        WritingPromptLineModel(
                            text = "Write the kanji from this prompt. The answer stays hidden until you check.",
                            sizeSp = 15,
                            color = MainActivityUiSupport.STUDY_MUTED,
                            bold = false
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Practice").assertIsDisplayed()
        composeRule.onNodeWithText("Draw this kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Write kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Weak Anki evidence").assertIsDisplayed()
        composeRule.onNodeWithText("Prompt: split, rend").assertIsDisplayed()
        composeRule.onNodeWithText("Reading: レツ").assertIsDisplayed()
        composeRule.onNodeWithText("Write the kanji from this prompt. The answer stays hidden until you check.").assertIsDisplayed()
    }

    @Test
    fun omitsReasonWhenEmpty() {
        composeRule.setContent {
            WritingPromptHeader(
                model = WritingPromptHeaderModel(
                    modeLabel = "Practice",
                    title = "Draw this kanji",
                    taskLabel = "Write kanji",
                    reasonLine = "",
                    detailLines = listOf(
                        WritingPromptLineModel(
                            text = "Learn it from the reference, trace it, then check.",
                            sizeSp = 15,
                            color = MainActivityUiSupport.STUDY_MUTED,
                            bold = false
                        )
                    )
                )
            )
        }

        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
        composeRule.onNodeWithText("Learn it from the reference, trace it, then check.").assertIsDisplayed()
    }
}
