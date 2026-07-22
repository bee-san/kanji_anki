package dev.bee.kanjianki.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.fillMaxHeight
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import dev.bee.kanjianki.MainActivity
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.core.WidgetTextCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class KaniWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    companion object {
        private val COMPACT_SIZE = DpSize(250.dp, 72.dp)
        private val EXPANDED_SIZE = DpSize(250.dp, 130.dp)

        /**
         * Very wide placements keep the same stable stacked text-and-strip
         * composition while giving the summary additional horizontal room.
         */
        internal val WIDE_SIZE = DpSize(340.dp, 130.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, EXPANDED_SIZE, WIDE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val prefs = getAppWidgetState<Preferences>(context, id)
        val options = KaniWidgetInstanceOptions.fromStorageValues(
            prefs[stringPreferencesKey(KaniWidgetInstanceOptions.STYLE_PREF_KEY)],
            prefs[stringPreferencesKey(KaniWidgetInstanceOptions.THEME_OVERRIDE_PREF_KEY)],
        )
        if (options.style == KaniWidgetStyle.HEATMAP) {
            val activitySnapshot = withContext(ioDispatcher) {
                ActivityWidgetSnapshotLoader.load(context, nowMillis)
            }
            KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, nowMillis)
            provideContent {
                LegacyActivityWidgetContent(activitySnapshot, options)
            }
        } else {
            val snapshot = withContext(ioDispatcher) {
                StudyWidgetSnapshotLoader.load(context, nowMillis)
            }
            KaniWidgetBoundaryAlarm.scheduleStudyBoundary(
                context,
                nowMillis,
                snapshot.nextUsefulAtMillis,
            )
            provideContent {
                KaniWidgetContent(snapshot, options)
            }
        }
    }
}

internal data class OverviewActivityStripMetrics(
    val cellSizeDp: Int,
    val gapDp: Int,
) {
    fun widthDp(dayCount: Int): Int = if (dayCount <= 0) {
        0
    } else {
        dayCount * cellSizeDp + (dayCount - 1) * gapDp
    }
}

internal fun overviewActivityStripMetrics() = OverviewActivityStripMetrics(
    cellSizeDp = 9,
    gapDp = 2,
)

internal data class OverviewActionMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val fontSp: Int,
)

internal fun overviewActionMetrics() = OverviewActionMetrics(
    widthDp = 80,
    heightDp = 56,
    fontSp = 13,
)

@Composable
internal fun KaniWidgetContent(
    snapshot: KaniWidgetSnapshot,
    options: KaniWidgetInstanceOptions = KaniWidgetInstanceOptions(),
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val isExpanded = size.height >= 120.dp
    val fontScale = context.resources.configuration.fontScale
    val veryLargeFont = fontScale >= 1.8f
    val showTertiary = isExpanded && fontScale < 1.3f
    val copy = widgetCopy(snapshot, isExpanded)
    val visibleCopy = overviewVisibleCopy(snapshot, copy, veryLargeFont)
    val palette = KaniWidgetPalette.forChoice(options.resolveTheme(snapshot.themeChoice))
    val homeAction = actionStartActivity(kaniWidgetHomeIntent(context))
    val studyAction = actionStartActivity(kaniWidgetLaunchIntent(context, snapshot))
    val description = WidgetTextCopy.widgetDescription(copy.title, copy.body)
    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(palette.background.toProvider())
        .cornerRadius(16.dp)
        .padding(if (isExpanded) 12.dp else 8.dp)
    Row(
        modifier = cardModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(homeAction)
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetTextBlock(
                copy = copy,
                title = visibleCopy.title,
                palette = palette,
                visibility = WidgetTextBlockVisibility(
                    showBody = fontScale < 2f,
                    showBrand = showTertiary,
                    showExtra = showTertiary,
                    showStrip = showTertiary,
                ),
                dayCounts = snapshot.last7DayCounts,
            )
        }
        OverviewAction(visibleCopy.action, studyAction, palette)
    }
}

@Composable
internal fun LegacyActivityWidgetContent(
    snapshot: ActivityWidgetSnapshot,
    options: KaniWidgetInstanceOptions,
) {
    if (snapshot.state == ActivityWidgetState.NOT_SET_UP || snapshot.state == ActivityWidgetState.ERROR) {
        KaniWidgetContent(
            snapshot = KaniWidgetSnapshot(
                state = if (snapshot.state == ActivityWidgetState.ERROR) {
                    KaniWidgetState.ERROR
                } else {
                    KaniWidgetState.NOT_SET_UP
                },
                themeChoice = snapshot.themeChoice,
            ),
            options = options.copy(style = KaniWidgetStyle.DUE_CARD),
        )
        return
    }
    val palette = KaniWidgetPalette.forChoice(options.resolveTheme(snapshot.themeChoice))
    val statsAction = actionStartActivity(kaniWidgetStatsIntent(LocalContext.current))
    HeatmapContent(
        snapshot = snapshot,
        palette = palette,
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background.toProvider())
            .cornerRadius(16.dp)
            .clickable(statsAction)
            .padding(12.dp)
            .semantics { contentDescription = activityHeaderLine(snapshot) },
    )
}

@Composable
private fun WidgetTextBlock(
    copy: KaniWidgetCopy,
    title: String,
    palette: KaniWidgetPalette,
    visibility: WidgetTextBlockVisibility,
    dayCounts: List<Int> = emptyList(),
) {
    if (visibility.showBrand) {
        Text(
            text = WidgetTextCopy.appName(),
            style = TextStyle(
                color = palette.primaryText.toProvider(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(3.dp))
    }
    Text(
        text = title,
        style = TextStyle(
            color = palette.ink.toProvider(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    if (visibility.showBody) {
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = copy.body,
            style = TextStyle(
                color = palette.muted.toProvider(),
                fontSize = 13.sp,
            ),
        )
    }
    if (visibility.showExtra && copy.extraLine.isNotEmpty()) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = copy.extraLine,
            style = TextStyle(
                color = palette.muted.toProvider(),
                fontSize = 12.sp,
            ),
        )
    }
    if (visibility.showStrip && dayCounts.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        ActivityStrip(dayCounts, palette)
    }
}

private data class WidgetTextBlockVisibility(
    val showBody: Boolean,
    val showBrand: Boolean,
    val showExtra: Boolean,
    val showStrip: Boolean,
)

@Composable
private fun OverviewAction(
    label: String,
    action: Action,
    palette: KaniWidgetPalette,
) {
    val metrics = overviewActionMetrics()
    Box(
        modifier = GlanceModifier
            .width(metrics.widthDp.dp)
            .height(metrics.heightDp.dp)
            .background(palette.primary.toProvider())
            .cornerRadius(14.dp)
            .clickable(action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = palette.onPrimary.toProvider(),
                fontSize = metrics.fontSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun ActivityStrip(dayCounts: List<Int>, palette: KaniWidgetPalette) {
    val maxCount = dayCounts.maxOrNull() ?: 1
    val metrics = overviewActivityStripMetrics()
    Row(
        modifier = GlanceModifier.padding(vertical = 2.dp),
    ) {
        dayCounts.forEachIndexed { index, count ->
            if (index > 0) Spacer(GlanceModifier.width(metrics.gapDp.dp))
            Box(
                modifier = GlanceModifier
                    .size(metrics.cellSizeDp.dp)
                    .background(heatCellRole(count, maxCount, palette).toProvider()),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

/**
 * Shared activity-cell color: empty days use the widget background and active
 * days use the same three discrete semantic levels as the Activity widget.
 */
internal fun heatCellRole(count: Int, maxCount: Int, palette: KaniWidgetPalette): KaniWidgetColorRole =
    palette.activityHeat(activityIntensity(count, maxCount))

@Composable
private fun HeatmapContent(
    snapshot: ActivityWidgetSnapshot,
    palette: KaniWidgetPalette,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = activityHeaderLine(snapshot),
            style = TextStyle(
                color = palette.ink.toProvider(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(6.dp))
        HeatmapGrid(snapshot.last35DayCounts, palette)
    }
}

/** Header for the legacy Activity layout without advertising due Study work. */
internal fun activityHeaderLine(snapshot: ActivityWidgetSnapshot): String =
    "${WidgetTextCopy.appName()} · ${snapshot.last35DayTotal} reviews · " +
        WidgetTextCopy.streakLabel(snapshot.streakDays)

/** Header for the original study snapshot, retained for pure compatibility tests. */
internal fun heatmapHeaderLine(snapshot: KaniWidgetSnapshot): String {
    val status = if (snapshot.state == KaniWidgetState.DUE_NOW) {
        WidgetTextCopy.dueCountLabel(snapshot.dueCount)
    } else {
        WidgetTextCopy.nothingDueTitle()
    }
    return "${WidgetTextCopy.appName()} · $status · ${WidgetTextCopy.streakLabel(snapshot.streakDays)}"
}

@Composable
private fun HeatmapGrid(dayCounts: List<Int>, palette: KaniWidgetPalette) {
    val cells = dayCounts.takeLast(ActivityWidgetSnapshotLoader.HISTORY_DAYS)
    val maxCount = cells.maxOrNull() ?: 1
    Column {
        cells.chunked(StudyWidgetSnapshotLoader.STRIP_DAYS).forEachIndexed { rowIndex, week ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(2.dp))
            HeatmapWeekRow(week, maxCount, palette)
        }
    }
}

@Composable
private fun HeatmapWeekRow(week: List<Int>, maxCount: Int, palette: KaniWidgetPalette) {
    Row {
        week.forEachIndexed { index, count ->
            if (index > 0) Spacer(GlanceModifier.width(2.dp))
            Box(
                modifier = GlanceModifier
                    .size(13.dp)
                    .background(heatCellRole(count, maxCount, palette).toProvider()),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

/** Maps a palette role to a Glance day/night [ColorProvider]. */
private fun KaniWidgetColorRole.toProvider(): ColorProvider = ColorProvider(day = day, night = night)

/**
 * Reuse the visible Kani task when the widget is tapped. Without these flags,
 * every tap starts another MainActivity and Back can reveal a stale duplicate
 * screen underneath it instead of returning to the previous app.
 *
 * Title/body taps open Home; only the action row ("Study now") deep-links
 * straight to Study via [kaniWidgetLaunchIntent].
 *
 * A widget "Sync" affordance was evaluated and deliberately not added: the
 * manual sync path is designed around an in-app confirmation step, and the
 * repaired-tagging write-back must stay manual-confirm-only, so no sync entry
 * point is reachable from the widget.
 */
internal fun kaniWidgetHomeIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

/** Action-row tap: deep-links straight to Study when reviews are due. */
internal fun kaniWidgetLaunchIntent(context: Context, snapshot: KaniWidgetSnapshot): Intent =
    kaniWidgetHomeIntent(context).apply {
        if (snapshot.state == KaniWidgetState.DUE_NOW) {
            putExtra(MainActivityBase.EXTRA_OPEN_STUDY, true)
        }
    }

/** Focus-kanji card tap: opens the selected glyph's existing in-app detail route. */
internal fun kaniFocusDetailIntent(context: Context, kanji: String): Intent =
    kaniWidgetHomeIntent(context).apply {
        putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, kanji)
    }

/** Heatmap-card tap: opens the stats screen that hosts the full heatmap. */
internal fun kaniWidgetStatsIntent(context: Context): Intent =
    kaniWidgetHomeIntent(context).apply {
        putExtra(MainActivityBase.EXTRA_OPEN_STATS, true)
    }

internal data class KaniWidgetCopy(
    val title: String,
    val body: String,
    val action: String,
    /** Optional third line rendered only when non-empty (expanded tier). */
    val extraLine: String = "",
)

internal data class KaniWidgetVisibleCopy(
    val title: String,
    val action: String,
)

internal fun overviewVisibleCopy(
    snapshot: KaniWidgetSnapshot,
    copy: KaniWidgetCopy,
    veryLargeFont: Boolean,
): KaniWidgetVisibleCopy {
    if (!veryLargeFont) return KaniWidgetVisibleCopy(copy.title, copy.action)
    val title = when (snapshot.state) {
        KaniWidgetState.DUE_NOW -> WidgetTextCopy.visualCountLabel(snapshot.dueCount)
        KaniWidgetState.NOTHING_DUE -> "0"
        KaniWidgetState.NOT_SET_UP -> "—"
        KaniWidgetState.ERROR -> "!"
    }
    val action = if (snapshot.state == KaniWidgetState.DUE_NOW) {
        WidgetTextCopy.studyLabel()
    } else {
        WidgetTextCopy.appName()
    }
    return KaniWidgetVisibleCopy(title, action)
}

/**
 * Selects the widget's text per state and size tier. Kept as a pure function
 * so tier-dependent content (count split, due-later line, best streak) is
 * unit-testable without a Glance host.
 */
internal fun widgetCopy(snapshot: KaniWidgetSnapshot, isExpanded: Boolean): KaniWidgetCopy = when (snapshot.state) {
    KaniWidgetState.NOT_SET_UP -> KaniWidgetCopy(
        WidgetTextCopy.notSetUpTitle(),
        WidgetTextCopy.notSetUpBody(),
        WidgetTextCopy.openKaniLabel(),
    )
    KaniWidgetState.ERROR -> KaniWidgetCopy(
        WidgetTextCopy.errorTitle(),
        WidgetTextCopy.errorBody(),
        WidgetTextCopy.openKaniLabel(),
    )
    KaniWidgetState.NOTHING_DUE -> KaniWidgetCopy(
        WidgetTextCopy.nothingDueTitle(),
        WidgetTextCopy.nothingDueBody(snapshot.streakDays, snapshot.nextUsefulAtMillis),
        WidgetTextCopy.openKaniLabel(),
        extraLine = if (isExpanded) WidgetTextCopy.bestStreakLabel(snapshot.bestStreakDays) else "",
    )
    KaniWidgetState.DUE_NOW -> {
        val reviewCount = (snapshot.dueCount - snapshot.newDueCount).coerceAtLeast(0)
        val title = if (isExpanded && snapshot.newDueCount > 0 && reviewCount > 0) {
            WidgetTextCopy.dueSplitLabel(reviewCount, snapshot.newDueCount)
        } else {
            WidgetTextCopy.dueCountLabel(snapshot.dueCount)
        }
        KaniWidgetCopy(
            title,
            WidgetTextCopy.streakLabel(snapshot.streakDays),
            WidgetTextCopy.studyNowLabel(),
            extraLine = if (isExpanded) {
                WidgetTextCopy.dueLaterLabel(snapshot.dueLaterCount, snapshot.dueLaterByMillis)
            } else {
                ""
            },
        )
    }
}
