package dev.bee.kanjianki.kani.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.kani.core.Stage
import dev.bee.kanjianki.kani.core.Stroke

/** Phone page: 24dp gutters, safe areas, scroll-free by design. */
@Composable
fun Page(
    modifier: Modifier = Modifier,
    top: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Kani.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 24.dp, end = 24.dp, top = top, bottom = 24.dp),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, color: Color = Kani.colors.accent) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = color),
    )
}

@Composable
fun Title(text: String, size: Int = 30) {
    Text(
        text,
        style = TextStyle(
            fontSize = size.sp, lineHeight = (size * 1.15).sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp, color = Kani.colors.ink,
        ),
    )
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Kani.colors.body, size: Int = 15) {
    Text(text, modifier = modifier, style = TextStyle(fontSize = size.sp, lineHeight = (size * 1.45).sp, color = color))
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Kani.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) c.buttonBg else c.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (enabled) c.buttonText else c.dim))
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Kani.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.ink))
    }
}

@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, color: Color = Kani.colors.correct, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color),
    )
}

@Composable
fun KCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = Kani.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.card)
            .border(BorderStroke(1.dp, c.line), RoundedCornerShape(22.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun StagePill(stage: Stage) {
    val c = Kani.colors
    val (bg, fg, label) = when (stage) {
        Stage.TRAINING -> Triple(c.trainingBg, c.trainingText, "Training")
        Stage.WATCHING -> Triple(c.watchingBg, c.watchingText, "Watching")
        Stage.SOLID -> Triple(c.solidBg, c.solidText, "Solid")
    }
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg),
    )
}

@Composable
fun KanjiText(text: String, size: Int, modifier: Modifier = Modifier, color: Color = Kani.colors.ink, weight: FontWeight = FontWeight.Medium) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(fontFamily = KanjiFont, fontSize = size.sp, lineHeight = (size * 1.2).sp, fontWeight = weight, color = color, textAlign = TextAlign.Center),
    )
}

@Composable
fun ProgressBar(index: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Kani.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total.coerceAtLeast(1)) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < index) c.ink else c.line),
            )
        }
    }
}

@Composable
fun TopRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** The カ seal from the Kani 1.0 logo work. */
@Composable
fun KaSeal(size: Dp, modifier: Modifier = Modifier) {
    val c = Kani.colors
    val seal = c.accent
    val ink = if (c.isDark) Color.Black else c.bg
    Canvas(modifier.size(size).semantics { contentDescription = "Kani" }) {
        val s = this.size.width / 80f
        drawRoundRect(seal, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * s, 16f * s))
        fun p(x: Float, y: Float) = Offset((x - 20f) * s, (y - 20f) * s)
        val stroke = DrawStroke(width = 10.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val a = Path().apply {
            moveTo(p(37f, 50f).x, p(37f, 50f).y)
            lineTo(p(78f, 50f).x, p(78f, 50f).y)
            quadraticTo(p(80.5f, 50f).x, p(80.5f, 50f).y, p(80f, 53f).x, p(80f, 53f).y)
            cubicTo(p(77.5f, 70f).x, p(77.5f, 70f).y, p(73f, 80f).x, p(73f, 80f).y, p(63f, 89f).x, p(63f, 89f).y)
        }
        val b = Path().apply {
            moveTo(p(59f, 31f).x, p(59f, 31f).y)
            cubicTo(p(58.5f, 57f).x, p(58.5f, 57f).y, p(52f, 76f).x, p(52f, 76f).y, p(37f, 90f).x, p(37f, 90f).y)
        }
        drawPath(a, ink, style = stroke)
        drawPath(b, ink, style = stroke)
    }
}

/** "kani" with a small square seal for the i-dot, set just above the x-height. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 32) {
    val c = Kani.colors
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        "kan\u0131",
        onTextLayout = { layout = it },
        modifier = modifier
            .semantics { contentDescription = "kani" }
            .drawBehind {
                val l = layout ?: return@drawBehind
                val i = l.getBoundingBox(3)
                val em = size.sp.toPx()
                val dot = em * 0.19f
                // The x-height is about 0.51 em; the seal sits just above it.
                val bottom = l.getLineBaseline(0) - em * 0.51f - em * 0.045f
                drawRoundRect(
                    c.accent,
                    topLeft = Offset(i.center.x - dot / 2f, bottom - dot),
                    size = androidx.compose.ui.geometry.Size(dot, dot),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(dot * 0.2f),
                )
            },
        style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-size * 0.035).sp, color = c.ink),
    )
}

/**
 * A kanji drawn from its KanjiVG strokes, with [highlight] strokes in the accent colour.
 * Used to show the part that decides between two look-alikes.
 */
@Composable
fun StrokeGlyph(
    strokes: List<Stroke>,
    size: Dp,
    modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(),
    base: Color = Kani.colors.ink,
    accent: Color = Kani.colors.accent,
    hidden: Set<Int> = emptySet(),
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        strokes.forEachIndexed { i, stroke ->
            if (i in hidden) return@forEachIndexed
            drawStroke(stroke, w, if (i in highlight) accent else base, if (i in highlight) 0.07f else 0.055f)
        }
    }
}

fun DrawScope.drawStroke(stroke: Stroke, w: Float, color: Color, widthFraction: Float) {
    if (stroke.size < 2) return
    val path = Path().apply {
        moveTo(stroke[0].x * w, stroke[0].y * w)
        for (i in 1 until stroke.size) lineTo(stroke[i].x * w, stroke[i].y * w)
    }
    drawPath(path, color, style = DrawStroke(width = w * widthFraction, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun Dot(on: Boolean) {
    val c = Kani.colors
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .then(if (on) Modifier.background(c.ink) else Modifier.border(1.5.dp, c.dim, CircleShape)),
    )
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
