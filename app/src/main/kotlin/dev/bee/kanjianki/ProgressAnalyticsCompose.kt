package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.progress.AnalyticsRange
import dev.bee.kanjianki.progress.ProgressAccuracyRetentionState
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

@Composable
internal fun ProgressAnalyticsDashboardScreen(
    state: ProgressAnalyticsState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgressOverviewSection(state.overview)
        ProgressReviewsAnalyticsSection(state.reviewsAnalytics)
        ProgressAccuracyRetentionSection(state.accuracyRetention)
        ProgressByLevelSection(state.progressByLevel)
        ProgressWeaknessInsightsSection(state.weaknessInsights)
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
                contentDescription = tab.label,
                modifier = Modifier.size(22.dp),
            )
        },
        label = {
            Text(
                text = tab.label,
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
            indicatorColor = Color(0xFFFFE5EF),
        ),
        modifier = Modifier
            .testTag(progressAnalyticsBottomNavItemTestTag(tab))
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun ProgressOverviewSection(state: ProgressOverviewState) {
    ProgressSectionCard(
        title = state.title,
        subtitle = state.subtitle,
        trailing = {
            ProgressMascotBadge()
        },
    ) {
        ProgressMetricGrid(
            specs = listOf(
                ProgressMetricSpec(
                    label = "Total reviews",
                    value = state.totalReviews.valueLabel,
                    detail = state.totalReviews.detailLabel,
                    delta = state.totalReviews.deltaLabel,
                    iconRes = R.drawable.ic_stats_24,
                    accent = KaniUiTokens.Coral,
                ),
                ProgressMetricSpec(
                    label = "Accuracy",
                    value = state.accuracy.valueLabel,
                    detail = state.accuracy.detailLabel,
                    delta = state.accuracy.deltaLabel,
                    iconRes = R.drawable.ic_target_24,
                    accent = KaniUiTokens.Teal,
                ),
                ProgressMetricSpec(
                    label = "Streak",
                    value = state.currentStreak.valueLabel,
                    detail = state.currentStreak.detailLabel,
                    delta = "Best ${state.currentStreak.bestDays} days",
                    iconRes = R.drawable.ic_flame_24,
                    accent = KaniUiTokens.Gold,
                ),
                ProgressMetricSpec(
                    label = "Kanji learned",
                    value = state.kanjiLearned.valueLabel,
                    detail = state.kanjiLearned.detailLabel,
                    delta = state.kanjiLearned.deltaLabel,
                    iconRes = R.drawable.ic_book_24,
                    accent = KaniUiTokens.Blue,
                ),
                ProgressMetricSpec(
                    label = "Focus sessions",
                    value = state.focusSessions.valueLabel,
                    detail = state.focusSessions.detailLabel,
                    delta = state.focusSessions.deltaLabel,
                    iconRes = R.drawable.ic_sparkle_24,
                    accent = KaniUiTokens.StudyPlum,
                ),
                ProgressMetricSpec(
                    label = "Study time",
                    value = state.studyTime.valueLabel,
                    detail = state.studyTime.detailLabel,
                    delta = state.studyTime.deltaLabel,
                    iconRes = R.drawable.ic_trending_24,
                    accent = KaniUiTokens.Primary,
                ),
            ),
        )

        Spacer(modifier = Modifier.height(2.dp))
        ProgressLineChartCard(
            chart = state.reviewsOverTime,
            selectedRange = AnalyticsRange.THIRTY_DAYS,
            accentColor = KaniUiTokens.Coral,
            secondaryColor = KaniUiTokens.Teal,
        )

        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressDistributionCard(
                chart = state.cardTypeBreakdown,
                modifier = Modifier.weight(1f),
            )
            ProgressDistributionCard(
                chart = state.correctIncorrectBreakdown,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressReviewsAnalyticsSection(state: ProgressReviewsAnalyticsState) {
    ProgressSectionCard(
        title = state.title,
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
            specs = listOf(
                ProgressMetricSpec(
                    label = "Total reviews",
                    value = state.totalReviews.valueLabel,
                    iconRes = R.drawable.ic_stats_24,
                    accent = KaniUiTokens.Coral,
                ),
                ProgressMetricSpec(
                    label = "Average / day",
                    value = state.averagePerDay.valueLabel,
                    iconRes = R.drawable.ic_trending_24,
                    accent = KaniUiTokens.Teal,
                ),
                ProgressMetricSpec(
                    label = "Correct",
                    value = state.correct.valueLabel,
                    iconRes = R.drawable.ic_target_24,
                    accent = KaniUiTokens.Blue,
                ),
                ProgressMetricSpec(
                    label = "Incorrect",
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
                title = "Best day",
                value = state.bestDayLabel,
                detail = state.currentStreak.valueLabel,
                accent = KaniUiTokens.Coral,
                modifier = Modifier.weight(1f),
            )
            ProgressMiniSummaryCard(
                title = "Streak",
                value = state.currentStreak.valueLabel,
                detail = state.currentStreak.detailLabel ?: "",
                accent = KaniUiTokens.Teal,
                modifier = Modifier.weight(1f),
            )
        }

        ProgressTipCard(text = state.tip)
    }
}

@Composable
private fun ProgressAccuracyRetentionSection(state: ProgressAccuracyRetentionState) {
    ProgressSectionCard(
        title = state.title,
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
                    accent = when (status.status) {
                        "Excellent" -> KaniUiTokens.Teal
                        "Great" -> KaniUiTokens.Blue
                        "Good" -> KaniUiTokens.Gold
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
private fun ProgressByLevelSection(state: ProgressByLevelState) {
    ProgressSectionCard(
        title = state.title,
        trailing = {
            ProgressChip(
                text = state.selectedFilterLabel,
                accent = KaniUiTokens.Coral,
                selected = true,
            )
        },
    ) {
        ProgressFractionCard(state.overallLearned)
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
private fun ProgressWeaknessInsightsSection(state: ProgressWeaknessInsightsState) {
    ProgressSectionCard(title = state.title) {
        ProgressFocusScoreCard(state.focusScore)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.weaknessRows.forEach { row ->
                ProgressWeaknessRow(row = row)
            }
        }

        Text(
            text = "Most missed kanji",
            color = KaniUiTokens.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        ProgressMissedKanjiGrid(items = state.mostMissedKanji)

        Text(
            text = "Support needed",
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
    subtitle: String? = null,
    modifier: Modifier = Modifier,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = KaniUiTokens.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp,
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            color = KaniUiTokens.Muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                }
            }
            content()
        }
    }
}

@Composable
private fun ProgressMascotBadge() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFFD8E4)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "🦀",
                fontSize = 31.sp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Kani",
            color = KaniUiTokens.Coral,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProgressMetricGrid(
    specs: List<ProgressMetricSpec>,
    columns: Int = 3,
) {
    val rows = specs.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { spec ->
                    ProgressMetricCard(spec = spec, modifier = Modifier.weight(1f))
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(spec.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(spec.iconRes),
                    contentDescription = spec.label,
                    tint = spec.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = spec.label,
                color = KaniUiTokens.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = spec.value,
                color = KaniUiTokens.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 22.sp,
                maxLines = 2,
            )
            if (!spec.delta.isNullOrBlank()) {
                Text(
                    text = spec.delta,
                    color = spec.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 14.sp,
                )
            }
            if (!spec.detail.isNullOrBlank()) {
                Text(
                    text = spec.detail,
                    color = KaniUiTokens.Muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = KaniUiTokens.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                color = KaniUiTokens.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = accent,
                    fontSize = 11.sp,
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
        color = Color(0xFFFFF2F7),
        border = BorderStroke(1.dp, Color(0xFFFFD4E2)),
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
private fun ProgressFractionCard(state: ProgressFractionMetricState) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All levels learned",
                    color = KaniUiTokens.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.valueLabel ?: "${state.value} / ${state.total}",
                    color = KaniUiTokens.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = KaniUiTokens.Coral,
            ) {
                Text(
                    text = "${state.percent}%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                    if (!chart.tooltipLabel.isNullOrBlank()) {
                        Text(
                            text = chart.tooltipLabel,
                            color = KaniUiTokens.Muted,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (selectedRange != null) {
                    ProgressChip(
                        text = selectedRange.label,
                        accent = accentColor,
                        selected = true,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics { contentDescription = chart.accessibilitySummary },
            ) {
                val leftAxisWidth = 34.dp
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .width(leftAxisWidth)
                            .height(140.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        chart.yAxisLabels.reversed().forEach { label ->
                            Text(
                                text = label,
                                color = KaniUiTokens.Muted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp),
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                            val plotWidth = size.width
                            val plotHeight = size.height
                            val allValues = chart.series.flatMap { it.values }
                            val maxValue = (allValues.maxOrNull() ?: 1).coerceAtLeast(1)
                            val minValue = 0
                            val lineColors = listOf(accentColor, secondaryColor, KaniUiTokens.Blue, KaniUiTokens.Gold)
                            repeat(chart.yAxisLabels.size) { index ->
                                val y = plotHeight - (plotHeight / (chart.yAxisLabels.size - 1).coerceAtLeast(1)) * index
                                drawLine(
                                    color = Color(0xFFF0E7EF),
                                    start = Offset(0f, y),
                                    end = Offset(plotWidth, y),
                                    strokeWidth = 1.2f,
                                )
                            }
                            chart.series.forEachIndexed { seriesIndex, series ->
                                val seriesColor = lineColors.getOrElse(seriesIndex) { accentColor }
                                val values = series.values
                                if (values.size < 2) return@forEachIndexed
                                val stepX = plotWidth / (values.size - 1)
                                val points = values.mapIndexed { index, value ->
                                    val normalized = (value - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1)
                                    Offset(
                                        x = stepX * index,
                                        y = plotHeight - (normalized * (plotHeight - 10f)) - 4f,
                                    )
                                }
                                for (index in 0 until points.size - 1) {
                                    val start = points[index]
                                    val end = points[index + 1]
                                    drawLine(
                                        color = seriesColor.copy(alpha = if (series.style == ProgressSeriesStyle.DASHED) 0.45f else 1f),
                                        start = start,
                                        end = end,
                                        strokeWidth = if (series.style == ProgressSeriesStyle.DASHED) 4.5f else 5.5f,
                                        cap = StrokeCap.Round,
                                    )
                                }
                                points.forEach { point ->
                                    drawCircle(
                                        color = seriesColor,
                                        radius = if (series.style == ProgressSeriesStyle.DASHED) 3.5f else 4.8f,
                                        center = point,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                chart.xAxisLabels.forEach { label ->
                    Text(
                        text = label,
                        color = KaniUiTokens.Muted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!chart.series.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chart.series.take(2).forEachIndexed { index, series ->
                        ProgressLegendPill(
                            text = series.label,
                            accent = if (index == 0) accentColor else secondaryColor,
                            selected = index == 0,
                        )
                    }
                }
            }
        }
    }
}

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
                        text = chart.selectedRange.label,
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
    Surface(
        modifier = modifier,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressDonutChart(
                    segments = chart.segments,
                    modifier = Modifier
                        .size(104.dp)
                        .semantics { contentDescription = chart.accessibilitySummary },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chart.segments.forEachIndexed { index, segment ->
                        ProgressLegendRow(
                            segment = segment,
                            color = donutColors[index % donutColors.size],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressDonutChart(
    segments: List<ProgressDistributionSegmentState>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val total = segments.sumOf { it.value }.coerceAtLeast(1)
        val strokeWidth = size.minDimension * 0.16f
        var startAngle = -90f
        val colors = donutColors
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
            color = KaniUiTokens.White,
            radius = size.minDimension * 0.28f,
            center = center,
        )
    }
}

@Composable
private fun ProgressLegendRow(
    segment: ProgressDistributionSegmentState,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column {
            Text(
                text = segment.label,
                color = KaniUiTokens.Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${segment.value} · ${segment.percent}%",
                color = KaniUiTokens.Muted,
                fontSize = 10.sp,
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
                text = range.label,
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
                .background(Color(0xFFF4ECF2)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.percent / 100f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(when (row.label) {
                        "Meaning" -> KaniUiTokens.Teal
                        "Reading" -> KaniUiTokens.Blue
                        "Writing" -> KaniUiTokens.Gold
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
                .background(Color(0xFFF4ECF2)),
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
        color = Color(0xFFFFF8FB),
        border = BorderStroke(1.dp, Color(0xFFFFD5E3)),
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
                Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                    val stroke = size.minDimension * 0.14f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    drawArc(
                        color = Color(0xFFF1DDE7),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = KaniUiTokens.Coral,
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
                        text = "of ${state.total}",
                        color = KaniUiTokens.Muted,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Focus score",
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
                    text = "Kanji that need extra practice are highlighted here.",
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
                        text = "${row.missedCount} misses",
                        color = KaniUiTokens.Muted,
                        fontSize = 10.sp,
                    )
                }
                ProgressChip(
                    text = row.severity,
                    accent = when (row.severity) {
                        "High" -> KaniUiTokens.Coral
                        "Medium" -> KaniUiTokens.Gold
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
                    .background(Color(0xFFF4ECF2)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(row.accuracyPercent / 100f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(when (row.severity) {
                            "High" -> KaniUiTokens.Coral
                            "Medium" -> KaniUiTokens.Gold
                            else -> KaniUiTokens.Teal
                        }),
                )
            }
        }
    }
}

@Composable
private fun ProgressMissedKanjiGrid(items: List<ProgressMissedKanjiState>) {
    val rows = items.chunked(3)
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
        color = Color(0xFFFFF8FB),
        border = BorderStroke(1.dp, Color(0xFFFFD5E3)),
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
                text = "${item.misses} misses",
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
                color = Color(0xFFFFEEF5),
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

private val donutColors = listOf(
    KaniUiTokens.Coral,
    KaniUiTokens.Teal,
    KaniUiTokens.Blue,
    KaniUiTokens.Gold,
    KaniUiTokens.StudyPlum,
)
