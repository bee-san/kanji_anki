package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.R
import dev.bee.kanjianki.domain.DashboardRowSnapshot
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.ui.components.BlossomCard
import dev.bee.kanjianki.ui.components.BlossomTag
import dev.bee.kanjianki.ui.components.BlossomTone
import dev.bee.kanjianki.ui.components.DetailLine
import dev.bee.kanjianki.ui.components.DividerPetal
import dev.bee.kanjianki.ui.components.EmptyStateCard
import dev.bee.kanjianki.ui.components.MetricTile
import dev.bee.kanjianki.ui.components.SectionEyebrow
import dev.bee.kanjianki.ui.components.StatusBanner
import dev.bee.kanjianki.ui.components.ghostButtonColors
import dev.bee.kanjianki.ui.components.primaryButtonColors
import dev.bee.kanjianki.ui.components.secondaryButtonColors
import dev.bee.kanjianki.ui.components.warmButtonColors

@Composable
fun DashboardScreen(
    dashboard: DashboardSnapshot?,
    selectedKanji: String?,
    syncBusy: Boolean,
    syncStatusMessage: String?,
    onSyncNow: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onStartReviewSession: () -> Unit,
    onStartMixedSession: () -> Unit,
    onStartNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dashboard == null) {
        EmptyStateCard(
            title = "Arranging your dashboard garden…",
            body = "Loading the cached collection snapshot and lining up the quickest study paths.",
            plushieRes = R.drawable.plushie_read_book,
            modifier = modifier.padding(16.dp),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QuickLaunchCard(
                syncBusy = syncBusy,
                onSyncNow = onSyncNow,
                onStartReviewSession = onStartReviewSession,
                onStartMixedSession = onStartMixedSession,
                onStartNewSession = onStartNewSession,
            )
        }
        item {
            BlossomCard(tone = BlossomTone.ROSE) {
                SectionEyebrow("Collection snapshot")
                Text(
                    text = "What needs love first",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        label = "Total kanji",
                        value = dashboard.summary.totalKanjiCount.toString(),
                        tone = BlossomTone.PINK,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Unknown",
                        value = dashboard.summary.unknownKanjiCount.toString(),
                        tone = BlossomTone.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        label = "Average rank",
                        value = dashboard.summary.averageKanjiRank?.let { "%.1f".format(it) } ?: "n/a",
                        tone = BlossomTone.VIOLET,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Problem seeds",
                        value = dashboard.problemSeedCount.toString(),
                        tone = BlossomTone.APRICOT,
                        supporting = "${dashboard.sourceCounts.noteCount} notes / ${dashboard.sourceCounts.cardCount} cards",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (!syncStatusMessage.isNullOrBlank()) {
            item {
                StatusBanner(
                    message = syncStatusMessage,
                    tone = if (syncBusy) BlossomTone.APRICOT else BlossomTone.MINT,
                )
            }
        }
        items(dashboard.warnings) { warning ->
            StatusBanner(
                message = warning,
                tone = BlossomTone.DANGER,
            )
        }
        item {
            BlossomCard(tone = BlossomTone.PINK) {
                SectionEyebrow("Kanji watchlist")
                Text(
                    text = "Tap a row and jump straight into detail",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "The list is ordered for triage: support gaps, suspended bridges, and unknown troublemakers rise to the top.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(dashboard.rows) { row ->
            DashboardRowCard(
                row = row,
                selected = row.kanji == selectedKanji,
                onOpenDetail = { onOpenDetail(row.kanji) },
            )
        }
    }
}

@Composable
private fun QuickLaunchCard(
    syncBusy: Boolean,
    onSyncNow: () -> Unit,
    onStartReviewSession: () -> Unit,
    onStartMixedSession: () -> Unit,
    onStartNewSession: () -> Unit,
) {
    BlossomCard(tone = BlossomTone.PINK) {
        SectionEyebrow("Study fast")
        Text(
            text = "One tap into the queue",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Review first if you want the quickest win. Mixed keeps the queue lively. New is for fresh introductions only.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onStartReviewSession,
            modifier = Modifier.fillMaxWidth(),
            colors = primaryButtonColors(),
        ) {
            Text("Review now")
        }
        Button(
            onClick = onStartMixedSession,
            modifier = Modifier.fillMaxWidth(),
            colors = secondaryButtonColors(),
        ) {
            Text("Start a mixed session")
        }
        Button(
            onClick = onStartNewSession,
            modifier = Modifier.fillMaxWidth(),
            colors = warmButtonColors(),
        ) {
            Text("Open a new batch")
        }
        Button(
            onClick = onSyncNow,
            enabled = !syncBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = ghostButtonColors(),
        ) {
            Text(if (syncBusy) "Syncing collection…" else "Refresh collection snapshot")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardRowCard(
    row: DashboardRowSnapshot,
    selected: Boolean,
    onOpenDetail: () -> Unit,
) {
    BlossomCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
        tone = if (selected) BlossomTone.PINK else BlossomTone.ROSE,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = row.kanji,
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "Rank ${row.jitenRank?.let { "%.1f".format(it) } ?: "unknown"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                BlossomTag(
                    text = "Open in detail",
                    tone = BlossomTone.PINK,
                    selected = true,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (row.isUnknown) {
                BlossomTag("Unknown", tone = BlossomTone.DANGER, selected = true)
            }
            if (row.supportDeficit > 0) {
                BlossomTag("Support deficit ${row.supportDeficit}", tone = BlossomTone.APRICOT)
            }
            if (row.suspendedExpressionCount > 0) {
                BlossomTag("Suspended ${row.suspendedExpressionCount}", tone = BlossomTone.ROSE)
            }
            if (row.activeRecurringExpressionCount > 0) {
                BlossomTag("Bridge ${row.activeRecurringExpressionCount}", tone = BlossomTone.MINT)
            }
            BlossomTag("Collection ${row.collectionExpressionCount}", tone = BlossomTone.VIOLET)
            BlossomTag("Mature ${row.matureSupportCount}", tone = BlossomTone.PINK)
        }
        DividerPetal()
        DetailLine(label = "Browser search", value = row.browserSearch)
        DetailLine(
            label = "Why this row matters",
            value = buildString {
                append("${row.collectionExpressionCount} collection expressions tracked. ")
                append("${row.matureSupportCount} mature supports available. ")
                if (row.supportDeficit > 0) {
                    append("${row.supportDeficit} more mature supports would stabilize it.")
                } else {
                    append("Support target is currently satisfied.")
                }
            },
        )
    }
}
