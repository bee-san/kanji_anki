package dev.bee.kanjianki.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.HomeAccent
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.HomeMetric
import dev.bee.kanjianki.presentation.HomeMetricKind
import dev.bee.kanjianki.presentation.HomeNotice
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.TodayPlan
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniColors
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val HOME_PRIMARY_TEST_TAG: String = "kani-home-primary"
const val HOME_METRIC_ROW_TEST_TAG: String = "kani-home-metrics"
const val HOME_TODAY_TEST_TAG: String = "kani-home-today"
const val HOME_TODAY_ACTION_TEST_TAG: String = "kani-home-today-action"
const val HOME_DECK_OVERVIEW_TEST_TAG: String = "kani-home-deck-overview"

/** One tile per metric, tagged by what it measures rather than by its label. */
fun homeMetricTestTag(kind: HomeMetricKind): String =
    "kani-home-metric-${kind.name.lowercase()}"

/** One notice card per host limitation, tagged by which limitation it explains. */
fun homeNoticeTestTag(notice: HomeNotice): String =
    "kani-home-notice-${notice.name.lowercase()}"

/**
 * Home's primary action: sync when nothing is imported, study once something is.
 *
 * One button in a fixed-height box rather than two that swap, which is the reason the
 * Android screen gave for its own `heightIn(min = …)` wrapper: the primary action must
 * not jump between shapes when the sync state flips, or a tap aimed at Sync lands on
 * whatever moved into its place.
 *
 * The floor is on the button as well as on the box. With it only on the box, the space
 * was reserved but the button kept Material's 40dp default and the remaining 18dp was
 * dead margin — a stable *layout* around an under-sized *target*, which is the half of
 * the promise a user can actually feel.
 */
@Composable
fun HomePrimaryAction(
    home: HomeDashboard,
    copy: DashboardCopy,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = rememberStudyRemainingLine(home.studyRemainingCount)
    val label = if (home.needsFirstSync) {
        copy.syncAction
    } else {
        // The count goes on the button because it is the answer to "is this worth
        // starting" — the question the user is asking as they reach for it.
        remaining.ifBlank { copy.studyAction }
    }
    Column(modifier = modifier.fillMaxWidth().heightIn(min = PRIMARY_ACTION_MIN_HEIGHT)) {
        Button(
            onClick = { dispatch(home.primaryAction) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PRIMARY_ACTION_MIN_HEIGHT)
                .testTag(HOME_PRIMARY_TEST_TAG),
            // Disabled rather than hidden while syncing, for the same reason the
            // onboarding button is: a control that vanishes mid-tap moves what is
            // underneath it under the user's finger.
            enabled = !home.syncing,
            shape = KaniUiTokens.WideButtonShape,
        ) {
            Text(
                text = label,
                fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * The metric tiles.
 *
 * Stacks rather than sitting in a row at large font scales or in a narrow window.
 * Three tiles side by side at a 1.3x font scale truncate their values, and a truncated
 * streak count is worse than no streak count — it reads as a smaller number.
 */
@Composable
fun HomeMetricRow(
    metrics: List<HomeMetric>,
    copy: DashboardCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag(HOME_METRIC_ROW_TEST_TAG)) {
        val stacked = fontScale >= STACK_FONT_SCALE || maxWidth < STACK_WIDTH
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (metric in metrics) {
                    HomeMetricCard(metric, copy, resolver, dispatch, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (metric in metrics) {
                    HomeMetricCard(metric, copy, resolver, dispatch, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    metric: HomeMetric,
    copy: DashboardCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier,
) {
    val label = copy.metricLabel(metric.kind)
    val value = resolver.resolve(metric.value)
    val detail = resolver.resolve(metric.detail)
    val accent = metric.accent.color(KaniTheme.colors)
    val action = metric.action
    val description = listOf(copy.metricCardDescription, label, value, detail)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    Surface(
        modifier = modifier
            .heightIn(min = METRIC_MIN_HEIGHT)
            .testTag(homeMetricTestTag(metric.kind))
            .semantics { contentDescription = description },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, accent.copy(alpha = METRIC_BORDER_ALPHA)),
        // A tile that does nothing is not a button. Only the sync tile has an action,
        // so only the sync tile takes a click — which is also what stops a screen
        // reader promising an activation the streak tile would ignore.
        onClick = { action?.let(dispatch) },
        enabled = action != null,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
            Text(
                text = value,
                color = accent,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = KaniTheme.colors.muted,
                    fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                )
            }
        }
    }
}

/**
 * The Today card: what Kani suggests, and the one thing that would act on it.
 *
 * Nothing renders when the plan has nothing to say. A titled card containing only its
 * own title reads as content that failed to load.
 */
@Composable
fun HomeTodayCard(
    plan: TodayPlan,
    copy: DashboardCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plan.isEmpty) return
    val summary = resolver.resolve(plan.summary)
    val details = plan.details.map(resolver::resolve).filter { it.isNotBlank() }
    val action = plan.recommendation.action
    val actionLabel = if (plan.recommendation.action == KaniAction.Provider.RequestSync) {
        copy.syncAction
    } else {
        copy.studyAction
    }
    // One merged description, in reading order, because the card is five separate
    // `Text`s that together make one sentence — read individually they are fragments.
    val description = (listOf(copy.todayTitle, summary) + details + listOfNotNull(
        actionLabel.takeIf { action != null },
    )).filter { it.isNotBlank() }.joinToString(DESCRIPTION_SEPARATOR)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HOME_TODAY_TEST_TAG)
            .semantics { contentDescription = description },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = copy.todayTitle,
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            for (detail in details) {
                Text(
                    text = detail,
                    color = KaniTheme.colors.muted,
                    fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                )
            }
            action?.let {
                TextButton(
                    onClick = { dispatch(it) },
                    modifier = Modifier
                        .heightIn(min = KaniUiTokens.MinTouchTarget)
                        .testTag(HOME_TODAY_ACTION_TEST_TAG),
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

/** The deck breakdown, when the host has one. Nothing renders for an empty list. */
@Composable
fun HomeDeckOverview(
    rows: List<dev.bee.kanjianki.presentation.UiText>,
    copy: DashboardCopy,
    resolver: UiTextResolver,
    modifier: Modifier = Modifier,
) {
    val lines = rows.map(resolver::resolve).filter { it.isNotBlank() }
    if (lines.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HOME_DECK_OVERVIEW_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = copy.deckOverviewTitle,
            modifier = Modifier.semantics { heading() },
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
        for (line in lines) {
            Text(
                text = line,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
            )
        }
    }
}

/**
 * A host limitation Home explains rather than hides.
 *
 * The only one so far is a provider that cannot report FSRS memory state, which
 * changes how early reviews are scheduled without disabling anything. There is no
 * button because there is nothing for the user to do about it — but a desktop user
 * whose intervals differ from an Android user's deserves to know why.
 */
@Composable
fun HomeNoticeCard(
    notice: HomeNotice,
    copy: DashboardCopy,
    modifier: Modifier = Modifier,
) {
    val title = copy.noticeTitle(notice)
    val body = copy.noticeBody(notice)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(homeNoticeTestTag(notice))
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
 * The live theme colour for an accent.
 *
 * The mapping lives here rather than in the model because the model must not know
 * which palette is active — that was the flaw in carrying a packed ARGB, which pinned
 * a hue chosen from the legacy palette regardless of the user's theme.
 */
internal fun HomeAccent.color(colors: KaniColors): Color = when (this) {
    HomeAccent.NEUTRAL -> colors.ink
    HomeAccent.DUE -> colors.coral
    HomeAccent.LEARNING -> colors.blue
    HomeAccent.RESTING -> colors.teal
}

private val PRIMARY_ACTION_MIN_HEIGHT = 58.dp
private val METRIC_MIN_HEIGHT = 96.dp
private val STACK_WIDTH = 300.dp
private const val STACK_FONT_SCALE = 1.3f
private const val METRIC_BORDER_ALPHA = 0.45f
private const val DESCRIPTION_SEPARATOR = " · "
