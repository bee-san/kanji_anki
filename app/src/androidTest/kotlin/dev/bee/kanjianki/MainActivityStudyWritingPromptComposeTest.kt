package dev.bee.kanjianki

import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingPromptComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRecallWritingPromptHeaderWithoutSchedulerReason() {
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
        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
        composeRule.onNodeWithText("Prompt: split, rend").assertIsDisplayed()
        composeRule.onNodeWithText("Reading: レツ").assertIsDisplayed()
        composeRule.onNodeWithText("Write the kanji from this prompt. The answer stays hidden until you check.").assertIsDisplayed()
    }

    @Test
    fun keepsWritingPromptHeaderCleanWhenReasonEmpty() {
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

    @Test
    fun rendersWritingSessionCardShell() {
        val answerState = WritingAnswerPanelState(true)
        composeRule.setContent {
            val context = LocalContext.current
            WritingSessionCard(
                WritingSessionCardModel(
                    promptHeader = WritingPromptHeaderModel(
                        modeLabel = "Practice",
                        title = "Draw this kanji",
                        taskLabel = "Write kanji",
                        reasonLine = "",
                        detailLines = emptyList()
                    ),
                    answerPanel = writingAnswerPanelModel(),
                    answerPanelState = answerState,
                    writingTitle = "Writing",
                    writingTitleColor = MainActivityUiSupport.STUDY_PLUM,
                    status = WritingStatusState().apply {
                        setStatus("Trace the first strokes", MainActivityUiSupport.STUDY_MUTED)
                    },
                    drawingPad = DrawingPadView(context).apply { setTarget("裂") },
                    padMaxSizePx = 320,
                    resultStatus = WritingResultStatusHandle().apply {
                        show("Result", MainActivityUiSupport.STUDY_MUTED)
                    }
                )
            )
        }

        composeRule.onNodeWithText("Practice").assertIsDisplayed()
        composeRule.onNodeWithText("Draw this kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Reference answer").assertIsDisplayed()
        composeRule.onNodeWithText("Writing").assertIsDisplayed()
        composeRule.onNodeWithText("Trace the first strokes").assertIsDisplayed()
        composeRule.onNodeWithText("Result").assertIsDisplayed()
    }

    @Test
    fun keepsEmbeddedViewsAttachedAcrossRecomposition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousParent = FrameLayout(context)
        val drawingPad = DrawingPadView(context).apply { setTarget("裂") }
        previousParent.addView(drawingPad)
        val title = mutableStateOf("Writing")

        composeRule.setContent {
            WritingSessionCard(
                WritingSessionCardModel(
                    promptHeader = WritingPromptHeaderModel(
                        modeLabel = "Practice",
                        title = "Draw this kanji",
                        taskLabel = "Write kanji",
                        reasonLine = "",
                        detailLines = emptyList()
                    ),
                    answerPanel = writingAnswerPanelModel(),
                    answerPanelState = WritingAnswerPanelState(false),
                    writingTitle = title.value,
                    writingTitleColor = MainActivityUiSupport.STUDY_PLUM,
                    status = WritingStatusState().apply {
                        setStatus("Trace the first strokes", MainActivityUiSupport.STUDY_MUTED)
                    },
                    drawingPad = drawingPad,
                    padMaxSizePx = 320,
                    resultStatus = WritingResultStatusHandle().apply {
                        show("Result", MainActivityUiSupport.STUDY_MUTED)
                    }
                )
            )
        }

        composeRule.waitForIdle()
        assertNotSame(previousParent, drawingPad.parent)
        assertNotNull(drawingPad.parent)

        composeRule.runOnIdle {
            title.value = "Writing again"
        }
        composeRule.waitForIdle()

        assertNotNull(drawingPad.parent)
        composeRule.onNodeWithText("Writing again").assertIsDisplayed()
        composeRule.onNodeWithText("Trace the first strokes").assertIsDisplayed()
        composeRule.onNodeWithText("Result").assertIsDisplayed()
    }

    private fun writingAnswerPanelModel(): StudyAnswerPanelModel {
        return StudyAnswerPanelModel(
            title = "Reference",
            glyph = "裂",
            glyphSizeSp = 72,
            lines = listOf(
                StudyAnswerLineModel(
                    text = "Reference answer",
                    color = androidx.compose.ui.graphics.Color(MainActivityUiSupport.STUDY_PLUM),
                    sizeSp = 17,
                    bold = true
                )
            ),
            helperText = null
        )
    }
}
