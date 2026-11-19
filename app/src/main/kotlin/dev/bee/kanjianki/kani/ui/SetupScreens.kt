package dev.bee.kanjianki.kani.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.kani.ScanSummary
import dev.bee.kanjianki.kani.core.ScanAnalyzer
import dev.bee.kanjianki.kani.data.Scanner
import java.text.NumberFormat

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Page(top = 64.dp) {
        KaSeal(88.dp)
        Gap(40)
        Title("Stop mixing up the kanji you already study", size = 34)
        Gap(14)
        Body("Kani reads your AnkiDroid history, finds the kanji you keep missing, and trains only those.", color = Kani.colors.secondary, size = 17)
        Gap(28)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PairChip("未", "末")
            PairChip("待", "持")
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Get started", onClick = onStart)
    }
}

@Composable
private fun PairChip(a: String, b: String) {
    val c = Kani.colors
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(c.chip).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KanjiText(a, 30)
        Text("  ·  ", style = TextStyle(color = c.dim, fontSize = 16.sp))
        KanjiText(b, 30)
    }
}

@Composable
private fun SetupHeader(step: Int, onBack: (() -> Unit)?) {
    TopRow {
        if (onBack != null) TextLink("Back", color = Kani.colors.secondary, onClick = onBack)
        Spacer(Modifier.weight(1f))
        Text("$step / 4", style = TextStyle(fontSize = 14.sp, color = Kani.colors.dim, fontWeight = FontWeight.Medium))
    }
    Gap(4)
    ProgressBar(step, 4)
    Gap(28)
}

@Composable
fun ConnectScreen(installed: Boolean, onAllow: () -> Unit, onInstall: () -> Unit, onBack: () -> Unit) {
    Page {
        SetupHeader(1, onBack)
        Title("Connect AnkiDroid")
        Gap(24)
        KCard {
            Eyebrow("Kani reads", color = Kani.colors.correct)
            Gap(10)
            Bullet("Your notes and the decks they're in")
            Bullet("Which cards are leeches, keep failing, or are suspended")
            Gap(18)
            Eyebrow("Kani never")
            Gap(10)
            Bullet("Changes your scheduling")
            Bullet("Edits your cards, unless you turn on tagging")
            Bullet("Sends anything off your phone")
        }
        Spacer(Modifier.weight(1f))
        if (installed) {
            PrimaryButton("Allow access", onClick = onAllow)
        } else {
            Body("AnkiDroid isn't installed on this phone.", color = Kani.colors.secondary)
            Gap(12)
            PrimaryButton("Get AnkiDroid", onClick = onInstall)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•  ", style = TextStyle(color = Kani.colors.dim, fontSize = 15.sp))
        Body(text)
    }
}

@Composable
fun CardsScreen(
    detected: Scanner.Detected?,
    error: String?,
    onConfirm: (Scanner.Detected) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Kani.colors
    var editing by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    Page {
        SetupHeader(2, onBack)
        Title("Here's what Kani will use")
        Gap(20)
        when {
            detected == null && error == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            detected == null || detected.models.isEmpty() -> {
                Body(error ?: "Kani couldn't read AnkiDroid.", color = c.secondary)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Try again", onClick = onRetry)
            }
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    KCard {
                        Eyebrow("Decks", color = c.dim)
                        Gap(8)
                        detected.decks.take(4).forEach { (deck, count) -> KeyValue(deck, NumberFormat.getIntegerInstance().format(count)) }
                        if (detected.decks.size > 4) Body("+ ${detected.decks.size - 4} more", color = c.dim, size = 13)
                        Divider()
                        Eyebrow("Fields", color = c.dim)
                        Gap(8)
                        val m = detected.models.first().mapping
                        KeyValue(m.expression, detected.exampleExpression, kanji = true)
                        m.reading?.let { KeyValue(it, detected.exampleReading) }
                        m.meaning?.let { KeyValue(it, detected.exampleMeaning) }
                        Divider()
                        Eyebrow("Looks for", color = c.dim)
                        Gap(8)
                        Body("Leeches, cards failed twice or more, and suspended cards.")
                    }
                    if (editing) {
                        Gap(20)
                        Eyebrow("Note types", color = c.dim)
                        Gap(6)
                        detected.models.forEach { model ->
                            val on = model.name !in excluded
                            ToggleRow(
                                "${model.name} · ${NumberFormat.getIntegerInstance().format(model.notes)}",
                                on,
                            ) { excluded = if (on) excluded + model.name else excluded - model.name }
                        }
                    }
                }
                Gap(16)
                val chosen = detected.copy(models = detected.models.filter { it.name !in excluded })
                PrimaryButton("Looks good", enabled = chosen.models.isNotEmpty()) { onConfirm(chosen) }
                if (!editing) {
                    Gap(4)
                    TextLink("Change something", modifier = Modifier.align(Alignment.CenterHorizontally)) { editing = true }
                }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, kanji: Boolean = false) {
    val c = Kani.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 15.sp, color = c.secondary), maxLines = 1)
        if (kanji) {
            KanjiText(value, 18)
        } else {
            Text(value, style = TextStyle(fontSize = 15.sp, color = c.ink, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

@Composable
private fun Divider() {
    Gap(14)
    Box(Modifier.fillMaxWidth().height(1.dp).background(Kani.colors.divider))
    Gap(14)
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val c = Kani.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 16.sp, color = c.ink))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.buttonText,
                checkedTrackColor = c.ink,
                uncheckedThumbColor = c.dim,
                uncheckedTrackColor = c.chip,
                uncheckedBorderColor = c.line,
            ),
        )
    }
}

@Composable
fun ScanningScreen(scanned: Int, summary: ScanSummary?, inSetup: Boolean, onContinue: () -> Unit, onSeeWords: () -> Unit) {
    val c = Kani.colors
    val nf = NumberFormat.getIntegerInstance()
    Page {
        if (inSetup) SetupHeader(3, null) else Gap(44)
        if (summary == null) {
            Title("Reading your cards")
            Gap(12)
            val shown by animateIntAsState(scanned, tween(600), label = "scanned")
            Body(if (shown == 0) "Looking through AnkiDroid…" else "${nf.format(shown)} cards", color = c.secondary)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            return@Page
        }
        Title(if (summary.flagged == 1) "1 kanji keeps slipping" else "${nf.format(summary.flagged)} kanji keep slipping")
        Gap(8)
        Body("Out of ${nf.format(summary.distinctKanji)} kanji in your decks", color = c.secondary)
        Gap(24)
        KCard {
            ReasonRow("Leech", summary.reasons[ScanAnalyzer.Reason.LEECH] ?: 0)
            ReasonRow("Failed again and again", summary.reasons[ScanAnalyzer.Reason.FAILED] ?: 0)
            ReasonRow("Suspended", summary.reasons[ScanAnalyzer.Reason.SUSPENDED] ?: 0)
        }
        if (summary.lookAlikes.isNotEmpty()) {
            Gap(24)
            Eyebrow("Look-alikes in your decks", color = c.dim)
            Gap(10)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.lookAlikes.forEach { (a, b) ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        KanjiText("$a · $b", 20)
                    }
                }
            }
        }
        Gap(8)
        TextLink("How Kani picked these", onClick = onSeeWords)
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (summary.flagged == 0) "Done" else "Continue", onClick = onContinue)
    }
}

@Composable
private fun ReasonRow(label: String, count: Int) {
    val c = Kani.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 16.sp, color = c.ink))
        Text("$count", style = TextStyle(fontSize = 18.sp, color = c.ink, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun DailyScreen(
    initialMinutes: Int,
    onBack: () -> Unit,
    onStart: (minutes: Int, reminder: Boolean, handwriting: Boolean, tag: Boolean) -> Unit,
) {
    val c = Kani.colors
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    var reminder by remember { mutableStateOf(true) }
    var handwriting by remember { mutableStateOf(true) }
    var tag by remember { mutableStateOf(false) }
    Page {
        SetupHeader(4, onBack)
        Title("A little every day")
        Gap(24)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(3, 5, 10).forEach { m ->
                val on = m == minutes
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) c.ink else c.card)
                        .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(18.dp))
                        .clickable(role = Role.RadioButton) { minutes = m }
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$m", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (on) c.buttonText else c.ink))
                    Text("min", style = TextStyle(fontSize = 14.sp, color = if (on) c.buttonText else c.secondary))
                }
            }
        }
        Gap(24)
        ToggleRow("Daily reminder", reminder) { reminder = !reminder }
        ToggleRow("Handwriting", handwriting) { handwriting = !handwriting }
        ToggleRow("Tag solid notes in Anki", tag) { tag = !tag }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Start my first session") { onStart(minutes, reminder, handwriting, tag) }
    }
}
