package dev.bee.kanjianki.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.bee.kanjianki.core.WidgetTextCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ActivityWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    companion object {
        internal val RESPONSIVE_SIZES = setOf(
            DpSize(120.dp, 72.dp),
            DpSize(120.dp, 120.dp),
            DpSize(250.dp, 130.dp),
        )
    }

    override val sizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val snapshot = withContext(ioDispatcher) {
            ActivityWidgetSnapshotLoader.load(context, nowMillis)
        }
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, nowMillis)
        provideContent { ActivityWidgetContent(snapshot) }
    }
}

internal enum class ActivityWidgetTier {
    COMPACT,
    REGULAR,
    WIDE,
}

internal enum class ActivityIntensity {
    EMPTY,
    LOW,
    MEDIUM,
    HIGH,
}

internal data class ActivityCell(
    val count: Int,
    val intensity: ActivityIntensity,
    val isToday: Boolean,
)

internal data class ActivityWidgetPresentation(
    val title: String,
    val streak: String,
    val bestStreak: String,
    val action: String,
    val contentDescription: String,
    val destination: KaniWidgetDestination,
    val cells: List<ActivityCell>,
)

internal data class ActivityWidgetLayout(
    val showBestStreak: Boolean,
    val showStreak: Boolean,
    val showGrid: Boolean,
    val useSevenDayGrid: Boolean,
    val showAction: Boolean,
    val stackAction: Boolean,
    val useCompactHero: Boolean,
    val titleFontSp: Float,
    val actionFontSp: Float,
    val supportFontSp: Float,
)

internal data class ActivityWidgetVisibleCopy(
    val title: String,
    val action: String,
)

internal fun activityWidgetLayout(
    tier: ActivityWidgetTier,
    fontScale: Float,
) = ActivityWidgetLayout(
    showBestStreak = tier != ActivityWidgetTier.COMPACT && fontScale < 1.3f,
    showStreak = fontScale < 1.3f,
    showGrid = fontScale < 2f,
    useSevenDayGrid = tier == ActivityWidgetTier.COMPACT || fontScale >= 1.3f,
    showAction = tier != ActivityWidgetTier.COMPACT || fontScale >= 1.3f,
    stackAction = tier != ActivityWidgetTier.WIDE,
    useCompactHero = tier == ActivityWidgetTier.REGULAR || fontScale >= 1.3f,
    titleFontSp = if (tier == ActivityWidgetTier.COMPACT) 14f else 16f,
    actionFontSp = 13f,
    supportFontSp = 12f,
)

internal fun activityWidgetTier(widthDp: Float, heightDp: Float): ActivityWidgetTier = when {
    heightDp < 96f -> ActivityWidgetTier.COMPACT
    widthDp < 230f -> ActivityWidgetTier.REGULAR
    else -> ActivityWidgetTier.WIDE
}

internal fun activityIntensity(count: Int, maximum: Int): ActivityIntensity {
    if (count <= 0) return ActivityIntensity.EMPTY
    if (maximum <= 0) return ActivityIntensity.LOW
    val ratio = count.toDouble() / maximum.toDouble()
    return when {
        ratio <= 0.33 -> ActivityIntensity.LOW
        ratio <= 0.66 -> ActivityIntensity.MEDIUM
        else -> ActivityIntensity.HIGH
    }
}

internal fun activityWidgetPresentation(
    snapshot: ActivityWidgetSnapshot,
    tier: ActivityWidgetTier,
): ActivityWidgetPresentation {
    val destination = when (snapshot.state) {
        ActivityWidgetState.HISTORY,
        ActivityWidgetState.NO_HISTORY,
        -> KaniWidgetDestination.STATS
        ActivityWidgetState.NOT_SET_UP,
        ActivityWidgetState.ERROR,
        -> KaniWidgetDestination.HOME
    }
    val action = if (destination == KaniWidgetDestination.STATS) {
        WidgetTextCopy.openStatsLabel()
    } else {
        WidgetTextCopy.openKaniLabel()
    }
    val historyCounts = when (tier) {
        ActivityWidgetTier.COMPACT -> snapshot.last35DayCounts.takeLast(COMPACT_HISTORY_DAYS)
        ActivityWidgetTier.REGULAR,
        ActivityWidgetTier.WIDE,
        -> snapshot.last35DayCounts.takeLast(ActivityWidgetSnapshotLoader.HISTORY_DAYS)
    }
    val maximum = historyCounts.maxOrNull() ?: 0
    val cells = if (
        snapshot.state == ActivityWidgetState.HISTORY ||
        snapshot.state == ActivityWidgetState.NO_HISTORY
    ) {
        historyCounts.mapIndexed { index, count ->
            ActivityCell(
                count = count.coerceAtLeast(0),
                intensity = activityIntensity(count, maximum),
                isToday = index == historyCounts.lastIndex,
            )
        }
    } else {
        emptyList()
    }

    return when (snapshot.state) {
        ActivityWidgetState.HISTORY -> {
            val total = if (tier == ActivityWidgetTier.COMPACT) {
                snapshot.last7DayTotal
            } else {
                snapshot.last35DayTotal
            }
            val days = if (tier == ActivityWidgetTier.COMPACT) {
                COMPACT_HISTORY_DAYS
            } else {
                ActivityWidgetSnapshotLoader.HISTORY_DAYS
            }
            ActivityWidgetPresentation(
                title = WidgetTextCopy.activityPeriodLabel(total, days),
                streak = WidgetTextCopy.streakLabel(snapshot.streakDays),
                bestStreak = WidgetTextCopy.bestStreakLabel(snapshot.bestStreakDays),
                action = action,
                contentDescription = WidgetTextCopy.activityDescription(
                    reviewCount = total,
                    days = days,
                    reviewsToday = snapshot.reviewsToday,
                    streakDays = snapshot.streakDays,
                    bestStreakDays = snapshot.bestStreakDays,
                    action = action,
                ),
                destination = destination,
                cells = cells,
            )
        }
        ActivityWidgetState.NO_HISTORY -> ActivityWidgetPresentation(
            title = WidgetTextCopy.noActivityTitle(),
            streak = WidgetTextCopy.noActivityBody(),
            bestStreak = "",
            action = action,
            contentDescription = WidgetTextCopy.activityStateDescription(
                WidgetTextCopy.noActivityTitle(),
                WidgetTextCopy.noActivityBody(),
                action,
            ),
            destination = destination,
            cells = cells,
        )
        ActivityWidgetState.NOT_SET_UP -> ActivityWidgetPresentation(
            title = WidgetTextCopy.notSetUpTitle(),
            streak = WidgetTextCopy.notSetUpBody(),
            bestStreak = "",
            action = action,
            contentDescription = WidgetTextCopy.activityStateDescription(
                WidgetTextCopy.notSetUpTitle(),
                WidgetTextCopy.notSetUpBody(),
                action,
            ),
            destination = destination,
            cells = emptyList(),
        )
        ActivityWidgetState.ERROR -> ActivityWidgetPresentation(
            title = WidgetTextCopy.errorTitle(),
            streak = WidgetTextCopy.errorBody(),
            bestStreak = "",
            action = action,
            contentDescription = WidgetTextCopy.activityStateDescription(
                WidgetTextCopy.errorTitle(),
                WidgetTextCopy.errorBody(),
                action,
            ),
            destination = destination,
            cells = emptyList(),
        )
    }
}

internal fun activityWidgetVisibleCopy(
    snapshot: ActivityWidgetSnapshot,
    presentation: ActivityWidgetPresentation,
    layout: ActivityWidgetLayout,
): ActivityWidgetVisibleCopy {
    val action = when {
        layout.useCompactHero && presentation.destination == KaniWidgetDestination.HOME -> WidgetTextCopy.appName()
        presentation.destination == KaniWidgetDestination.STATS -> WidgetTextCopy.statsLabel()
        else -> presentation.action
    }
    val title = if (layout.useCompactHero) {
        compactActivityTitle(snapshot, layout)
    } else {
        standardActivityTitle(snapshot, presentation)
    }
    return ActivityWidgetVisibleCopy(title, action)
}

private fun compactActivityTitle(
    snapshot: ActivityWidgetSnapshot,
    layout: ActivityWidgetLayout,
): String = when (snapshot.state) {
    ActivityWidgetState.HISTORY -> if (layout.useSevenDayGrid && layout.showGrid) {
        WidgetTextCopy.reviewCountLabel(snapshot.last7DayTotal)
    } else {
        WidgetTextCopy.reviewCountLabel(snapshot.last35DayTotal)
    }
    ActivityWidgetState.NO_HISTORY -> WidgetTextCopy.reviewCountLabel(0)
    ActivityWidgetState.NOT_SET_UP -> "—"
    ActivityWidgetState.ERROR -> "!"
}

private fun standardActivityTitle(
    snapshot: ActivityWidgetSnapshot,
    presentation: ActivityWidgetPresentation,
): String {
    if (snapshot.state != ActivityWidgetState.HISTORY) return presentation.title
    if (presentation.cells.size == ActivityWidgetSnapshotLoader.HISTORY_DAYS) return presentation.title
    val days = if (presentation.cells.size == COMPACT_HISTORY_DAYS) {
        COMPACT_HISTORY_DAYS
    } else {
        ActivityWidgetSnapshotLoader.HISTORY_DAYS
    }
    val total = if (days == COMPACT_HISTORY_DAYS) snapshot.last7DayTotal else snapshot.last35DayTotal
    return WidgetTextCopy.activityPeriodShortLabel(total, days)
}

internal fun activityWidgetVisibleCells(
    presentation: ActivityWidgetPresentation,
    layout: ActivityWidgetLayout,
): List<ActivityCell> = when {
    !layout.showGrid -> emptyList()
    layout.useSevenDayGrid -> normalizeActivityCells(
        presentation.cells.takeLast(COMPACT_HISTORY_DAYS),
    )
    else -> presentation.cells
}

private fun normalizeActivityCells(cells: List<ActivityCell>): List<ActivityCell> {
    val maximum = cells.maxOfOrNull { it.count } ?: 0
    return cells.map { cell ->
        cell.copy(intensity = activityIntensity(cell.count, maximum))
    }
}

internal fun activityWidgetContentDescription(
    snapshot: ActivityWidgetSnapshot,
    presentation: ActivityWidgetPresentation,
    layout: ActivityWidgetLayout,
): String {
    if (snapshot.state != ActivityWidgetState.HISTORY) return presentation.contentDescription
    val useSevenDayPeriod = layout.showGrid && layout.useSevenDayGrid
    val total = if (useSevenDayPeriod) snapshot.last7DayTotal else snapshot.last35DayTotal
    val days = if (useSevenDayPeriod) COMPACT_HISTORY_DAYS else ActivityWidgetSnapshotLoader.HISTORY_DAYS
    return WidgetTextCopy.activityDescription(
        reviewCount = total,
        days = days,
        reviewsToday = snapshot.reviewsToday,
        streakDays = snapshot.streakDays,
        bestStreakDays = snapshot.bestStreakDays,
        action = presentation.action,
    )
}

@Composable
internal fun ActivityWidgetContent(snapshot: ActivityWidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val tier = activityWidgetTier(size.width.value, size.height.value)
    val layout = activityWidgetLayout(tier, context.resources.configuration.fontScale)
    val presentation = activityWidgetPresentation(snapshot, tier)
    val palette = KaniWidgetPalette.forChoice(snapshot.themeChoice)
    val launchAction = actionStartActivity(kaniActivityLaunchIntent(context, snapshot))
    val visibleCopy = activityWidgetVisibleCopy(snapshot, presentation, layout)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background.toGlanceColor())
            .cornerRadius(16.dp)
            .clickable(launchAction)
            .semantics {
                contentDescription = activityWidgetContentDescription(snapshot, presentation, layout)
            }
            .padding(if (tier == ActivityWidgetTier.COMPACT) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (layout.stackAction) {
            Text(
                text = visibleCopy.title,
                style = TextStyle(
                    color = palette.ink.toGlanceColor(),
                    fontSize = layout.titleFontSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (layout.showAction) {
                Text(
                    text = visibleCopy.action,
                    style = TextStyle(
                        color = palette.primaryText.toGlanceColor(),
                        fontSize = layout.actionFontSp.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = visibleCopy.title,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = palette.ink.toGlanceColor(),
                        fontSize = layout.titleFontSp.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (layout.showAction) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = visibleCopy.action,
                        style = TextStyle(
                            color = palette.primaryText.toGlanceColor(),
                            fontSize = layout.actionFontSp.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        ActivityWidgetBody(presentation, layout, tier, palette)
    }
}

@Composable
private fun ActivityWidgetBody(
    presentation: ActivityWidgetPresentation,
    layout: ActivityWidgetLayout,
    tier: ActivityWidgetTier,
    palette: KaniWidgetPalette,
) {
    val visibleCells = activityWidgetVisibleCells(presentation, layout)
    if (presentation.cells.isEmpty() && layout.showStreak) {
        Text(
            text = presentation.streak,
            style = TextStyle(
                color = palette.muted.toGlanceColor(),
                fontSize = layout.supportFontSp.sp,
            ),
            maxLines = 2,
        )
    } else if (visibleCells.isNotEmpty()) {
        if (layout.showStreak) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presentation.streak,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = palette.ink.toGlanceColor(),
                        fontSize = layout.supportFontSp.sp,
                    ),
                    maxLines = 1,
                )
                if (layout.showBestStreak) {
                    Spacer(modifier = GlanceModifier.width(ACTIVITY_METADATA_GAP_DP.dp))
                    Text(
                        text = presentation.bestStreak,
                        style = TextStyle(
                            color = palette.muted.toGlanceColor(),
                            fontSize = layout.supportFontSp.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
        }
        ActivityGrid(
            cells = visibleCells,
            tier = if (layout.useSevenDayGrid) ActivityWidgetTier.COMPACT else tier,
            palette = palette,
        )
    }
}

@Composable
private fun ActivityGrid(
    cells: List<ActivityCell>,
    tier: ActivityWidgetTier,
    palette: KaniWidgetPalette,
) {
    val cellSize = activityGridCellSizeDp(tier).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        cells.chunked(GRID_COLUMNS).forEach { rowCells ->
            Row {
                rowCells.forEach { cell ->
                    val outer = if (cell.isToday) palette.todayOutline else palette.activityHeat(cell.intensity)
                    Box(
                        modifier = GlanceModifier
                            .width(cellSize)
                            .height(cellSize)
                            .padding(1.dp)
                            .background(outer.toGlanceColor())
                            .cornerRadius(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell.isToday) {
                            Box(
                                modifier = GlanceModifier
                                    .width(cellSize - 2.dp)
                                    .height(cellSize - 2.dp)
                                    .background(palette.background.toGlanceColor())
                                    .cornerRadius(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .width(cellSize - 4.dp)
                                        .height(cellSize - 4.dp)
                                        .background(palette.activityHeat(cell.intensity).toGlanceColor())
                                        .cornerRadius(1.dp),
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun activityGridCellSizeDp(tier: ActivityWidgetTier): Int = when (tier) {
    ActivityWidgetTier.COMPACT -> 12
    ActivityWidgetTier.REGULAR,
    ActivityWidgetTier.WIDE,
    -> 14
}

internal fun kaniActivityLaunchIntent(
    context: Context,
    snapshot: ActivityWidgetSnapshot,
) = when (snapshot.state) {
    ActivityWidgetState.HISTORY,
    ActivityWidgetState.NO_HISTORY,
    -> kaniWidgetStatsIntent(context)
    ActivityWidgetState.NOT_SET_UP,
    ActivityWidgetState.ERROR,
    -> kaniWidgetHomeIntent(context)
}

private const val COMPACT_HISTORY_DAYS = 7
private const val GRID_COLUMNS = 7
internal const val ACTIVITY_METADATA_GAP_DP = 8
