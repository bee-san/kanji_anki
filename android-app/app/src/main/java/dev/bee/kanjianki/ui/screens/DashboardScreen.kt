package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.domain.DashboardSnapshot

@Composable
fun DashboardScreen(
    dashboard: DashboardSnapshot?,
    selectedKanji: String?,
    syncBusy: Boolean,
    syncStatusMessage: String?,
    onSyncNow: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dashboard == null) {
        Text(
            text = "Loading dashboard sync state…",
            modifier = modifier.padding(24.dp),
        )
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dashboard", style = MaterialTheme.typography.titleLarge)
                    Text("Total kanji: ${dashboard.summary.totalKanjiCount}")
                    Text("Unknown kanji: ${dashboard.summary.unknownKanjiCount}")
                    Text("Average rank: ${dashboard.summary.averageKanjiRank ?: 0.0}")
                    Text("Problem seeds: ${dashboard.problemSeedCount}")
                    Text("Source counts: ${dashboard.sourceCounts.noteCount} notes / ${dashboard.sourceCounts.cardCount} cards")
                    if (!syncStatusMessage.isNullOrBlank()) {
                        Text(syncStatusMessage, style = MaterialTheme.typography.bodySmall)
                    }
                    if (syncBusy) {
                        CircularProgressIndicator()
                    }
                    Button(
                        onClick = onSyncNow,
                        enabled = !syncBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sync collection now")
                    }
                }
            }
        }
        items(dashboard.rows) { row ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(row.kanji) },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${row.kanji}  rank ${row.jitenRank ?: "?"}", style = MaterialTheme.typography.titleMedium)
                    if (row.kanji == selectedKanji) {
                        Text("Selected in Detail", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Collection expressions: ${row.collectionExpressionCount}")
                    Text("Suspended: ${row.suspendedExpressionCount}  Active bridge: ${row.activeRecurringExpressionCount}")
                    Text("Mature support: ${row.matureSupportCount}  Deficit: ${row.supportDeficit}")
                    Text("Search: ${row.browserSearch}", style = MaterialTheme.typography.bodySmall)
                    Text("Tap to open detail", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
