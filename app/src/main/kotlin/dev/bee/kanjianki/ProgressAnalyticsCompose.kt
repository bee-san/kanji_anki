package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.progress.AnalyticsRange
import dev.bee.kanjianki.progress.ProgressAccuracyRetentionState
import dev.bee.kanjianki.progress.ProgressAnalyticsCopy
import dev.bee.kanjianki.progress.ProgressAnalyticsState
import dev.bee.kanjianki.progress.ProgressBarChartState
import dev.bee.kanjianki.progress.ProgressByLevelState
import dev.bee.kanjianki.progress.ProgressCategoryStatusState
import dev.bee.kanjianki.progress.ProgressDistributionChartState
import dev.bee.kanjianki.progress.ProgressDistributionSegmentState
import dev.bee.kanjianki.progress.ProgressFractionMetricState
import dev.bee.kanjianki.progress.ProgressLevelRowState
import dev.bee.kanjianki.progress.ProgressLineChartState
import dev.bee.kanjianki.progress.ProgressMissedKanjiState
import dev.bee.kanjianki.progress.ProgressOverviewState
import dev.bee.kanjianki.progress.ProgressRetentionRowState
import dev.bee.kanjianki.progress.ProgressReviewsAnalyticsState
import dev.bee.kanjianki.progress.ProgressScoreMetricState
import dev.bee.kanjianki.progress.ProgressSeriesState
import dev.bee.kanjianki.progress.ProgressSeriesStyle
import dev.bee.kanjianki.progress.ProgressStreakMetricState
import dev.bee.kanjianki.progress.ProgressSupportNeedState
import dev.bee.kanjianki.progress.ProgressWeaknessInsightsState
import dev.bee.kanjianki.progress.ProgressWeaknessRowState

internal enum class ProgressAnalyticsBottomNavTab(
    val label: String,
    val iconRes: Int,
) {
    Home("Home", R.drawable.ic_home_24),
    Study("Study", R.drawable.ic_study_24),
    Progress("Progress", R.drawable.ic_stats_24),
    Profile("Profile", R.drawable.ic_profile_24),
}

internal fun progressAnalyticsBottomNavItemTestTag(tab: ProgressAnalyticsBottomNavTab): String {
    return "progress-bottom-nav-${tab.name.lowercase()}"
}

internal const val ProgressOverviewHeroSummaryTag = "progress-overview-hero-summary"
internal const val ProgressOverviewMetricsCompactTag = "progress-overview-metrics-compact"
internal const val ProgressDistributionCardCompactLayoutTag = "progress-distribution-card-compact-layout"

private const val ProgressAnalyticsCompactWidthBreakpointDp = 420

internal fun progressAnalyticsOverviewMetricColumns(maxWidth: Dp): Int {
    return if (progressAnalyticsIsCompactWidth(maxWidth)) 2 else 3
}

internal fun progressAnalyticsDistributionUsesStackedLegendLayout(maxWidth: Dp): Boolean {
    return progressAnalyticsIsCompactWidth(maxWidth)
}

internal fun progressAnalyticsOverviewSummaryText(state: ProgressOverviewState): String {
    return buildString {
        append(ProgressAnalyticsCopy.totalReviewsLabel())
        append(' ')
        append(state.totalReviews.valueLabel)
        append(" · ")
        append(ProgressAnalyticsCopy.accuracyLabel())
        append(' ')
        append(state.accuracy.valueLabel)
        append(" · ")
        append(ProgressAnalyticsCopy.streakLabel())
        append(' ')
        append(state.currentStreak.valueLabel)
    }
}

private fun progressAnalyticsIsCompactWidth(maxWidth: Dp): Boolean {
    return maxWidth < ProgressAnalyticsCompactWidthBreakpointDp.dp
}

@Composable
internal fun ProgressAnalyticsDashboardScreen(
    state: ProgressAnalyticsState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compactLayout = progressAnalyticsIsCompactWidth(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (compactLayout) 20.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 12.dp else 16.dp),
        ) {
            ProgressOverviewSection(
                state = state.overview,
                compactLayout = compactLayout,
            )
            ProgressReviewsAnalyticsSection(
                state = state.reviewsAnalytics,
                compactLayout = compactLayout,
            )
            ProgressAccuracyRetentionSection(
                state = state.accuracyRetention,
                compactLayout = compactLayout,
            )
            ProgressByLevelSection(
                state = state.progressByLevel,
                compactLayout = compactLayout,
            )
            ProgressWeaknessInsightsSection(
                state = state.weaknessInsights,
                compactLayout = compactLayout,
            )
        }
    }
}

@Composable
internal fun ProgressAnalyticsBottomNav(
    selectedTab: ProgressAnalyticsBottomNavTab,
    onHome: () -> Unit,
    onStudy: () -> Unit,
    onProgress: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = KaniUiTokens.White,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            ProgressBottomNavItem(
                tab = ProgressAnalyticsBottomNavTab.Home,
                selected = selectedTab == ProgressAnalyticsBottomNavTab.Home,
                onClick = onHome,
            )
            ProgressBottomNavItem(
                tab = ProgressAnalyticsBottomNavTab.Study,
                selected = selectedTab == ProgressAnalyticsBottomNavTab.Study,
                onClick = onStudy,
            )
            ProgressBottomNavItem(
                tab = ProgressAnalyticsBottomNavTab.Progress,
                selected = selectedTab == ProgressAnalyticsBottomNavTab.Progress,
                onClick = onProgress,
            )
            ProgressBottomNavItem(
                tab = ProgressAnalyticsBottomNavTab.Profile,
                selected = selectedTab == ProgressAnalyticsBottomNavTab.Profile,
                onClick = onProfile,
            )
        }
    }
}

@Composable
private fun RowScope.ProgressBottomNavItem(
    tab: ProgressAnalyticsBottomNavTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = ProgressAnalyticsCopy.bottomNavLabel(tab.label),
                modifier = Modifier.size(22.dp),
            )
        },
        label = {
            Text(
                text = ProgressAnalyticsCopy.bottomNavLabel(tab.label),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = KaniUiTokens.Coral,
            selectedTextColor = KaniUiTokens.Coral,
            unselectedIconColor = KaniUiTokens.Muted,
            unselectedTextColor = KaniUiTokens.Muted,
            indicatorColor = KaniTheme.colors.pill,
        ),
        modifier = Modifier
            .testTag(progressAnalyticsBottomNavItemTestTag(tab))
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun ProgressOverviewSection(
    state: ProgressOverviewState,
    compactLayout: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = progressAnalyticsOverviewMetricColumns(maxWidth)

        ProgressSectionCard(
            title = state.title,
            subtitle = state.subtitle,
            compactLayout = compactLayout,
            trailing = {
                ProgressMascotBadge(compactLayout = compactLayout)
            },
        ) {
            ProgressOverviewHeroSummaryCard(
                state = state,
                compactLayout = compactLayout,
            )

            Spacer(modifier = Modifier.height(if (compactLayout) 8.dp else 12.dp))

            ProgressMetricGrid(
                specs = listOf(
                    ProgressMetricSpec(
                        label = ProgressAnalyticsCopy.totalReviewsLabel(),
                        value = state.totalReviews.valueLabel,
                        detail = state.totalReviews.detailLabel,
                        delta = state.totalReviews.deltaLabel,
                        iconRes = R.drawable.ic_stats_24,
                        accent = KaniUiTokens.Coral,
                    ),
                    ProgressMetricSpec(
                        label = ProgressAnalyticsCopy.accuracyLabel(),
                        value = state.accuracy.valueLabel,
                        detail = state.accuracy.detailLabel,
                        delta = state.accuracy.deltaLabel,
                        iconRes = R.drawable.ic_target_24,
                        accent = KaniUiTokens.Teal,
                    ),
                    ProgressMetricSpec(
                        label = ProgressAnalyticsCopy.streakLabel(),
                        value = state.currentStreak.valueLabel,
                        detail = state.currentStreak.detailLabel,
                        delta = ProgressAnalyticsCopy.bestStreakLabel(state.currentStreak.bestDays),
                        iconRes = R.drawable.ic_flame_24,
                        accent = KaniUiTokens.Gold,
                    ),
                    ProgressMetricSpec(
                        label = ProgressAnalyticsCopy.kanjiLearnedLabel(),
                        value = state.kanjiLearned.valueLabel,
                        detail = state.kanjiLearned.detailLabel,
                        delta = state.kanjiLearned.deltaLabel,
                        iconRes = R.drawable.ic_book_24,
                        accent = KaniUiTokens.Blue,
                    ),
                    ProgressMetricSpec(
                        label = ProgressAnalyticsCopy.focusSessionsLabel(),
                        value = state.focusSessions.valueLabel,
                        detail = state.focusSessions.detailLabel,
                        delta = state.focusSessions.deltaLabel,
                        iconRes = R.drawable.ic_sparkle_24,
                        accent = KaniUiTokens.StudyPlum,
                    ),
                    ProgressMetricSpec(
                        label = ProgressAnalyticsCopy.studyTimeLabel(),
                        value = state.studyTime.valueLabel,
                        detail = state.studyTime.detailLabel,
                        delta = state.studyTime.deltaLabel,
                        iconRes = R.drawable.ic_trending_24,
                        accent = KaniUiTokens.Primary,
                    ),
                ),
                columns = columns,
                compactLayout = compactLayout,
                modifier = if (compactLayout) Modifier.testTag(ProgressOverviewMetricsCompactTag) else Modifier,
            )

            Spacer(modifier = Modifier.height(2.dp))
            ProgressLineChartCard(
                chart = state.reviewsOverTime,
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                accentColor = KaniUiTokens.Coral,
                secondaryColor = KaniUiTokens.Teal,
            )

            Spacer(modifier = Modifier.height(2.dp))
            ProgressDistributionChartsRow(state)
        }
    }
}

@Composable
private fun ProgressOverviewHeroSummaryCard(
    state: ProgressOverviewState,
    compactLayout: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProgressOverviewHeroSummaryTag),
        shape = RoundedCornerShape(18.dp),
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactLayout) 10.dp else 14.dp, vertical = if (compactLayout) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = state.subtitle,
                color = KaniUiTokens.Muted,
                fontSize = if (compactLayout) 10.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (compactLayout) 12.sp else 16.sp,
                maxLines = 2,
            )
            Text(
                text = progressAnalyticsOverviewSummaryText(state),
                color = KaniUiTokens.Ink,
                fontSize = if (compactLayout) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (compactLayout) 14.sp else 18.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ProgressDistributionChartsRow(state: ProgressOverviewState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(
                modifier = Modifier.testTag("progress-distribution-charts-stacked"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProgressDistributionCard(
                    chart = state.cardTypeBreakdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress-distribution-card-types"),
                )
                ProgressDistributionCard(
                    chart = state.correctIncorrectBreakdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress-distribution-card-correctness"),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProgressDistributionCard(
                    chart = state.cardTypeBreakdown,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("progress-distribution-card-types"),
                )
                ProgressDistributionCard(
                    chart = state.correctIncorrectBreakdown,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("progress-distribution-card-correctness"),
                )
            }
        }
    }
}

@Composable
private fun ProgressReviewsAnalyticsSection(
    state: ProgressReviewsAnalyticsState,
    compactLayout: Boolean,
) {
    ProgressSectionCard(
        title = state.title,
        compactLayout = compactLayout,
        trailing = {
            ProgressRangeChipRow(
                ranges = state.availableRanges,
                selectedRange = state.selectedRange,
            )
        },
    ) {
        ProgressBarChartCard(
            chart = state.reviewsPerDay,
            accentColor = KaniUiTokens.Coral,
        )

        ProgressMetricGrid(
            columns = 2,
            compactLayout = compactLayout,
            specs = listOf(
                ProgressMetricSpec(
                    label = ProgressAnalyticsCopy.totalReviewsLabel(),
                    value = state.totalReviews.valueLabel,
                    iconRes = R.drawable.ic_stats_24,
                    accent = KaniUiTokens.Coral,
                ),
                ProgressMetricSpec(
                    label = ProgressAnalyticsCopy.averagePerDayLabel(),
                    value = state.averagePerDay.valueLabel,
                    iconRes = R.drawable.ic_trending_24,
                    accent = KaniUiTokens.Teal,
                ),
                ProgressMetricSpec(
                    label = ProgressAnalyticsCopy.correctLabel(),
                    value = state.correct.valueLabel,
                    iconRes = R.drawable.ic_target_24,
                    accent = KaniUiTokens.Blue,
                ),
                ProgressMetricSpec(
                    label = ProgressAnalyticsCopy.incorrectLabel(),
                    value = state.incorrect.valueLabel,
                    iconRes = R.drawable.ic_book_24,
                    accent = KaniUiTokens.Gold,
                ),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressMiniSummaryCard(
                title = ProgressAnalyticsCopy.bestDayCardTitle(),
                value = state.bestDayLabel,
                detail = state.currentStreak.valueLabel,
                accent = KaniUiTokens.Coral,
                compactLayout = compactLayout,
                modifier = Modifier.weight(1f),
            )
            ProgressMiniSummaryCard(
                title = ProgressAnalyticsCopy.streakLabel(),
                value = state.currentStreak.valueLabel,
                detail = state.currentStreak.detailLabel ?: "",
                accent = KaniUiTokens.Teal,
                compactLayout = compactLayout,
                modifier = Modifier.weight(1f),
            )
        }

        ProgressTipCard(text = state.tip)
    }
}

@Composable
private fun ProgressAccuracyRetentionSection(
    state: ProgressAccuracyRetentionState,
    compactLayout: Boolean,
) {
    ProgressSectionCard(
        title = state.title,
        compactLayout = compactLayout,
        trailing = {
            ProgressRangeChipRow(
                ranges = state.availableRanges,
                selectedRange = state.selectedRange,
            )
        },
    ) {
        ProgressLineChartCard(
            chart = state.accuracyTrend,
            selectedRange = state.selectedRange,
            accentColor = KaniUiTokens.Coral,
            secondaryColor = KaniUiTokens.Teal,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.retentionByCardType.forEach { row ->
                ProgressRetentionRow(row = row)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.categoryStatuses.forEach { status ->
                ProgressChip(
                    text = "${status.label}: ${status.status}",
                    accent = when (ProgressAnalyticsCopy.statusKey(status.status)) {
                        "excellent" -> KaniUiTokens.Teal
                        "great" -> KaniUiTokens.Blue
                        "good" -> KaniUiTokens.Gold
                        else -> KaniUiTokens.Coral
                    },
                    selected = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            text = state.retentionSummary,
            color = KaniUiTokens.Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ProgressByLevelSection(
    state: ProgressByLevelState,
    compactLayout: Boolean,
) {
    ProgressSectionCard(
        title = state.title,
        compactLayout = compactLayout,
        trailing = {
            ProgressChip(
                text = state.selectedFilterLabel,
                accent = KaniUiTokens.Coral,
                selected = true,
            )
        },
    ) {
        ProgressFractionCard(state.overallLearned, compactLayout = compactLayout)
        ProgressLineChartCard(
            chart = state.cumulativeProgress,
            selectedRange = state.cumulativeProgress.selectedRange,
            accentColor = KaniUiTokens.Blue,
            secondaryColor = KaniUiTokens.Coral,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.levelRows.forEach { row ->
                ProgressLevelRow(row = row)
            }
        }
    }
}

@Composable
private fun ProgressWeaknessInsightsSection(
    state: ProgressWeaknessInsightsState,
    compactLayout: Boolean,
) {
    ProgressSectionCard(
        title = state.title,
        compactLayout = compactLayout,
    ) {
        ProgressFocusScoreCard(state.focusScore)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.weaknessRows.forEach { row ->
                ProgressWeaknessRow(row = row)
            }
        }

        Text(
            text = ProgressAnalyticsCopy.mostMissedKanjiTitle(),
            color = KaniUiTokens.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        ProgressMissedKanjiGrid(items = state.mostMissedKanji)

        Text(
            text = ProgressAnalyticsCopy.supportNeededTitle(),
            color = KaniUiTokens.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.supportNeeded.forEach { item ->
                ProgressSupportNeedRow(item = item)
            }
        }
    }
}

@Composable
private fun ProgressSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    compactLayout: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = KaniUiTokens.PanelFill,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactLayout) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 10.dp else 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = KaniUiTokens.Ink,
                        fontSize = if (compactLayout) 19.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = if (compactLayout) 21.sp else 24.sp,
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(if (compactLayout) 2.dp else 4.dp))
                        Text(
                            text = subtitle,
                            color = KaniUiTokens.Muted,
                            fontSize = if (compactLayout) 10.sp else 12.sp,
                            lineHeight = if (compactLayout) 13.sp else 16.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                }
            }
            content()
        }
    }
}

@Composable
private fun ProgressMascotBadge(compactLayout: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (compactLayout) 52.dp else 72.dp)
                .clip(RoundedCornerShape(if (compactLayout) 18.dp else 24.dp))
                .background(KaniTheme.colors.track),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "🦀",
                fontSize = if (compactLayout) 24.sp else 31.sp,
            )
        }
        if (!compactLayout) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Kani",
                color = KaniUiTokens.Coral,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProgressMetricGrid(
    modifier: Modifier = Modifier,
    specs: List<ProgressMetricSpec>,
    columns: Int = 3,
    compactLayout: Boolean = false,
) {
    val rowSpacing = if (compactLayout || columns <= 2) 6.dp else 10.dp
    val rows = rememberChunkedRows(specs, columns)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(rowSpacing), modifier = Modifier.fillMaxWidth()) {
                row.forEach { spec ->
                    ProgressMetricCard(
                        spec = spec,
                        compactLayout = compactLayout,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size < columns) {
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressMetricCard(
    spec: ProgressMetricSpec,
    modifier: Modifier = Modifier,
    compactLayout: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactLayout) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 4.dp else 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactLayout) 28.dp else 34.dp)
                    .clip(CircleShape)
                    .background(spec.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(spec.iconRes),
                    contentDescription = spec.label,
                    tint = spec.accent,
                    modifier = Modifier.size(if (compactLayout) 14.dp else 18.dp),
                )
            }
            Text(
                text = spec.label,
                color = KaniUiTokens.Muted,
                fontSize = if (compactLayout) 10.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = spec.value,
                color = KaniUiTokens.Ink,
                fontSize = if (compactLayout) 16.sp else 20.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = if (compactLayout) 18.sp else 22.sp,
                maxLines = 2,
            )
            if (!spec.delta.isNullOrBlank()) {
                Text(
                    text = spec.delta,
                    color = spec.accent,
                    fontSize = if (compactLayout) 9.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = if (compactLayout) 11.sp else 14.sp,
                )
            }
            if (!spec.detail.isNullOrBlank()) {
                Text(
                    text = spec.detail,
                    color = KaniUiTokens.Muted,
                    fontSize = if (compactLayout) 9.sp else 11.sp,
                    lineHeight = if (compactLayout) 11.sp else 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ProgressMiniSummaryCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    compactLayout: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactLayout) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 2.dp else 4.dp),
        ) {
            Text(
                text = title,
                color = KaniUiTokens.Muted,
                fontSize = if (compactLayout) 9.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                color = KaniUiTokens.Ink,
                fontSize = if (compactLayout) 14.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = accent,
                    fontSize = if (compactLayout) 9.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ProgressTipCard(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = KaniUiTokens.Coral,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProgressFractionCard(
    state: ProgressFractionMetricState,
    compactLayout: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactLayout) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ProgressAnalyticsCopy.allLevelsLearnedLabel(),
                    color = KaniUiTokens.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.valueLabel ?: "${state.value} / ${state.total}",
                    color = KaniUiTokens.Ink,
                    fontSize = if (compactLayout) 24.sp else 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = if (compactLayout) 26.sp else 28.sp,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = KaniUiTokens.Coral,
            ) {
                Text(
                    text = "${state.percent}%",
                    modifier = Modifier.padding(
                        horizontal = if (compactLayout) 10.dp else 12.dp,
                        vertical = if (compactLayout) 6.dp else 8.dp,
                    ),
                    color = KaniUiTokens.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ProgressLineChartCard(
    chart: ProgressLineChartState,
    selectedRange: AnalyticsRange? = chart.selectedRange,
    accentColor: Color,
    secondaryColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressLineChartHeader(chart, selectedRange, accentColor)
            ProgressLineChartCard(chart, accentColor, secondaryColor)
            ProgressLineChartXAxis(chart.xAxisLabels)
            ProgressLineChartLegend(chart.series, accentColor, secondaryColor)
        }
    }
}

@Composable
private fun ProgressLineChartHeader(
    chart: ProgressLineChartState,
    selectedRange: AnalyticsRange?,
    accentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chart.title,
                color = KaniUiTokens.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            ProgressLineChartTooltip(chart.tooltipLabel)
        }
        if (selectedRange != null) {
            ProgressChip(
                text = ProgressAnalyticsCopy.rangeLabel(selectedRange),
                accent = accentColor,
                selected = true,
            )
        }
    }
}

@Composable
private fun ProgressLineChartTooltip(label: String?) {
    if (label.isNullOrBlank()) return
    Text(
        text = label,
        color = KaniUiTokens.Muted,
        fontSize = 11.sp,
    )
}

@Composable
private fun ProgressLineChartCard(
    chart: ProgressLineChartState,
    accentColor: Color,
    secondaryColor: Color,
) {
    val lineColors = rememberProgressLineChartColors(accentColor, secondaryColor)
    val gridLineColor = KaniTheme.colors.track
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = chart.accessibilitySummary },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressLineChartYAxis(chart.yAxisLabels)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    drawProgressLineChart(chart, lineColors, gridLineColor)
                }
            }
        }
    }
}

@Composable
private fun ProgressLineChartYAxis(labels: List<String>) {
    Column(
        modifier = Modifier
            .width(34.dp)
            .height(140.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.reversed().forEach { label ->
            Text(
                text = label,
                color = KaniUiTokens.Muted,
                fontSize = 10.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProgressLineChartXAxis(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { label ->
            Text(
                text = label,
                color = KaniUiTokens.Muted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressLineChartLegend(
    series: List<ProgressSeriesState>,
    accentColor: Color,
    secondaryColor: Color,
) {
    if (series.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            series.take(2).forEachIndexed { index, item ->
                ProgressLegendPill(
                    text = item.label,
                    accent = if (index == 0) accentColor else secondaryColor,
                    selected = index == 0,
                )
            }
        }
    }
}

private fun DrawScope.drawProgressLineChart(
    chart: ProgressLineChartState,
    lineColors: List<Color>,
    gridLineColor: Color,
) {
    val values = chart.series.flatMap { it.values }
    val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    drawProgressGridLines(chart.yAxisLabels.size, gridLineColor)
    chart.series.forEachIndexed { index, series ->
        drawProgressSeries(
            series = series,
            color = lineColors.getOrElse(index) { lineColors.first() },
            maxValue = maxValue,
        )
    }
}

private fun DrawScope.drawProgressGridLines(labelCount: Int, gridLineColor: Color) {
    val plotHeight = size.height
    val stepCount = (labelCount - 1).coerceAtLeast(1)
    repeat(labelCount) { index ->
        val y = plotHeight - (plotHeight / stepCount) * index
        drawLine(
            color = gridLineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2f,
        )
    }
}

private fun DrawScope.drawProgressSeries(
    series: ProgressSeriesState,
    color: Color,
    maxValue: Int,
) {
    if (series.values.size < 2) return
    val points = progressSeriesPoints(series.values, maxValue)
    drawProgressSegments(points, color, series.style)
    drawProgressPoints(points, color, series.style)
}

private fun DrawScope.progressSeriesPoints(values: List<Int>, maxValue: Int): List<Offset> {
    val valueRange = maxValue.coerceAtLeast(1)
    val stepX = size.width / (values.size - 1)
    return values.mapIndexed { index, value ->
        val normalized = value.coerceAtLeast(0).toFloat() / valueRange
        Offset(
            x = stepX * index,
            y = size.height - (normalized * (size.height - 10f)) - 4f,
        )
    }
}

private fun DrawScope.drawProgressSegments(
    points: List<Offset>,
    color: Color,
    style: ProgressSeriesStyle,
) {
    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color.copy(alpha = progressSeriesAlpha(style)),
            start = start,
            end = end,
            strokeWidth = progressSeriesStrokeWidth(style),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawProgressPoints(
    points: List<Offset>,
    color: Color,
    style: ProgressSeriesStyle,
) {
    points.forEach { point ->
        drawCircle(
            color = color,
            radius = progressSeriesPointRadius(style),
            center = point,
        )
    }
}

private fun progressSeriesAlpha(style: ProgressSeriesStyle): Float =
    if (style == ProgressSeriesStyle.DASHED) 0.45f else 1f

private fun progressSeriesStrokeWidth(style: ProgressSeriesStyle): Float =
    if (style == ProgressSeriesStyle.DASHED) 4.5f else 5.5f

private fun progressSeriesPointRadius(style: ProgressSeriesStyle): Float =
    if (style == ProgressSeriesStyle.DASHED) 3.5f else 4.8f

@Composable
private fun ProgressBarChartCard(
    chart: ProgressBarChartState,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chart.title,
                        color = KaniUiTokens.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = chart.accessibilitySummary,
                        color = KaniUiTokens.Muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                    )
                }
                if (chart.selectedRange != null) {
                    ProgressChip(
                        text = ProgressAnalyticsCopy.rangeLabel(chart.selectedRange),
                        accent = accentColor,
                        selected = true,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val maxValue = (chart.values.maxOrNull() ?: 1).coerceAtLeast(1)
                chart.labels.zip(chart.values).forEach { (label, value) ->
                    val heightFraction = value.toFloat() / maxValue.toFloat()
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = value.toString(),
                            color = KaniUiTokens.Ink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height((88.dp * heightFraction).coerceAtLeast(14.dp))
                                .clip(RoundedCornerShape(999.dp))
                                .background(accentColor),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
                            color = KaniUiTokens.Muted,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressDistributionCard(
    chart: ProgressDistributionChartState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val stackedLayout = progressAnalyticsDistributionUsesStackedLegendLayout(maxWidth)
        val colors = rememberProgressDistributionColors()

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = KaniUiTokens.White,
            border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = chart.title,
                    color = KaniUiTokens.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (stackedLayout) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ProgressDistributionCardCompactLayoutTag),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProgressDonutChart(
                                segments = chart.segments,
                                colors = colors,
                                modifier = Modifier
                                    .size(96.dp)
                                    .semantics { contentDescription = chart.accessibilitySummary },
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chart.segments.forEachIndexed { index, segment ->
                                ProgressLegendRow(
                                    segment = segment,
                                    color = colors[index % colors.size],
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressDonutChart(
                            segments = chart.segments,
                            colors = colors,
                            modifier = Modifier
                                .size(104.dp)
                                .semantics { contentDescription = chart.accessibilitySummary },
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chart.segments.forEachIndexed { index, segment ->
                                ProgressLegendRow(
                                    segment = segment,
                                    color = colors[index % colors.size],
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressDonutChart(
    segments: List<ProgressDistributionSegmentState>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val holeColor = KaniUiTokens.White
    Canvas(modifier = modifier) {
        val total = segments.sumOf { it.value }.coerceAtLeast(1)
        val strokeWidth = size.minDimension * 0.16f
        var startAngle = -90f
        segments.forEachIndexed { index, segment ->
            val sweep = (segment.value.toFloat() / total.toFloat()) * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            startAngle += sweep
        }
        drawCircle(
            color = holeColor,
            radius = size.minDimension * 0.28f,
            center = center,
        )
    }
}

@Composable
private fun ProgressLegendRow(
    segment: ProgressDistributionSegmentState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = segment.label,
                color = KaniUiTokens.Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${segment.value} · ${segment.percent}%",
                color = KaniUiTokens.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressRangeChipRow(
    ranges: List<AnalyticsRange>,
    selectedRange: AnalyticsRange,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ranges.forEach { range ->
            ProgressChip(
                text = ProgressAnalyticsCopy.rangeLabel(range),
                accent = KaniUiTokens.Coral,
                selected = range == selectedRange,
            )
        }
    }
}

@Composable
private fun ProgressChip(
    text: String,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent else KaniUiTokens.White,
        border = BorderStroke(1.dp, if (selected) accent else KaniUiTokens.SubtleButtonBorder),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selected) KaniUiTokens.White else accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressLegendPill(
    text: String,
    accent: Color,
    selected: Boolean,
) {
    ProgressChip(text = text, accent = accent, selected = selected)
}

@Composable
private fun ProgressRetentionRow(row: ProgressRetentionRowState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = row.label,
                color = KaniUiTokens.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.valueLabel,
                color = KaniUiTokens.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KaniTheme.colors.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.percent / 100f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(when (ProgressAnalyticsCopy.cardTypeKey(row.label)) {
                        "meaning" -> KaniUiTokens.Teal
                        "reading" -> KaniUiTokens.Blue
                        "writing" -> KaniUiTokens.Gold
                        else -> KaniUiTokens.Coral
                    }),
            )
        }
    }
}

@Composable
private fun ProgressLevelRow(row: ProgressLevelRowState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = row.level,
                color = KaniUiTokens.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${row.learned}/${row.total} · ${row.percent}%",
                color = KaniUiTokens.Muted,
                fontSize = 11.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KaniTheme.colors.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.percent / 100f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(when (row.level) {
                        "N5" -> KaniUiTokens.Coral
                        "N4" -> KaniUiTokens.Teal
                        "N3" -> KaniUiTokens.Blue
                        "N2" -> KaniUiTokens.Gold
                        else -> KaniUiTokens.StudyPlum
                    }),
            )
        }
    }
}

@Composable
private fun ProgressFocusScoreCard(state: ProgressScoreMetricState) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .semantics { contentDescription = state.accessibilityLabel },
                contentAlignment = Alignment.Center,
            ) {
                val gaugeColor = KaniUiTokens.Coral
                val gaugeTrackColor = KaniTheme.colors.track
                Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                    val stroke = size.minDimension * 0.14f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    drawArc(
                        color = gaugeTrackColor,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = gaugeColor,
                        startAngle = 180f,
                        sweepAngle = (state.value / state.total.toFloat()).coerceIn(0f, 1f) * 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.value.toString(),
                        color = KaniUiTokens.Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = ProgressAnalyticsCopy.ofTotalLabel(state.total),
                        color = KaniUiTokens.Muted,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = ProgressAnalyticsCopy.focusScoreLabel(),
                    color = KaniUiTokens.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.status,
                    color = KaniUiTokens.Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 22.sp,
                )
                Text(
                    text = ProgressAnalyticsCopy.focusScoreDetail(),
                    color = KaniUiTokens.Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Text(
                    text = state.accessibilityLabel,
                    color = KaniUiTokens.Coral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ProgressWeaknessRow(row: ProgressWeaknessRowState) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.label,
                        color = KaniUiTokens.Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = ProgressAnalyticsCopy.missesLabel(row.missedCount),
                        color = KaniUiTokens.Muted,
                        fontSize = 10.sp,
                    )
                }
                ProgressChip(
                    text = row.severity,
                    accent = when (ProgressAnalyticsCopy.severityKey(row.severity)) {
                        "high" -> KaniUiTokens.Coral
                        "medium" -> KaniUiTokens.Gold
                        else -> KaniUiTokens.Teal
                    },
                    selected = false,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(KaniTheme.colors.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(row.accuracyPercent / 100f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(when (ProgressAnalyticsCopy.severityKey(row.severity)) {
                            "high" -> KaniUiTokens.Coral
                            "medium" -> KaniUiTokens.Gold
                            else -> KaniUiTokens.Teal
                        }),
                )
            }
        }
    }
}

@Composable
private fun ProgressMissedKanjiGrid(items: List<ProgressMissedKanjiState>) {
    val rows = rememberChunkedRows(items, 3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    ProgressMissedKanjiChip(item = item, modifier = Modifier.weight(1f))
                }
                if (row.size < 3) {
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressMissedKanjiChip(
    item: ProgressMissedKanjiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = item.kanji,
                color = KaniUiTokens.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = ProgressAnalyticsCopy.missesLabel(item.misses),
                color = KaniUiTokens.Coral,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProgressSupportNeedRow(item: ProgressSupportNeedState) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    color = KaniUiTokens.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (item.targetLabel.isNotBlank()) {
                    Text(
                        text = item.targetLabel,
                        color = KaniUiTokens.Muted,
                        fontSize = 10.sp,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = KaniTheme.colors.pill,
            ) {
                Text(
                    text = item.count.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = KaniUiTokens.Coral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private data class ProgressMetricSpec(
    val label: String,
    val value: String,
    val iconRes: Int,
    val accent: Color,
    val delta: String? = null,
    val detail: String? = null,
)

@Composable
private fun <T> rememberChunkedRows(items: List<T>, chunkSize: Int): List<List<T>> {
    return remember(items, chunkSize) {
        items.chunked(chunkSize)
    }
}

@Composable
private fun rememberProgressDistributionColors(): List<Color> {
    val coral = KaniUiTokens.Coral
    val teal = KaniUiTokens.Teal
    val blue = KaniUiTokens.Blue
    val gold = KaniUiTokens.Gold
    val plum = KaniUiTokens.StudyPlum
    return remember(coral, teal, blue, gold, plum) {
        listOf(coral, teal, blue, gold, plum)
    }
}

@Composable
private fun rememberProgressLineChartColors(accentColor: Color, secondaryColor: Color): List<Color> {
    val blue = KaniUiTokens.Blue
    val gold = KaniUiTokens.Gold
    return remember(accentColor, secondaryColor, blue, gold) {
        listOf(accentColor, secondaryColor, blue, gold)
    }
}
