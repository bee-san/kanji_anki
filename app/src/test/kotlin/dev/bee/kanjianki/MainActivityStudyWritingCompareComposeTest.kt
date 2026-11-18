package dev.bee.kanjianki

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.bee.kanjianki.core.study.InkPoint
import dev.bee.kanjianki.core.study.InkStroke
import dev.bee.kanjianki.core.study.StrokeDiagnosis
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.WritingCompareModel
import dev.bee.kanjianki.core.study.WritingSample
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyWritingCompareComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun comparePanelShowsBothSidesWithALegendAndSpokenVerdicts() {
        val state = WritingCompareState()
        state.show(
            WritingCompareModel.from(
                guide(3),
                sample(2),
                StrokeDiagnosis.builder()
                    .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                    .add(StrokeDiagnosis.Label.MISSING_STROKE, 3)
                    .build(),
            )
        )
        composeRule.setContent { KaniTheme { WritingComparePanel(state) } }

        composeRule.onNodeWithTag(WritingCompareTestTags.PANEL).assertIsDisplayed()
        composeRule.onNodeWithText("Yours").assertIsDisplayed()
        composeRule.onNodeWithText("Model").assertIsDisplayed()
        composeRule.onNodeWithTag(WritingCompareTestTags.YOURS)
            .assertContentDescriptionEquals("Yours, 2 strokes. stroke 1: Wrong order.")
        composeRule.onNodeWithTag(WritingCompareTestTags.MODEL)
            .assertContentDescriptionEquals("Model, 3 strokes. stroke 1: Wrong order; stroke 3: Missing.")
        composeRule.onNodeWithTag(WritingCompareTestTags.LEGEND_PREFIX + "SEQUENCE").assertIsDisplayed()
        composeRule.onNodeWithText("Wrong order").assertIsDisplayed()
        composeRule.onNodeWithTag(WritingCompareTestTags.LEGEND_PREFIX + "COUNT").assertIsDisplayed()
        composeRule.onNodeWithText("Missing").assertIsDisplayed()
        composeRule.onAllNodesWithTag(WritingCompareTestTags.LEGEND_PREFIX + "SHAPE").assertCountEquals(0)

        composeRule.runOnIdle { state.hide() }

        composeRule.onAllNodesWithTag(WritingCompareTestTags.PANEL).assertCountEquals(0)
    }

    @Test
    fun cleanComparisonHasNoLegend() {
        val state = WritingCompareState()
        state.show(WritingCompareModel.from(guide(2), sample(2), null))
        composeRule.setContent { KaniTheme { WritingComparePanel(state) } }

        composeRule.onNodeWithTag(WritingCompareTestTags.YOURS).assertContentDescriptionEquals("Yours, 2 strokes.")
        listOf("SHAPE", "SEQUENCE", "COUNT").forEach { group ->
            composeRule.onAllNodesWithTag(WritingCompareTestTags.LEGEND_PREFIX + group).assertCountEquals(0)
        }
    }

    @Test
    fun orderAndDirectionShareOneLegendEntry() {
        val state = WritingCompareState()
        state.show(
            WritingCompareModel.from(
                guide(2),
                sample(2),
                StrokeDiagnosis.builder()
                    .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                    .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 2)
                    .build(),
            )
        )
        composeRule.setContent { KaniTheme { WritingComparePanel(state) } }

        composeRule.onAllNodesWithTag(WritingCompareTestTags.LEGEND_PREFIX + "SEQUENCE").assertCountEquals(1)
        composeRule.onNodeWithText("Wrong order or direction").assertIsDisplayed()
        composeRule.onNodeWithTag(WritingCompareTestTags.YOURS)
            .assertContentDescriptionEquals("Yours, 2 strokes. stroke 1: Wrong order; stroke 2: Wrong direction.")
    }

    private fun guide(count: Int): StrokeGuide {
        return StrokeGuide("拉", (0 until count).map { line(0.1f, 0.2f + it * 0.2f, 0.9f, 0.2f + it * 0.2f) })
    }

    private fun sample(count: Int): WritingSample {
        return WritingSample((0 until count).map { line(20f, 40f + it * 40f, 180f, 40f + it * 40f) }, 200f, 200f)
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float): InkStroke {
        return InkStroke(listOf(InkPoint(x1, y1, 0L), InkPoint(x2, y2, 1L)))
    }
}
