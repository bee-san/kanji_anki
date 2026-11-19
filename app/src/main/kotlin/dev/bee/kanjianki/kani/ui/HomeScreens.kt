package dev.bee.kanjianki.kani.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.kani.Pair2
import dev.bee.kanjianki.kani.TodayData
import dev.bee.kanjianki.kani.core.Status

@Composable
fun TodayScreen(data: TodayData, onStart: () -> Unit, onSprint: () -> Unit, onBlindSpots: () -> Unit, onSettings: () -> Unit) {
    val c = Kani.colors
    Page {
        TopRow {
            KaSeal(34.dp)
            HGap(10)
            Wordmark(size = 26)
            Spacer(Modifier.weight(1f))
            SettingsIcon(onSettings)
        }
        Gap(28)
        KCard(padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            Eyebrow("Today")
            Gap(10)
            when {
                data.due > 0 -> {
                    Title(if (data.due == 1) "1 kanji you keep missing" else "${data.due} kanji you keep missing", size = 32)
                    Gap(22)
                    PrimaryButton("Start", onClick = onStart)
                }
                data.pairs.isNotEmpty() -> {
                    Title("Nothing slipping today", size = 32)
                    Gap(22)
                    PrimaryButton("Sprint your look-alikes", onClick = onSprint)
                }
                else -> Title("Nothing slipping today", size = 32)
            }
        }
        if (data.solid + data.training > 0) {
            Gap(12)
            Text(
                "${data.solid} solid · ${data.training} training",
                modifier = Modifier.padding(start = 4.dp),
                style = TextStyle(fontSize = 14.sp, color = c.dim),
            )
        }
        if (data.pairs.isNotEmpty()) {
            Gap(32)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your blind spots", modifier = Modifier.weight(1f), style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink))
                TextLink("See all", onClick = onBlindSpots)
            }
            Gap(4)
            data.pairs.forEach { PairRow(it, onClick = onBlindSpots) }
        }
    }
}

@Composable
private fun SettingsIcon(onClick: () -> Unit) {
    val c = Kani.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val sw = w * 0.09f
            listOf(0.22f to 0.32f, 0.5f to 0.68f, 0.78f to 0.42f).forEach { (y, knob) ->
                drawLine(c.ink, Offset(0f, w * y), Offset(w, w * y), sw, StrokeCap.Round)
                drawCircle(c.bg, w * 0.12f, Offset(w * knob, w * y))
                drawCircle(c.ink, w * 0.12f, Offset(w * knob, w * y), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            }
        }
    }
}

@Composable
fun PairRow(pair: Pair2, onClick: (() -> Unit)? = null) {
    val c = Kani.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KanjiText(pair.target, 28)
        Text("  ·  ", style = TextStyle(color = c.dim, fontSize = 16.sp))
        KanjiText(pair.other, 28)
        Spacer(Modifier.weight(1f))
        Text(
            when (pair.mixups) {
                0 -> "look-alike"
                1 -> "1 mix-up"
                else -> "${pair.mixups} mix-ups"
            },
            style = TextStyle(fontSize = 14.sp, color = c.secondary),
        )
        HGap(12)
        StagePill(pair.stage)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
}

@Composable
fun BlindSpotsScreen(pairs: List<Pair2>, onBack: () -> Unit, onSprint: () -> Unit) {
    val c = Kani.colors
    Page {
        TopRow { TextLink("← Today", color = c.secondary, onClick = onBack) }
        Gap(12)
        Eyebrow("From your own answers")
        Gap(8)
        Title("Blind spots")
        Gap(16)
        if (pairs.isEmpty()) {
            Body("No look-alikes yet. They show up here as you answer.", color = c.secondary)
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(pairs, key = { it.target + it.other }) { PairRow(it) }
            }
            Gap(16)
            PrimaryButton("Sprint these · 2 min", onClick = onSprint)
        }
    }
}

data class WordRow(val expression: String, val kanji: List<Pair<String, Status?>>)

@Composable
fun WordsScreen(rows: List<WordRow>, onBack: () -> Unit, onCheckAnyway: (String) -> Unit) {
    val c = Kani.colors
    Page {
        TopRow { TextLink("← Back", color = c.secondary, onClick = onBack) }
        Gap(12)
        Eyebrow("Which kanji is the problem")
        Gap(8)
        Title("Only the kanji you miss")
        Gap(8)
        Body(
            "A failing word only flags the kanji you don't read fine elsewhere. Tap a cleared kanji to train it anyway.",
            color = c.secondary,
        )
        Gap(16)
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.expression }) { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    KanjiText(row.expression, 22, modifier = Modifier.align(Alignment.Start))
                    Gap(8)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.kanji.forEach { (k, status) -> CulpritChip(k, status) { onCheckAnyway(k) } }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            }
        }
    }
}

@Composable
private fun CulpritChip(kanji: String, status: Status?, onCheckAnyway: () -> Unit) {
    val c = Kani.colors
    val (bg, fg, label) = when (status) {
        Status.TRAINING -> Triple(c.trainingBg, c.trainingText, "This one")
        Status.SOLID -> Triple(c.solidBg, c.solidText, "Solid")
        Status.CLEARED -> Triple(c.chip, c.secondary, "Cleared")
        null -> Triple(c.chip, c.dim, "")
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (status == Status.CLEARED) Modifier.clickable(role = Role.Button, onClick = onCheckAnyway) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KanjiText(kanji, 20, color = fg)
        if (label.isNotEmpty()) {
            HGap(8)
            Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg))
        }
    }
}

data class SettingsState(
    val theme: ThemeMode,
    val minutes: Int,
    val reminder: Boolean,
    val handwriting: Boolean,
    val tag: Boolean,
    val speak: Boolean,
    val version: String,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onMinutes: (Int) -> Unit,
    onReminder: () -> Unit,
    onHandwriting: () -> Unit,
    onTag: () -> Unit,
    onSpeak: () -> Unit,
    onRescan: () -> Unit,
    onChangeCards: () -> Unit,
    onWords: () -> Unit,
) {
    val c = Kani.colors
    Page {
        TopRow { TextLink("← Today", color = c.secondary, onClick = onBack) }
        Gap(12)
        Title("Settings")
        Gap(20)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Eyebrow("Look", color = c.dim)
            Gap(10)
            Segmented(
                listOf("System" to ThemeMode.SYSTEM, "Light" to ThemeMode.LIGHT, "AMOLED" to ThemeMode.AMOLED),
                state.theme,
                onTheme,
            )
            Gap(24)
            Eyebrow("Daily", color = c.dim)
            Gap(10)
            Segmented(listOf("3 min" to 3, "5 min" to 5, "10 min" to 10), state.minutes, onMinutes)
            Gap(8)
            ToggleRow("Daily reminder", state.reminder, onReminder)
            ToggleRow("Handwriting", state.handwriting, onHandwriting)
            ToggleRow("Speak the word on reveal", state.speak, onSpeak)
            Gap(16)
            Eyebrow("Anki", color = c.dim)
            Gap(4)
            ToggleRow("Tag solid notes in Anki", state.tag, onTag)
            SettingsLink("Scan AnkiDroid again", onRescan)
            SettingsLink("Change decks and fields", onChangeCards)
            SettingsLink("How Kani picked your kanji", onWords)
            Gap(24)
            Text("Kani ${state.version}", style = TextStyle(fontSize = 13.sp, color = c.dim))
            Gap(4)
            Text(
                "Stroke data: KanjiVG by Ulrich Apel (CC BY-SA 3.0). Meanings and readings: KANJIDIC2 (EDRDG). " +
                    "Look-alike pairs: Kiku.",
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = c.dim),
            )
        }
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        style = TextStyle(fontSize = 16.sp, color = Kani.colors.ink),
    )
}

@Composable
fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val c = Kani.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.ink else c.bg)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (on) c.buttonText else c.ink))
            }
        }
    }
}
