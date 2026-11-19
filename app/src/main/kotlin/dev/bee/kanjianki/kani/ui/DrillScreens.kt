package dev.bee.kanjianki.kani.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.kani.core.Pt
import dev.bee.kanjianki.kani.core.SprintTrial
import dev.bee.kanjianki.kani.core.Stroke
import dev.bee.kanjianki.kani.core.StrokeDiff
import kotlinx.coroutines.delay

private enum class Phase { INTRO, FLASH, MASK, PICK, FEEDBACK, DONE }

/** Face buttons pick tiles in reading order, for handhelds and controllers. */
private val FACE_KEYS = listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
private val FACE_LABELS = listOf("X", "Y", "A", "B")

@Composable
fun SprintScreen(
    trials: List<SprintTrial>,
    flashMs: Long = 700,
    onAnswer: (SprintTrial, String) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Kani.colors
    var phase by remember(trials) { mutableStateOf(Phase.INTRO) }
    var round by remember(trials) { mutableIntStateOf(0) }
    var right by remember(trials) { mutableIntStateOf(0) }
    var picked by remember(trials) { mutableStateOf<String?>(null) }
    var runKey by remember { mutableIntStateOf(0) }
    val trial = trials.getOrNull(round)
    val hasPad = remember { InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.supportsSource(InputDevice.SOURCE_GAMEPAD) == true } }
    val focus = remember { FocusRequester() }

    LaunchedEffect(phase, round, runKey) {
        when (phase) {
            Phase.FLASH -> { delay(flashMs); phase = Phase.MASK }
            Phase.MASK -> { delay(250); phase = Phase.PICK }
            Phase.FEEDBACK -> {
                delay(if (picked == trial?.shown) 450 else 1100)
                picked = null
                if (round + 1 >= trials.size) phase = Phase.DONE else { round++; phase = Phase.FLASH }
            }
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun pick(option: String) {
        val t = trial ?: return
        if (phase != Phase.PICK) return
        picked = option
        if (option == t.shown) right++
        onAnswer(t, option)
        phase = Phase.FEEDBACK
    }

    Page(
        Modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                when {
                    phase == Phase.INTRO && (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER) -> { phase = Phase.FLASH; true }
                    phase == Phase.PICK && code in FACE_KEYS -> {
                        trial?.options?.getOrNull(FACE_KEYS.indexOf(code))?.let(::pick); true
                    }
                    else -> false
                }
            },
    ) {
        TopRow {
            TextLink("Close", color = c.secondary, onClick = onClose)
            Spacer(Modifier.weight(1f))
            if (phase != Phase.INTRO && phase != Phase.DONE) {
                Text("${round + 1} / ${trials.size}", style = TextStyle(fontSize = 14.sp, color = c.dim, fontWeight = FontWeight.Medium))
            }
        }
        when (phase) {
            Phase.INTRO -> {
                Gap(24)
                Eyebrow("Look-alike sprint")
                Gap(8)
                Title("Spot the kanji you saw")
                Gap(12)
                Body("Each kanji flashes for a moment. Then pick it from its look-alikes.", color = c.secondary)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Start · ${trials.size} rounds") { phase = Phase.FLASH }
                if (hasPad) {
                    Gap(8)
                    Body("or press A", color = c.dim, size = 14, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Phase.DONE -> {
                Gap(24)
                Eyebrow("Sprint done")
                Gap(8)
                Title("$right of ${trials.size} right", size = 34)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Continue", onClick = onDone)
                Gap(8)
                SecondaryButton("Run again") {
                    round = 0; right = 0; picked = null; runKey++; phase = Phase.FLASH
                }
            }
            else -> {
                val t = trial ?: return@Page
                Gap(12)
                Text(
                    if (phase == Phase.FLASH || phase == Phase.MASK) "Remember this one…" else "Which one did you see?",
                    style = TextStyle(fontSize = 16.sp, color = c.secondary),
                )
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when (phase) {
                        Phase.FLASH -> KanjiText(t.shown, 150)
                        Phase.MASK -> Hatch()
                        else -> Unit
                    }
                }
                val cols = 2
                t.options.chunked(cols).forEachIndexed { rowIndex, rowOptions ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowOptions.forEachIndexed { colIndex, option ->
                            val i = rowIndex * cols + colIndex
                            val state = when {
                                phase != Phase.FEEDBACK -> 0
                                option == t.shown -> 1
                                option == picked -> 2
                                else -> 0
                            }
                            // Choices stay hidden while the kanji flashes, so the pick tests memory.
                            val showing = phase == Phase.PICK || phase == Phase.FEEDBACK
                            Tile(
                                option,
                                state,
                                label = if (hasPad) FACE_LABELS.getOrNull(i) else null,
                                enabled = phase == Phase.PICK,
                                modifier = Modifier.weight(1f).alpha(if (showing) 1f else 0f),
                            ) { pick(option) }
                        }
                        if (rowOptions.size < cols) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(kanji: String, state: Int, label: String?, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Kani.colors
    val bg = when (state) {
        1 -> c.solidBg
        2 -> c.wrongBg
        else -> c.card
    }
    val border = when (state) {
        1 -> c.correct
        2 -> c.accent
        else -> c.line
    }
    Box(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(if (state == 0) 1.dp else 2.dp, border, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KanjiText(kanji, 56)
        if (label != null) {
            Text(label, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), style = TextStyle(fontSize = 12.sp, color = c.dim, fontWeight = FontWeight.Bold))
        }
    }
}

/** A hatch pattern that wipes the afterimage so the pick tests memory, not the retina. */
@Composable
private fun Hatch() {
    val c = Kani.colors
    Canvas(Modifier.size(170.dp)) {
        val w = size.width
        var x = -w
        while (x < w) {
            drawLine(c.dim, Offset(x, w), Offset(x + w, 0f), strokeWidth = 3f)
            drawLine(c.dim, Offset(x, 0f), Offset(x + w, w), strokeWidth = 3f)
            x += w / 9f
        }
    }
}

/**
 * Write only the part that decides. The shared strokes stay as a faint guide; the user draws the
 * deciding strokes from memory, then Kani checks count and position against KanjiVG.
 */
@Composable
fun WriteScreen(
    target: String,
    other: String,
    targetStrokes: List<Stroke>,
    otherStrokes: List<Stroke>,
    onDone: () -> Unit,
) {
    val c = Kani.colors
    val deciding = remember(target, other) { StrokeDiff.decidingStrokes(targetStrokes, otherStrokes) }
    val expected = remember(deciding) { deciding.sorted().map { targetStrokes[it] } }
    val drawn = remember(target) { mutableStateListOf<List<Pt>>() }
    var current by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var showGuide by remember { mutableStateOf(false) }
    var verdict by remember(target) { mutableStateOf<Boolean?>(null) }

    Page {
        Eyebrow("Still mixing up $target and $other")
        Gap(8)
        Title("Write the part that decides")
        Gap(8)
        Body(
            when (verdict) {
                true -> "That's it. This is what makes $target, not $other."
                false -> "Not quite. Tap Show guide, then try again."
                null -> "Draw the missing strokes, then tap Check."
            },
            color = when (verdict) {
                true -> c.correct
                false -> c.accent
                null -> c.secondary
            },
        )
        Gap(16)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(c.card)
                .border(1.dp, c.line, RoundedCornerShape(22.dp))
                .pointerInput(target) {
                    detectDragGestures(
                        onDragStart = { o -> current = listOf(Pt(o.x / size.width, o.y / size.height)) },
                        onDrag = { change, _ ->
                            val p = change.position
                            current = current + Pt(p.x / size.width, p.y / size.height)
                        },
                        onDragEnd = {
                            if (current.size >= 2) drawn += current
                            current = emptyList()
                            verdict = null
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                // Shared strokes: a faint frame of what both kanji have.
                targetStrokes.forEachIndexed { i, s -> if (i !in deciding) drawStroke(s, w, c.line, 0.05f) }
                // Dashed box around where the deciding part goes.
                val pts = expected.flatten()
                if (pts.isNotEmpty()) {
                    val pad = 0.05f
                    val l = (pts.minOf { it.x } - pad) * w
                    val t = (pts.minOf { it.y } - pad) * w
                    val r = (pts.maxOf { it.x } + pad) * w
                    val b = (pts.maxOf { it.y } + pad) * w
                    drawRoundRect(
                        c.accent.copy(alpha = 0.6f),
                        topLeft = Offset(l, t),
                        size = androidx.compose.ui.geometry.Size(r - l, b - t),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f),
                        style = DrawStroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                }
                if (showGuide) expected.forEach { drawStroke(it, w, c.accent.copy(alpha = 0.25f), 0.06f) }
                drawn.forEach { drawStroke(it, w, c.ink, 0.045f) }
                if (current.size >= 2) drawStroke(current, w, c.ink, 0.045f)
            }
        }
        Spacer(Modifier.weight(1f))
        if (verdict == true) {
            PrimaryButton("Continue", onClick = onDone)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Undo", Modifier.weight(1f)) { if (drawn.isNotEmpty()) drawn.removeAt(drawn.lastIndex); verdict = null }
                SecondaryButton(if (showGuide) "Hide guide" else "Show guide", Modifier.weight(1f)) { showGuide = !showGuide }
            }
            Gap(10)
            PrimaryButton("Check", enabled = drawn.isNotEmpty()) {
                verdict = StrokeDiff.matchesDeciding(drawn.toList(), expected)
                if (verdict == false) drawn.clear()
            }
            TextLink("Skip", color = c.secondary, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onDone)
        }
    }
}
