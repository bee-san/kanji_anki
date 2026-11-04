package dev.bee.kanjianki.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.CapturedInk
import dev.bee.kanjianki.presentation.InkPoint
import dev.bee.kanjianki.presentation.InkStroke
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val INK_CANVAS_TEST_TAG: String = "kani-ink-canvas"
const val INK_UNDO_TEST_TAG: String = "kani-ink-undo"
const val INK_CLEAR_TEST_TAG: String = "kani-ink-clear"

/**
 * The shared handwriting canvas: capture, guide overlay, undo, and clear.
 *
 * One surface for both hosts (ADR 0005). Android hands the captured [CapturedInk] to
 * ML Kit for recognition through its own adapter; desktop, which declares no
 * recognizer, uses it for guide tracing and unscheduled practice only — the canvas
 * itself is identical, and what happens to the ink afterward is the host's.
 *
 * Points are normalized to 0..1 over the drawn size and reported through [onChange] as
 * each stroke completes, so the parent owns the captured ink (making undo, clear, and
 * restore its state, not the canvas's). Mouse and stylus both drive
 * [detectDragGestures]; there is no separate stylus path because a normalized drag is
 * the same gesture whichever device produced it.
 *
 * [guide] is an optional faint stroke-order trace drawn under the ink — the writing
 * rung's "show me the shape" affordance, and on desktop the whole point of the canvas.
 */
@Composable
fun InkCanvas(
    ink: CapturedInk,
    onChange: (CapturedInk) -> Unit,
    copy: StudyCopy,
    modifier: Modifier = Modifier,
    guide: List<InkStroke> = emptyList(),
) {
    // The live, in-progress stroke as normalized points; committed to `ink` on lift.
    var current by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    val guideColor = KaniTheme.colors.muted
    val inkColor = KaniTheme.colors.ink
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = KaniUiTokens.StudyShapeLarge,
            color = KaniTheme.colors.surface,
            border = BorderStroke(1.dp, KaniTheme.colors.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(INK_CANVAS_TEST_TAG)
                    .semantics { contentDescription = copy.reveal }
                    .onSizeChanged { canvasSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                current = listOf(normalized(start, canvasSize, 0L))
                            },
                            onDrag = { change, _ ->
                                current = current + normalized(
                                    change.position,
                                    canvasSize,
                                    current.size.toLong(),
                                )
                            },
                            onDragEnd = {
                                onChange(ink.withStroke(InkStroke(current)))
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val w = size.width
                    val h = size.height
                    for (stroke in guide) {
                        drawStroke(stroke.points, guideColor, GUIDE_WIDTH, w, h)
                    }
                    for (stroke in ink.strokes) {
                        drawStroke(stroke.points, inkColor, INK_WIDTH, w, h)
                    }
                    drawStroke(current, inkColor, INK_WIDTH, w, h)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onChange(ink.withoutLastStroke()) },
                modifier = Modifier
                    .heightIn(min = SECONDARY_MIN_HEIGHT)
                    .testTag(INK_UNDO_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.undo)
            }
            TextButton(
                onClick = { onChange(CapturedInk.EMPTY) },
                modifier = Modifier
                    .heightIn(min = SECONDARY_MIN_HEIGHT)
                    .testTag(INK_CLEAR_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.clear)
            }
        }
    }
}

private fun normalized(position: Offset, size: Offset, order: Long): InkPoint {
    // A zero-size canvas (before the first layout) maps everything to the origin
    // rather than dividing by zero; the first real drag arrives after sizing.
    val x = if (size.x <= 0f) 0f else (position.x / size.x).coerceIn(0f, 1f)
    val y = if (size.y <= 0f) 0f else (position.y / size.y).coerceIn(0f, 1f)
    return InkPoint(x = x, y = y, timestampMillis = order)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<InkPoint>,
    color: androidx.compose.ui.graphics.Color,
    width: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = Offset(points[i].x * w, points[i].y * h),
            end = Offset(points[i + 1].x * w, points[i + 1].y * h),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private const val INK_WIDTH = 6f
private const val GUIDE_WIDTH = 2f
