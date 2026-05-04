package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.R
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import dev.bee.kanjianki.ui.components.BlossomCard
import dev.bee.kanjianki.ui.components.BlossomTag
import dev.bee.kanjianki.ui.components.BlossomTagFlow
import dev.bee.kanjianki.ui.components.BlossomTone
import dev.bee.kanjianki.ui.components.DetailLine
import dev.bee.kanjianki.ui.components.EmptyStateCard
import dev.bee.kanjianki.ui.components.MetricTile
import dev.bee.kanjianki.ui.components.SectionEyebrow
import dev.bee.kanjianki.ui.components.StatusBanner
import dev.bee.kanjianki.ui.components.ghostButtonColors
import dev.bee.kanjianki.ui.components.primaryButtonColors
import dev.bee.kanjianki.ui.components.secondaryButtonColors
import dev.bee.kanjianki.ui.components.warmButtonColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyScreen(
    overview: StudyOverviewSnapshot?,
    refreshResult: SeedRefreshSnapshot?,
    session: StudySessionSnapshot?,
    review: StudyReviewSnapshot?,
    statusMessage: String?,
    onStudyNow: () -> Unit,
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BlossomCard(tone = BlossomTone.VIOLET) {
            SectionEyebrow("Study lounge")
            Text(
                text = "Fast queue overview",
                style = MaterialTheme.typography.titleLarge,
            )
            if (overview == null) {
                Text(
                    text = "Loading your queue…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        label = "Due",
                        value = overview.dueCount.toString(),
                        tone = BlossomTone.PINK,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "New",
                        value = overview.newCount.toString(),
                        tone = BlossomTone.APRICOT,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        label = "Active queue",
                        value = overview.activeQueueCount.toString(),
                        tone = BlossomTone.MINT,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Problem seeds",
                        value = overview.currentProblemSeedCount.toString(),
                        tone = BlossomTone.ROSE,
                        supporting = overview.nextDueAt ?: "No due item scheduled yet",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            StatusBanner(
                message = statusMessage,
                tone = BlossomTone.MINT,
            )
        }

        if (refreshResult != null) {
            StatusBanner(
                message = "Seed refresh introduced ${refreshResult.introducedCount}, updated ${refreshResult.updatedCount}, reactivated ${refreshResult.reactivatedCount}, and inactivated ${refreshResult.inactivatedCount}.",
                tone = BlossomTone.APRICOT,
            )
        }

        BlossomCard(tone = BlossomTone.PINK) {
            SectionEyebrow("Launch pad")
            Text(
                text = "Start the next useful session",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Review is the speed run. Mixed keeps you moving. New is only for fresh introductions.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStudyNow,
                modifier = Modifier.fillMaxWidth(),
                colors = primaryButtonColors(),
            ) {
                Text("Study now")
            }
            Button(
                onClick = onLoadReviewSession,
                modifier = Modifier.fillMaxWidth(),
                colors = secondaryButtonColors(),
            ) {
                Text("Review only")
            }
            Button(
                onClick = onLoadMixedSession,
                modifier = Modifier.fillMaxWidth(),
                colors = warmButtonColors(),
            ) {
                Text("Start a mixed session")
            }
            Button(
                onClick = onLoadNewSession,
                modifier = Modifier.fillMaxWidth(),
                colors = ghostButtonColors(),
            ) {
                Text("Open a new batch")
            }
        }

        Button(
            onClick = onRefreshSeeds,
            modifier = Modifier.fillMaxWidth(),
            colors = ghostButtonColors(),
        ) {
            Text("Refresh queue seeds")
        }

        if (session == null) {
            EmptyStateCard(
                title = "No active session yet",
                body = "Pick one of the launch buttons above and the prompt card will appear here ready for a fast review.",
                plushieRes = R.drawable.plushie_quick_session,
            )
        } else {
            BlossomCard(tone = BlossomTone.ROSE) {
                SectionEyebrow("Current prompt")
                Text(
                    text = "${session.kanji} · ${session.promptLabel}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BlossomTag(text = session.taskKind, tone = BlossomTone.PINK, selected = true)
                    BlossomTag(text = session.schedulerPhase, tone = BlossomTone.VIOLET)
                    BlossomTag(text = session.itemStatus, tone = BlossomTone.MINT)
                    BlossomTag(text = session.guideLevelLabel, tone = BlossomTone.APRICOT)
                    BlossomTag(
                        text = if (session.requiresWriting) "Writing required" else "Recognition ok",
                        tone = if (session.requiresWriting) BlossomTone.DANGER else BlossomTone.MINT,
                    )
                }
                DetailLine(label = "Keyword", value = session.keyword)
                DetailLine(label = "Prompt type", value = session.promptType)
                DetailLine(
                    label = "Allowed ratings after failed writing",
                    value = session.handwritingPolicy.allowedRatingsOnFailure.joinToString().ifBlank { "none" },
                )
                DetailSection(
                    title = "Production context",
                    values = session.productionContext,
                    tone = BlossomTone.ROSE,
                )
                DetailSection(
                    title = "Recognition context",
                    values = session.recognitionContext,
                    tone = BlossomTone.VIOLET,
                )
                DetailSection(
                    title = "Support words",
                    values = session.supportWords,
                    tone = BlossomTone.MINT,
                )
                Button(
                    onClick = onSubmitPass,
                    modifier = Modifier.fillMaxWidth(),
                    colors = primaryButtonColors(),
                ) {
                    Text("Mark pass")
                }
                Button(
                    onClick = onSubmitRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ghostButtonColors(),
                ) {
                    Text("Mark retry")
                }
            }
        }

        if (review != null) {
            BlossomCard(tone = BlossomTone.MINT) {
                SectionEyebrow("Latest review")
                Text(
                    text = review.binaryOutcome.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BlossomTag(text = review.itemStatus, tone = BlossomTone.MINT, selected = true)
                    BlossomTag(text = "Review #${review.reviewCount}", tone = BlossomTone.VIOLET)
                    BlossomTag(text = review.guideLevelLabel, tone = BlossomTone.APRICOT)
                    BlossomTag(text = "${review.overviewDueCount} still due", tone = BlossomTone.ROSE)
                }
                DetailLine(label = "Reviewed at", value = review.reviewedAt)
                DetailLine(label = "Next due", value = review.dueAt ?: "No due date scheduled")
            }
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
