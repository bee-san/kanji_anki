package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.R
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.ui.components.BlossomCard
import dev.bee.kanjianki.ui.components.BlossomTagFlow
import dev.bee.kanjianki.ui.components.BlossomTone
import dev.bee.kanjianki.ui.components.DetailLine
import dev.bee.kanjianki.ui.components.DividerPetal
import dev.bee.kanjianki.ui.components.EmptyStateCard
import dev.bee.kanjianki.ui.components.SectionEyebrow

@Composable
fun DetailScreen(
    detail: KanjiDetailSnapshot?,
    modifier: Modifier = Modifier,
) {
    if (detail == null) {
        EmptyStateCard(
            title = "Pick a kanji first",
            body = "Open any dashboard row and the detail bouquet will bloom here with meanings, readings, and support examples.",
            plushieRes = R.drawable.plushie_sleepy,
            modifier = modifier.padding(16.dp),
        )
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BlossomCard(tone = BlossomTone.PINK) {
            SectionEyebrow("Detail bouquet")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = detail.kanji,
                    style = MaterialTheme.typography.displayLarge,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = detail.keyword.ifBlank { "No keyword yet" },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Rank ${detail.jitenRank?.let { "%.1f".format(it) } ?: "unknown"} · ${detail.strokeCount} strokes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (detail.componentHint.isNotBlank()) {
                        Text(
                            text = detail.componentHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DividerPetal()
            DetailLine(label = "Meanings", value = detail.meanings.joinToString().ifBlank { "none yet" })
            DetailLine(label = "Browser search", value = detail.browserSearch)
        }

        BlossomCard(tone = BlossomTone.VIOLET) {
            SectionEyebrow("Readings")
            Text(
                text = "Sound ribbons",
                style = MaterialTheme.typography.titleLarge,
            )
            DetailSection(title = "On readings", values = detail.onReadings, tone = BlossomTone.VIOLET)
            DetailSection(title = "Kun readings", values = detail.kunReadings, tone = BlossomTone.ROSE)
        }

        BlossomCard(tone = BlossomTone.APRICOT) {
            SectionEyebrow("Components")
            Text(
                text = "Pieces to remember",
                style = MaterialTheme.typography.titleLarge,
            )
            BlossomTagFlow(values = detail.components, tone = BlossomTone.APRICOT)
        }

        BlossomCard(tone = BlossomTone.MINT) {
            SectionEyebrow("Collection examples")
            Text(
                text = "Real support around this kanji",
                style = MaterialTheme.typography.titleLarge,
            )
            DetailSection(title = "Collection", values = detail.collectionExamples, tone = BlossomTone.MINT)
            DetailSection(title = "Bridge", values = detail.activeRecurringExamples, tone = BlossomTone.PINK)
            DetailSection(title = "Mature", values = detail.matureExamples, tone = BlossomTone.VIOLET)
            DetailSection(title = "Suspended", values = detail.suspendedExamples, tone = BlossomTone.DANGER)
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    values: List<String>,
    tone: BlossomTone,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BlossomTagFlow(values = values, tone = tone)
    }
}
