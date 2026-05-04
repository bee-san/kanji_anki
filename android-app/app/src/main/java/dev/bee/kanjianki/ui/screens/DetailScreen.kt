package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.domain.KanjiDetailSnapshot

@Composable
fun DetailScreen(
    detail: KanjiDetailSnapshot?,
    modifier: Modifier = Modifier,
) {
    if (detail == null) {
        Text("Select a kanji from the dashboard to load detail.", modifier = modifier.padding(24.dp))
        return
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${detail.kanji}  ${detail.keyword}", style = MaterialTheme.typography.headlineMedium)
                Text("Rank: ${detail.jitenRank ?: "unknown"}")
                Text("Meanings: ${detail.meanings.joinToString().ifBlank { "none" }}")
                Text("On: ${detail.onReadings.joinToString().ifBlank { "none" }}")
                Text("Kun: ${detail.kunReadings.joinToString().ifBlank { "none" }}")
                Text("Stroke count: ${detail.strokeCount}")
                Text("Component hint: ${detail.componentHint.ifBlank { "none" }}")
                Text("Search: ${detail.browserSearch}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Collection examples", style = MaterialTheme.typography.titleMedium)
                DetailExamplesLine(
                    label = "Collection",
                    values = detail.collectionExamples,
                )
                DetailExamplesLine(
                    label = "Suspended",
                    values = detail.suspendedExamples,
                )
                DetailExamplesLine(
                    label = "Bridge",
                    values = detail.activeRecurringExamples,
                )
                DetailExamplesLine(
                    label = "Mature",
                    values = detail.matureExamples,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Components", style = MaterialTheme.typography.titleMedium)
                Text(detail.components.joinToString().ifBlank { "none" })
            }
        }
    }
}

@Composable
private fun DetailExamplesLine(
    label: String,
    values: List<String>,
) {
    Text("$label: ${values.joinToString().ifBlank { "none" }}")
}
