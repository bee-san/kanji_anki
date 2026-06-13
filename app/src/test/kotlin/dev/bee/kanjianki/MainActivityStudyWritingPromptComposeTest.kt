package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyWritingPromptComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun writingPromptHeaderShowsWhyThisPromptReason() {
        composeRule.setContent {
            WritingPromptHeader(
                WritingPromptHeaderModel(
                    modeLabel = "Review",
                    title = "Draw this kanji",
                    taskLabel = "Write kanji",
                    reasonLine = "Why this prompt: writing fell behind recognition",
                    detailLines = listOf(
                        WritingPromptLineModel(
                            text = "Prompt: split, rend",
                            sizeSp = 17,
                            color = MainActivityUiSupport.STUDY_PLUM,
                            bold = true,
                        )
                    ),
                )
            )
        }

        composeRule.onNodeWithText("Why this prompt: writing fell behind recognition").assertIsDisplayed()
        composeRule.onNodeWithText("Prompt: split, rend").assertIsDisplayed()
    }
}
