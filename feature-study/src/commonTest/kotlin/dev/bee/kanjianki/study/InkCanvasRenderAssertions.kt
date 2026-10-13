package dev.bee.kanjianki.study

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.CapturedInk
import dev.bee.kanjianki.presentation.InkPoint
import dev.bee.kanjianki.presentation.InkStroke
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared ink canvas's render assertions, run on both hosts.
 *
 * Capture, undo, clear, and the guide overlay — the pieces ADR 0005 makes the desktop
 * canvas responsible for. What happens to the captured ink afterward (recognition on
 * Android, practice-only on desktop) is the host's and is not asserted here.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertDraggingCapturesANormalizedStroke() {
    var ink by mutableStateOf(CapturedInk.EMPTY)
    renderStudy(
        content = {
            InkCanvas(
                ink = ink,
                onChange = { ink = it },
                copy = studyCopy(),
                modifier = Modifier.width(300.dp),
            )
        },
    ) {
        onNodeWithTag(INK_CANVAS_TEST_TAG).performTouchInput {
            swipe(start = Offset(centerX, top + 4f), end = Offset(centerX, bottom - 4f))
        }
        // A drag commits one stroke of normalized points, all inside 0..1.
        assertEquals(1, ink.strokes.size)
        val points = ink.strokes.single().points
        assertTrue(points.isNotEmpty(), "the swipe captured points")
        assertTrue(
            points.all { it.x in 0f..1f && it.y in 0f..1f },
            "captured points are normalized: $points",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertUndoDropsTheLastStrokeAndClearWipesTheCanvas() {
    var ink by mutableStateOf(
        CapturedInk(strokes = listOf(stroke(0.2f), stroke(0.8f))),
    )
    renderStudy(
        content = { InkCanvas(ink = ink, onChange = { ink = it }, copy = studyCopy()) },
    ) {
        onNodeWithTag(INK_UNDO_TEST_TAG).performClick()
        assertEquals(1, ink.strokes.size)
        onNodeWithTag(INK_CLEAR_TEST_TAG).performClick()
        assertTrue(ink.isEmpty, "clear wipes every stroke")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheCanvasRendersACommittedStrokeAndAGuideWithoutError() {
    // Exercises the draw path for both the guide underlay and committed ink — a
    // canvas that threw while drawing either would fail to compose at all.
    renderStudy(
        content = {
            InkCanvas(
                ink = CapturedInk(strokes = listOf(stroke(0.5f))),
                onChange = {},
                copy = studyCopy(),
                guide = listOf(stroke(0.3f)),
            )
        },
    ) {
        onNodeWithTag(INK_CANVAS_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheInkControlsStayUsableTargetsAtEveryFontScale() {
    // The pad's own Undo and Clear, which the session-level target matrix cannot reach:
    // `InkCanvas` is composed directly by the writing surface's caller, so it is only
    // measurable here. Both took Material's 40dp `TextButton` default until the session
    // matrix found the same defect on the session's Undo.
    for (fontScale in INK_FONT_SCALES) {
        renderStudyAt(
            window = StudyWindow.DESKTOP_SMALL,
            fontScale = fontScale,
            content = {
                InkCanvas(
                    ink = CapturedInk(strokes = listOf(stroke(0.5f))),
                    onChange = {},
                    copy = studyCopy(),
                )
            },
        ) {
            for (tag in listOf(INK_UNDO_TEST_TAG, INK_CLEAR_TEST_TAG)) {
                val bounds = onNodeWithTag(tag).performScrollTo().getBoundsInRoot()
                val height = bounds.bottom - bounds.top
                assertTrue(
                    height.value + 1f >= INK_MIN_TARGET,
                    "$tag is $height tall at ${fontScale}x, under the ${INK_MIN_TARGET}dp target",
                )
            }
        }
    }
}

/** Matches the session matrix's scales, so the two agree on what "large text" means. */
private val INK_FONT_SCALES: List<Float> = STUDY_FONT_SCALES

/** The same floor `SECONDARY_MIN_HEIGHT` sets in production, restated as the requirement. */
private const val INK_MIN_TARGET: Float = 44f

@OptIn(ExperimentalTestApi::class)
internal fun assertTheInkTestTagsAreDistinct() {
    val tags = listOf(INK_CANVAS_TEST_TAG, INK_UNDO_TEST_TAG, INK_CLEAR_TEST_TAG)
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
}

private fun stroke(x: Float) = InkStroke(
    points = listOf(InkPoint(x, 0f, 0L), InkPoint(x, 1f, 1L)),
)
