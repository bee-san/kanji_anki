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
    fun writingPromptHeaderShowsOnlyModeTitleAndInstruction() {
        composeRule.setContent {
            WritingPromptHeader(
                WritingPromptHeaderModel(
                    modeLabel = "Review",
                    title = "Draw this kanji",
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

        composeRule.onNodeWithText("Review").assertIsDisplayed()
        composeRule.onNodeWithText("Draw this kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Prompt: split, rend").assertIsDisplayed()
    }
}
