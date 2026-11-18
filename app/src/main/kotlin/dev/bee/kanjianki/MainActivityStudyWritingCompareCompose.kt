@file:JvmName("MainActivityStudyWritingCompareCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.StudyWritingCopy
import dev.bee.kanjianki.core.study.WritingCompareModel

internal object WritingCompareTestTags {
    const val PANEL = "writing-compare-panel"
    const val YOURS = "writing-compare-yours"
    const val MODEL = "writing-compare-model"
    const val LEGEND_PREFIX = "writing-compare-legend-"
}

/** Holds the stroke-by-stroke comparison for the current writing result. */
internal class WritingCompareState {
    var model by mutableStateOf<WritingCompareModel?>(null)
        private set

    fun show(value: WritingCompareModel?) {
        model = value
    }

    fun hide() {
        model = null
    }
}

@Composable
internal fun WritingComparePanel(state: WritingCompareState, modifier: Modifier = Modifier) {
    val model = state.model ?: return
    WritingCompareDiagram(model, modifier)
}

/**
 * The theme has four clearly distinct hues, so verdicts share a colour by
 * kind: shape, order or direction, stroke count. The exact verdict of each
 * stroke is still spoken by the squares' content descriptions and listed in
 * the result text.
 */
private enum class VerdictGroup(val verdicts: List<WritingCompareModel.Verdict>) {
    SHAPE(listOf(WritingCompareModel.Verdict.ROUGH)),
    SEQUENCE(listOf(WritingCompareModel.Verdict.WRONG_ORDER, WritingCompareModel.Verdict.WRONG_DIRECTION)),
    COUNT(listOf(WritingCompareModel.Verdict.MISSING, WritingCompareModel.Verdict.EXTRA)),
}

private fun groupOf(verdict: WritingCompareModel.Verdict): VerdictGroup? =
    VerdictGroup.values().firstOrNull { verdict in it.verdicts }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WritingCompareDiagram(model: WritingCompareModel, modifier: Modifier = Modifier) {
    val problems = model.problems()
    val legend = VerdictGroup.values().mapNotNull { group ->
        val present = group.verdicts.filter { it in problems }
        if (present.isEmpty()) null else group to present
    }
    Column(modifier = modifier.fillMaxWidth().testTag(WritingCompareTestTags.PANEL)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompareSquare(
                title = StudyWritingCopy.compareYoursLabel(),
                strokes = model.yours,
                tag = WritingCompareTestTags.YOURS,
                modifier = Modifier.weight(1f),
            )
            CompareSquare(
                title = StudyWritingCopy.compareModelLabel(),
                strokes = model.model,
                tag = WritingCompareTestTags.MODEL,
                modifier = Modifier.weight(1f),
            )
        }
        if (legend.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                legend.forEach { (group, present) -> LegendEntry(group, present) }
            }
        }
    }
}

@Composable
private fun CompareSquare(
    title: String,
    strokes: List<WritingCompareModel.Stroke>,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = verdictColors()
    val description = StudyWritingCopy.compareSquareDescription(
        title,
        strokes.size,
        strokes.filter { it.verdict != WritingCompareModel.Verdict.OK }
            .map { StudyWritingCopy.compareStrokeDescription(it.number, verdictLabel(it.verdict)) },
    )
    Column(modifier = modifier) {
        Text(
            text = title,
            color = KaniTheme.colors.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag(tag)
                .clearAndSetSemantics { contentDescription = description },
            shape = RoundedCornerShape(10.dp),
            color = KaniTheme.colors.panelSoft,
            border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp)) {
                strokes.forEach { stroke -> drawCompareStroke(stroke, colors.getValue(stroke.verdict)) }
                // Badges last so a later stroke never hides an earlier number.
                strokes.forEach { stroke ->
                    drawStrokeBadge(stroke, colors.getValue(stroke.verdict), badgeTextColor(stroke.verdict), measurer)
                }
            }
        }
    }
}

private fun DrawScope.drawCompareStroke(stroke: WritingCompareModel.Stroke, color: Color) {
    val points = stroke.points
    val width = 3.dp.toPx()
    // A missing stroke was never drawn: show the model's stroke dashed.
    val dashed = if (stroke.verdict == WritingCompareModel.Verdict.MISSING) {
        PathEffect.dashPathEffect(floatArrayOf(width * 2f, width * 2f))
    } else {
        null
    }
    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = Offset(points[i].x * size.width, points[i].y * size.height),
            end = Offset(points[i + 1].x * size.width, points[i + 1].y * size.height),
            strokeWidth = width,
            cap = StrokeCap.Round,
            pathEffect = dashed,
        )
    }
}

private fun DrawScope.drawStrokeBadge(
    stroke: WritingCompareModel.Stroke,
    color: Color,
    textColor: Color,
    measurer: TextMeasurer,
) {
    val start = stroke.points.firstOrNull() ?: return
    val radius = 8.dp.toPx()
    val center = Offset(
        (start.x * size.width).coerceIn(radius, maxOf(radius, size.width - radius)),
        (start.y * size.height).coerceIn(radius, maxOf(radius, size.height - radius)),
    )
    drawCircle(color = color, radius = radius, center = center)
    val layout = measurer.measure(
        stroke.number.toString(),
        TextStyle(color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
    )
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
    )
}

@Composable
private fun LegendEntry(group: VerdictGroup, present: List<WritingCompareModel.Verdict>) {
    val label = if (present.size == 1) {
        verdictLabel(present[0])
    } else {
        when (group) {
            VerdictGroup.SHAPE -> StudyWritingCopy.compareRoughLabel()
            VerdictGroup.SEQUENCE -> StudyWritingCopy.compareOrderOrDirectionLabel()
            VerdictGroup.COUNT -> StudyWritingCopy.compareMissingOrExtraLabel()
        }
    }
    Row(
        modifier = Modifier.testTag(WritingCompareTestTags.LEGEND_PREFIX + group.name),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = verdictColors().getValue(present[0]),
        ) {}
        Text(
            text = label,
            color = KaniTheme.colors.ink,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun verdictColors(): Map<WritingCompareModel.Verdict, Color> {
    val colors = KaniTheme.colors
    return WritingCompareModel.Verdict.values().associateWith { verdict ->
        when (groupOf(verdict)) {
            null -> colors.teal
            VerdictGroup.SHAPE -> colors.gold
            VerdictGroup.SEQUENCE -> colors.blue
            VerdictGroup.COUNT -> colors.coral
        }
    }
}

/** Gold is light in every theme, so its numbers need dark ink. */
private fun badgeTextColor(verdict: WritingCompareModel.Verdict): Color =
    if (groupOf(verdict) == VerdictGroup.SHAPE) BADGE_INK else Color.White

private val BADGE_INK = Color(0xFF2D1635)

private fun verdictLabel(verdict: WritingCompareModel.Verdict): String = when (verdict) {
    WritingCompareModel.Verdict.OK -> StudyWritingCopy.compareOkLabel()
    WritingCompareModel.Verdict.ROUGH -> StudyWritingCopy.compareRoughLabel()
    WritingCompareModel.Verdict.WRONG_DIRECTION -> StudyWritingCopy.compareWrongDirectionLabel()
    WritingCompareModel.Verdict.WRONG_ORDER -> StudyWritingCopy.compareWrongOrderLabel()
    WritingCompareModel.Verdict.MISSING -> StudyWritingCopy.compareMissingLabel()
    WritingCompareModel.Verdict.EXTRA -> StudyWritingCopy.compareExtraLabel()
}
