package dev.bee.kanjianki.kani.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.kani.Reveal
import dev.bee.kanjianki.kani.SessionSummary
import dev.bee.kanjianki.kani.core.Check
import dev.bee.kanjianki.kani.core.Progress
import dev.bee.kanjianki.kani.core.Romaji
import dev.bee.kanjianki.kani.core.SessionItem
import dev.bee.kanjianki.kani.core.Status
import dev.bee.kanjianki.kani.core.Stroke
import dev.bee.kanjianki.kani.core.StrokeDiff
import dev.bee.kanjianki.kani.data.KaniStore

/** Answer box. For readings, romaji turns into hiragana as you type. */
@Composable
fun AnswerField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String,
    kana: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Kani.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = { v ->
            if (kana) {
                val converted = Romaji.toKana(v.text)
                onChange(TextFieldValue(converted, TextRange(converted.length)))
            } else {
                onChange(v)
            }
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 20.sp, color = c.ink, fontFamily = if (kana) KanjiFont else null).japanese(),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = modifier.focusRequester(focus),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty()) Text(placeholder, style = TextStyle(fontSize = 18.sp, color = c.dim))
                inner()
            }
        },
    )
}

@Composable
fun CheckScreen(
    item: SessionItem,
    index: Int,
    total: Int,
    word: KaniStore.Word?,
    reveal: Reveal?,
    speakOnReveal: Boolean,
    onSpeak: (String) -> Unit,
    onFirstKey: () -> Unit,
    onSubmit: (text: String, gaveUp: Boolean) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Kani.colors
    var text by remember(item) { mutableStateOf(TextFieldValue("")) }
    val reading = item.check == Check.READING && word != null

    val focusManager = LocalFocusManager.current
    LaunchedEffect(reveal) {
        if (reveal != null) focusManager.clearFocus(force = true)
        val spoken = reveal?.word?.reading
        if (reveal != null && speakOnReveal && !spoken.isNullOrEmpty()) onSpeak(spoken)
    }

    Page(scroll = false) {
        TopRow {
            TextLink("Close", color = c.secondary, onClick = onClose)
            Spacer(Modifier.weight(1f))
            Text("${index + 1} / $total", style = TextStyle(fontSize = 14.sp, color = c.dim, fontWeight = FontWeight.Medium))
        }
        Gap(4)
        ProgressBar(index, total)
        Gap(28)
        Eyebrow(if (reading) "How do you read it?" else "What does it mean?", color = c.dim)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (reading) KanjiText(word!!.expression, if (word.expression.length <= 2) 96 else 64)
            else KanjiText(item.kanji, 168)
        }
        Side {
            if (reveal == null) {
                AnswerField(
                    value = text,
                    onChange = {
                        if (it.text.isNotEmpty()) onFirstKey()
                        text = it
                    },
                    placeholder = if (reading) "Reading · type romaji or kana" else "Meaning",
                    kana = reading,
                    onSubmit = { if (text.text.isNotBlank()) onSubmit(text.text, false) },
                )
                Gap(12)
                PrimaryButton("Check", enabled = text.text.isNotBlank()) { onSubmit(text.text, false) }
                TextLink("I don't know", color = c.secondary, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    onSubmit("", true)
                }
            } else {
                RevealCard(item, reveal, onSpeak)
                Gap(16)
                PrimaryButton("Next", onClick = onNext)
            }
        }
    }
}

@Composable
private fun RevealCard(item: SessionItem, reveal: Reveal, onSpeak: (String) -> Unit) {
    val c = Kani.colors
    KCard {
        val label = if (reveal.correct) "Right · " + reveal.answer.substringBefore(',') else "Answer"
        Eyebrow(label, color = if (reveal.correct) c.correct else c.accent)
        if (!reveal.correct) {
            Gap(6)
            Text(reveal.answer, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink))
        }
        reveal.word?.let { w ->
            Gap(12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    KanjiText(w.expression, 22, modifier = Modifier.align(Alignment.Start))
                    Text(w.reading, style = TextStyle(fontSize = 15.sp, color = c.secondary))
                    if (w.meaning.isNotEmpty()) Text(w.meaning.take(60), style = TextStyle(fontSize = 15.sp, color = c.body))
                }
                SpeakerButton { onSpeak(w.reading) }
            }
        }
        if (!reveal.hook.isNullOrBlank() && item.check == Check.MEANING) {
            Gap(12)
            Text("Your hook: ${reveal.hook}", style = TextStyle(fontSize = 15.sp, color = c.body))
        }
    }
}

@Composable
fun SpeakerButton(onClick: () -> Unit) {
    val c = Kani.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(c.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Play the word" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.1f, w * 0.38f); lineTo(w * 0.3f, w * 0.38f); lineTo(w * 0.55f, w * 0.15f)
                lineTo(w * 0.55f, w * 0.85f); lineTo(w * 0.3f, w * 0.62f); lineTo(w * 0.1f, w * 0.62f); close()
            }
            drawPath(path, c.ink)
            drawArc(c.ink, -45f, 90f, false, Offset(w * 0.5f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.4f), style = DrawStroke(w * 0.08f))
        }
    }
}

/** Two look-alikes side by side with the strokes that tell them apart in colour. */
@Composable
fun PairCards(
    left: String,
    leftLabel: String,
    leftMeaning: String,
    right: String,
    rightLabel: String,
    rightMeaning: String,
    strokes: (String) -> List<Stroke>?,
    glyph: Dp = 96.dp,
) {
    val c = Kani.colors
    val ls = remember(left) { strokes(left) }
    val rs = remember(right) { strokes(right) }
    // The stroke diff searches ~1,900 alignments, so work it out once per pair, not per redraw.
    val leftDeciding = remember(ls, rs) { if (ls != null && rs != null) StrokeDiff.decidingStrokes(ls, rs) else emptySet() }
    val rightDeciding = remember(ls, rs) { if (ls != null && rs != null) StrokeDiff.decidingStrokes(rs, ls) else emptySet() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(left, leftLabel, leftMeaning) to (ls to leftDeciding), Triple(right, rightLabel, rightMeaning) to (rs to rightDeciding))
            .forEachIndexed { i, (info, pair) ->
                val (k, label, meaning) = info
                KCard(Modifier.weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    Eyebrow(label, color = if (i == 0) c.correct else c.accent)
                    Gap(10)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val (mine, deciding) = pair
                        if (mine != null && deciding.isNotEmpty()) {
                            StrokeGlyph(mine, glyph, highlight = deciding)
                        } else {
                            KanjiText(k, (glyph.value * 0.8f).toInt())
                        }
                    }
                    Gap(10)
                    Text(meaning, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink), maxLines = 2)
                }
            }
    }
}

@Composable
fun ContrastScreen(
    target: String,
    picked: String,
    meaning: (String) -> String,
    strokes: (String) -> List<Stroke>?,
    onTrain: (sure: Boolean?) -> Unit,
    onSkip: () -> Unit,
) {
    val c = Kani.colors
    var sure by remember(target, picked) { mutableStateOf<Boolean?>(null) }
    Page {
        Eyebrow("Mix-up")
        Gap(8)
        Row(verticalAlignment = Alignment.Bottom) {
            Title("You read ")
            KanjiText(target, 30, weight = FontWeight.Bold)
            Title(" as ")
            KanjiText(picked, 30, weight = FontWeight.Bold)
        }
        Gap(20)
        PairCards(target, "This card", meaning(target), picked, "You thought", meaning(picked), strokes)
        Gap(14)
        Body("The strokes in colour are the difference.", color = c.secondary)
        Gap(24)
        Text("Were you sure?", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink))
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("Sure", sure == true, Modifier.weight(1f)) { sure = true }
            Choice("Guessed", sure == false, Modifier.weight(1f)) { sure = false }
        }
        Spacer(Modifier.weight(1f))
        Side {
            PrimaryButton("Train this pair · 1 min") { onTrain(sure) }
            TextLink("Skip", color = c.secondary, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onSkip)
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Kani.colors
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) c.ink else c.card)
            .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (on) c.buttonText else c.ink))
    }
}

data class FamilyMember(val kanji: String, val note: String)

@Composable
fun ReadingFamilyScreen(
    kanji: String,
    word: KaniStore.Word,
    sound: String?,
    family: List<FamilyMember>,
    watchFor: List<Pair<String, String>>,
    check: (String) -> Boolean,
    onDone: () -> Unit,
) {
    val c = Kani.colors
    var text by remember(word) { mutableStateOf(TextFieldValue("")) }
    var result by remember(word) { mutableStateOf<Boolean?>(null) }
    Page(scroll = false) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Eyebrow("Wrong reading")
            Gap(8)
            if (sound != null && family.size > 1) {
                Row(verticalAlignment = Alignment.Bottom) {
                    KanjiText(kanji, 30, weight = FontWeight.Bold)
                    Title(" sounds like its family: $sound")
                }
                Gap(18)
                KCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        family.take(4).forEach { m ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (m.kanji == kanji) c.wrongBg else c.chip)
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                KanjiText(m.kanji, 30)
                                Text(m.note, style = TextStyle(fontSize = 12.sp, color = c.secondary))
                            }
                        }
                    }
                    if (watchFor.isNotEmpty()) {
                        Gap(12)
                        Body("Watch for: " + watchFor.joinToString(", ") { (k, r) -> "$k reads $r" } + ".", color = c.secondary, size = 14)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    KanjiText(word.expression, 30, weight = FontWeight.Bold)
                    Title(" reads ${word.reading}")
                }
            }
            Gap(24)
            Text("Read it again", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink))
            Gap(8)
            KanjiText(word.expression, 44, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Side {
            when (result) {
                null -> {
                    AnswerField(text, { text = it }, "Reading · type romaji or kana", kana = true, onSubmit = {
                        if (text.text.isNotBlank()) result = check(text.text)
                    })
                    Gap(12)
                    PrimaryButton("Check", enabled = text.text.isNotBlank()) { result = check(text.text) }
                }
                else -> {
                    Body(if (result == true) "Right: ${word.reading}" else "It's ${word.reading}", color = if (result == true) c.correct else c.accent, size = 17)
                    Gap(12)
                    PrimaryButton("Next", onClick = onDone)
                }
            }
        }
    }
}

@Composable
fun HookScreen(kanji: String, meaning: String, words: List<KaniStore.Word>, onSave: (String) -> Unit, onSkip: () -> Unit) {
    val c = Kani.colors
    var text by remember(kanji) { mutableStateOf(TextFieldValue("")) }
    Page {
        Eyebrow("Missed the meaning twice")
        Gap(8)
        Row(verticalAlignment = Alignment.Bottom) {
            Title("Build your own hook for ")
            KanjiText(kanji, 30, weight = FontWeight.Bold)
        }
        Gap(20)
        KCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KanjiText(kanji, 64)
                HGap(16)
                Column {
                    Text(meaning, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink))
                    words.take(2).forEach { Text("${it.expression} · ${it.reading}", style = TextStyle(fontSize = 14.sp, color = c.secondary)) }
                }
            }
        }
        Gap(20)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(fontSize = 17.sp, color = c.ink, lineHeight = 24.sp).japanese(),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.card)
                .border(1.dp, c.line, RoundedCornerShape(16.dp))
                .padding(16.dp),
            decorationBox = { inner ->
                if (text.text.isEmpty()) Text("A picture or story that links the shape to \"$meaning\"", style = TextStyle(fontSize = 17.sp, color = c.dim))
                inner()
            },
        )
        Gap(10)
        Body("It stays hidden until after your next try.", color = c.secondary, size = 14)
        Spacer(Modifier.weight(1f))
        Side {
            PrimaryButton("Save hook", enabled = text.text.isNotBlank()) { onSave(text.text) }
            TextLink("Skip for now", color = c.secondary, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onSkip)
        }
    }
}

@Composable
fun EarlyScreen(
    newKanji: String,
    known: String,
    meaning: (String) -> String,
    strokes: (String) -> List<Stroke>?,
    onQuickCheck: () -> Unit,
    onGotIt: () -> Unit,
) {
    val c = Kani.colors
    Page {
        Eyebrow("New in your Anki")
        Gap(8)
        Row(verticalAlignment = Alignment.Bottom) {
            KanjiText(newKanji, 30, weight = FontWeight.Bold)
            Title(" looks like ")
            KanjiText(known, 30, weight = FontWeight.Bold)
        }
        Gap(20)
        PairCards(newKanji, "New", meaning(newKanji), known, "You know", meaning(known), strokes)
        Gap(14)
        Body("Shown once, before you meet $newKanji in Anki.", color = c.secondary)
        Spacer(Modifier.weight(1f))
        Side {
            PrimaryButton("Quick check · 3 flashes", onClick = onQuickCheck)
            TextLink("Got it", color = c.secondary, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onGotIt)
        }
    }
}

@Composable
fun DoneScreen(summary: SessionSummary, meaning: (String) -> String, onDone: () -> Unit) {
    val c = Kani.colors
    Page(top = 40.dp, scroll = false) {
        val m = summary.seconds / 60
        val s = summary.seconds % 60
        Eyebrow("Session done · ${m} min ${s} s")
        Gap(8)
        Title(
            when (summary.solidNow) {
                0 -> if (summary.rows.size == 1) "1 kanji practised" else "${summary.rows.size} kanji practised"
                1 -> "1 kanji now solid"
                else -> "${summary.solidNow} kanji now solid"
            },
            size = 34,
        )
        Gap(16)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            summary.rows.forEach { row ->
                val days = if (row.status == Status.SOLID) Progress.DAYS_TO_SOLID else row.goodDays.size
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    KanjiText(row.kanji, 26, modifier = Modifier.padding(end = 14.dp))
                    Text(meaning(row.kanji), modifier = Modifier.weight(1f), style = TextStyle(fontSize = 15.sp, color = c.ink), maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(Progress.DAYS_TO_SOLID) { Dot(it < days) } }
                    Text(
                        if (days >= Progress.DAYS_TO_SOLID) "Solid" else "Day $days of ${Progress.DAYS_TO_SOLID}",
                        modifier = Modifier.padding(start = 12.dp),
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (days >= Progress.DAYS_TO_SOLID) FontWeight.Bold else FontWeight.Medium,
                            color = if (days >= Progress.DAYS_TO_SOLID) c.correct else c.secondary,
                        ),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            }
            Gap(20)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                summary.medianMs?.let { Stat("Median answer", "%.1f s".format(it / 1000.0), Modifier.weight(1f)) }
                if (summary.sprintTotal > 0) Stat("Look-alike sprint", "${summary.sprintRight} / ${summary.sprintTotal}", Modifier.weight(1f))
            }
        }
        Side {
            if (summary.tagged > 0) {
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.divider)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        if (summary.tagged == 1) "✓  1 note tagged in Anki" else "✓  ${summary.tagged} notes tagged in Anki",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink),
                    )
                }
            }
            PrimaryButton("Done", onClick = onDone)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Kani.colors
    KCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = TextStyle(fontSize = 13.sp, color = c.secondary))
        Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink))
    }
}
