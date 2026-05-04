package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot

@Composable
fun StudyScreen(
    overview: StudyOverviewSnapshot?,
    refreshResult: SeedRefreshSnapshot?,
    session: StudySessionSnapshot?,
    review: StudyReviewSnapshot?,
    statusMessage: String?,
    onRefreshSeeds: () -> Unit,
    onLoadNewSession: () -> Unit,
    onLoadMixedSession: () -> Unit,
    onLoadReviewSession: () -> Unit,
    onSubmitPass: () -> Unit,
    onSubmitRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Study Overview", style = MaterialTheme.typography.titleLarge)
                if (overview == null) {
                    Text("Loading study overview…")
                } else {
                    Text("Due: ${overview.dueCount}  New: ${overview.newCount}  Active: ${overview.activeQueueCount}")
                    Text("Problem seeds: ${overview.currentProblemSeedCount}")
                    Text("Next due: ${overview.nextDueAt ?: "none"}")
                }
                if (refreshResult != null) {
                    Text(
                        "Refresh introduced ${refreshResult.introducedCount}, updated ${refreshResult.updatedCount}, inactivated ${refreshResult.inactivatedCount}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!statusMessage.isNullOrBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Study actions", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onRefreshSeeds, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh seeds")
                }
                Button(onClick = onLoadNewSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Load next new session")
                }
                Button(onClick = onLoadMixedSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Load next mixed session")
                }
                Button(onClick = onLoadReviewSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Load next review session")
                }
                Button(
                    onClick = onSubmitPass,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = session != null,
                ) {
                    Text("Submit pass review")
                }
                Button(
                    onClick = onSubmitRetry,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = session != null,
                ) {
                    Text("Submit retry review")
                }
            }
        }

        if (session != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Current session", style = MaterialTheme.typography.titleMedium)
                    Text("${session.kanji}  ${session.promptLabel}")
                    Text("Prompt: ${session.promptType}  Task: ${session.taskKind}")
                    Text("Scheduler: ${session.schedulerPhase}")
                    Text("Requires writing: ${session.requiresWriting}")
                    Text("Guide: ${session.handwritingPolicy.guideMode} / ${session.handwritingPolicy.guideLevelLabel}")
                    Text("Allowed ratings on failure: ${session.handwritingPolicy.allowedRatingsOnFailure.joinToString()}")
                    Text("Keyword: ${session.keyword}")
                    Text("Context: ${session.productionContext.joinToString()}")
                    Text("Support words: ${session.supportWords.joinToString()}")
                }
            }
        }

        if (review != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Latest review", style = MaterialTheme.typography.titleMedium)
                    Text("Outcome: ${review.binaryOutcome}")
                    Text("Item status: ${review.itemStatus}")
                    Text("Review count: ${review.reviewCount}")
                    Text("Guide level: ${review.guideLevelLabel}")
                    Text("Next due: ${review.dueAt ?: "none"}")
                    Text("Overview due count: ${review.overviewDueCount}")
                }
            }
        }
    }
}
