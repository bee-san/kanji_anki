package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.StudyTextCopy
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
                        routeSnapshot = routeSnapshot(completed = 2, target = 5),
                        onClose = { closeClicks.incrementAndGet() },
                        onSettings = { settingsClicks.incrementAndGet() }
                    )
                }
            }
        }

        composeRule.onNodeWithText("2 / 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(StudyTextCopy.closeStudyLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(StudyTopBarDescriptions.PROGRESS)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))

        composeRule.onNodeWithContentDescription(StudyTextCopy.closeStudyLabel()).performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.runOnIdle {
            assertEquals(1, closeClicks.get())
            assertEquals(1, settingsClicks.get())
        }
    }

    @Test
    fun progressPillDrawsTrackAndFill() {
        setTopBarWithProgress(completed = 2, target = 5)

        val progressPixels = captureProgressPixels()

        assertEquals(StudyProgressFillColor, progressPixels.leftSample)
        assertEquals(StudyProgressTrackColor, progressPixels.rightSample)
    }

    @Test
    fun progressPillUsesAcceptedSnapshotBounds() {
        val routeSnapshot = mutableStateOf(routeSnapshot(completed = 0, target = 5))
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(260.dp)) {
                    StudyTopBar(
                        routeSnapshot = routeSnapshot.value,
                        onClose = {},
                        onSettings = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription(StudyTopBarDescriptions.PROGRESS)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        assertEquals(StudyProgressTrackColor, captureProgressPixels().leftSample)

        composeRule.runOnIdle {
            routeSnapshot.value = routeSnapshot(completed = 5, target = 5, version = 2L)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(StudyTopBarDescriptions.PROGRESS)
            .assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        assertEquals(StudyProgressFillColor, captureProgressPixels().rightSample)
    }

    private fun setTopBarWithProgress(completed: Int, target: Int) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(260.dp)) {
                    StudyTopBar(
                        routeSnapshot = routeSnapshot(completed, target),
                        onClose = {},
                        onSettings = {}
                    )
                }
            }
        }
    }

    private fun routeSnapshot(
        completed: Int,
        target: Int,
        version: Long = 1L,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        version = StudyRouteVersion(version),
        sessionGeneration = StudySessionGeneration(1L),
        phase = StudySessionPhase.ACTIVE,
        progress = StudySessionProgressUiState(
            completedCount = completed,
            targetCount = target,
        ),
    )

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
        val StudyProgressFillColor = LightKaniColors.primary
        val StudyProgressTrackColor = LightKaniColors.track
    }
}
