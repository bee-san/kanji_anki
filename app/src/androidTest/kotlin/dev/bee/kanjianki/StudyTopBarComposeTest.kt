package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StudyTopBarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersProgressAndWiresCallbacks() {
        val closeClicks = AtomicInteger()
        val settingsClicks = AtomicInteger()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(260.dp)) {
                    StudyTopBar(
                        completed = 2,
                        target = 5,
                        fraction = 0.4f,
                        onClose = { closeClicks.incrementAndGet() },
                        onSettings = { settingsClicks.incrementAndGet() }
                    )
                }
            }
        }

        composeRule.onNodeWithText("2 / 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close study").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(StudyTopBarDescriptions.PROGRESS)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))

        composeRule.onNodeWithContentDescription("Close study").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.runOnIdle {
            assertEquals(1, closeClicks.get())
            assertEquals(1, settingsClicks.get())
        }
    }

    @Test
    fun progressPillDrawsTrackAndFill() {
        setTopBarWithProgress(fraction = 0.4f)

        val progressPixels = captureProgressPixels()

        assertEquals(StudyProgressFillColor, progressPixels.leftSample)
        assertEquals(StudyProgressTrackColor, progressPixels.rightSample)
    }

    @Test
    fun progressPillClampsOutOfRangeFractions() {
        setTopBarWithProgress(fraction = -0.5f)
        assertEquals(StudyProgressTrackColor, captureProgressPixels().leftSample)

        setTopBarWithProgress(fraction = 1.5f)
        assertEquals(StudyProgressFillColor, captureProgressPixels().rightSample)
    }

    private fun setTopBarWithProgress(fraction: Float) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(260.dp)) {
                    StudyTopBar(
                        completed = 2,
                        target = 5,
                        fraction = fraction,
                        onClose = {},
                        onSettings = {}
                    )
                }
            }
        }
    }

    private fun captureProgressPixels(): ProgressPillSamples {
        val bitmap = composeRule.onNodeWithContentDescription(StudyTopBarDescriptions.PROGRESS)
            .captureToImage()
        val pixels = bitmap.toPixelMap()
        val y = pixels.height / 2
        return ProgressPillSamples(
            leftSample = pixels[pixels.width / 5, y],
            rightSample = pixels[pixels.width * 4 / 5, y]
        )
    }

    private data class ProgressPillSamples(
        val leftSample: Color,
        val rightSample: Color
    )

    private companion object {
        val StudyProgressFillColor = Color(0xFFF82D72)
        val StudyProgressTrackColor = Color(0xFFFBDDEC)
    }
}
