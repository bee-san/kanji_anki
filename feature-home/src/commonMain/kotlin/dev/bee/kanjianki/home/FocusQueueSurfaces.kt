package dev.bee.kanjianki.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.FocusCard
import dev.bee.kanjianki.presentation.FocusQueue
import dev.bee.kanjianki.presentation.FocusTag
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val FOCUS_QUEUE_TEST_TAG: String = "kani-focus-queue"
const val FOCUS_QUEUE_VIEW_ALL_TEST_TAG: String = "kani-focus-queue-view-all"
const val FOCUS_QUEUE_EMPTY_TEST_TAG: String = "kani-focus-queue-empty"

/** One card per queued kanji, tagged by the kanji it is about. */
fun focusCardTestTag(kanji: String): String = "kani-focus-card-$kanji"

/**
 * The focus queue: what Kani is working on, or why it is not working on anything.
 *
 * The empty state names its own cause. An empty queue on an empty collection needs a
 * sync and an empty queue on a full one needs a study session, and a single "nothing
 * queued" message sends half the users who see it to the wrong remedy — which is what
 * [FocusQueue.emptyReason] exists to prevent.
 */
@Composable
fun FocusQueuePanel(
    queue: FocusQueue,
    copy: DashboardCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = resolver.resolve(queue.plan)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FOCUS_QUEUE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copy.focusQueueTitle,
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            // Offered only when there is a fuller list to see. A "View all" leading to
            // the same empty screen is a dead end wearing an invitation.
            if (queue.showsViewAll) {
                TextButton(
                    onClick = { dispatch(queue.viewAllAction) },
                    modifier = Modifier
                        .heightIn(min = KaniUiTokens.MinTouchTarget)
                        .testTag(FOCUS_QUEUE_VIEW_ALL_TEST_TAG),
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = copy.focusQueueViewAll)
                }
            }
        }
        if (plan.isNotBlank()) {
            Text(
                text = plan,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
        val reason = queue.emptyReason
        if (reason == null) {
            for (card in queue.cards) {
                FocusQueueCard(card, copy, resolver, dispatch)
            }
        } else {
            FocusQueueEmptyState(
                title = copy.emptyTitle(reason),
                body = copy.emptyBody(reason),
            )
        }
    }
}

@Composable
private fun FocusQueueCard(
    card: FocusCard,
    copy: DashboardCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    val meaning = resolver.resolve(card.meaning)
    val accent = card.accent.color(KaniTheme.colors)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(focusCardTestTag(card.kanji))
            // Merged to the kanji and its meaning rather than reading all six of the
            // card's texts: the badges and the reason line restate what the meaning
            // already said, and a card announced in fragments is slower to skip than
            // to read through.
            .semantics { contentDescription = copy.focusCardDescription(card.kanji, meaning) },
        shape = KaniUiTokens.LeafShape,
        color = accent.copy(alpha = CARD_FILL_ALPHA),
        border = BorderStroke(1.dp, accent.copy(alpha = CARD_STROKE_ALPHA)),
        onClick = { dispatch(card.action) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(KANJI_TILE_SIZE)
                    .semantics { }, // The kanji is already in the card description.
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = KaniUiTokens.StudyShapeSmall,
                    color = accent.copy(alpha = TILE_FILL_ALPHA),
                    border = BorderStroke(1.dp, accent.copy(alpha = TILE_STROKE_ALPHA)),
                ) {
                    Text(
                        text = card.kanji,
                        modifier = Modifier.padding(10.dp),
                        color = KaniTheme.colors.ink,
                        fontSize = KaniUiTokens.StudyWordHeroTextSizeSp.sp,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = meaning,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                )
                for (line in supportingLines(card, resolver)) {
                    Text(
                        text = line,
                        color = KaniTheme.colors.muted,
                        fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                    )
                }
                if (card.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (tag in card.tags) {
                            FocusQueueTag(tag, resolver)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusQueueTag(tag: FocusTag, resolver: UiTextResolver) {
    val label = resolver.resolve(tag.label)
    if (label.isBlank()) return
    val accent = tag.accent.color(KaniTheme.colors)
    Surface(
        shape = KaniUiTokens.PillShape,
        color = accent.copy(alpha = TAG_FILL_ALPHA),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = accent,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
    }
}

@Composable
private fun FocusQueueEmptyState(title: String, body: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FOCUS_QUEUE_EMPTY_TEST_TAG)
            .semantics { contentDescription = "$title. $body" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}

/**
 * The card's supporting lines, in reading order, with blanks dropped.
 *
 * A card may have any subset of the three. Rendering an empty `Text` for a missing one
 * leaves a gap the user cannot distinguish from content that failed to load.
 */
private fun supportingLines(card: FocusCard, resolver: UiTextResolver): List<String> =
    listOf(card.reasonLine, card.sourceEvidence, card.body)
        .map(resolver::resolve)
        .filter { it.isNotBlank() }

private val KANJI_TILE_SIZE = 78.dp
private const val CARD_FILL_ALPHA = 0.06f
private const val CARD_STROKE_ALPHA = 0.58f
private const val TILE_FILL_ALPHA = 0.14f
private const val TILE_STROKE_ALPHA = 0.34f
private const val TAG_FILL_ALPHA = 0.16f
