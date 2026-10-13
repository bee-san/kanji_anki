package dev.bee.kanjianki.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
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
import dev.bee.kanjianki.platform.KaniLaunchExtras
import dev.bee.kanjianki.core.WidgetTextCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KaniWidget(
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

internal data class OverviewActivityBarMetrics(
    val trackHeightDp: Int,
    val barWidthDp: Int,
    val emptyHeightDp: Int,
)

internal fun overviewActivityBarMetrics() = OverviewActivityBarMetrics(
    trackHeightDp = 24,
    barWidthDp = 8,
    emptyHeightDp = 4,
)

internal fun overviewActivityBarHeight(count: Int, maximum: Int): Int {
    val metrics = overviewActivityBarMetrics()
    if (count <= 0 || maximum <= 0) return metrics.emptyHeightDp
    val ratio = count.coerceAtMost(maximum).toFloat() / maximum
    return metrics.emptyHeightDp +
        ((metrics.trackHeightDp - metrics.emptyHeightDp) * ratio).toInt()
}

internal data class OverviewActionMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val iconSizeDp: Int,
)

internal fun overviewActionMetrics() = OverviewActionMetrics(
    widthDp = 48,
    heightDp = 48,
    iconSizeDp = 20,
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
    val showDetails = isExpanded && fontScale < 1.3f
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
            if (isExpanded) {
                ExpandedOverview(
                    snapshot = snapshot,
                    copy = copy,
                    title = visibleCopy.title,
                    palette = palette,
                    showBody = fontScale < 2f,
                    showDetails = showDetails,
                )
            } else {
                CompactOverview(snapshot, palette)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
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
private fun CompactOverview(
    snapshot: KaniWidgetSnapshot,
    palette: KaniWidgetPalette,
) {
    val presentation = overviewCompactPresentation(snapshot)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetMascot(40)
        Spacer(GlanceModifier.width(8.dp))
        Column(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = presentation.hero,
                style = TextStyle(
                    color = palette.ink.toProvider(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = presentation.status,
                style = TextStyle(
                    color = palette.primaryText.toProvider(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

internal data class OverviewCompactPresentation(
    val hero: String,
    val status: String,
)

internal fun overviewCompactPresentation(snapshot: KaniWidgetSnapshot): OverviewCompactPresentation =
    when (snapshot.state) {
        KaniWidgetState.DUE_NOW -> OverviewCompactPresentation(
            hero = WidgetTextCopy.visualCountLabel(snapshot.dueCount),
            status = WidgetTextCopy.quickDueStatus(),
        )
        KaniWidgetState.NOTHING_DUE -> OverviewCompactPresentation(
            hero = "0",
            status = WidgetTextCopy.quickCaughtUpStatus(),
        )
        KaniWidgetState.NOT_SET_UP -> OverviewCompactPresentation(
            hero = "—",
            status = WidgetTextCopy.quickSetupStatus(),
        )
        KaniWidgetState.ERROR -> OverviewCompactPresentation(
            hero = "!",
            status = WidgetTextCopy.quickErrorStatus(),
        )
    }

@Composable
private fun ExpandedOverview(
    snapshot: KaniWidgetSnapshot,
    copy: KaniWidgetCopy,
    title: String,
    palette: KaniWidgetPalette,
    showBody: Boolean,
    showDetails: Boolean,
) {
    if (showDetails) {
        OverviewHeader(snapshot, palette)
        Spacer(GlanceModifier.height(3.dp))
    }
    Text(
        text = title,
        style = TextStyle(
            color = palette.ink.toProvider(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    if (showBody) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = overviewSupportLine(snapshot, copy),
            style = TextStyle(
                color = palette.muted.toProvider(),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
    if (showDetails && snapshot.last7DayCounts.isNotEmpty()) {
        Spacer(GlanceModifier.height(6.dp))
        OverviewActivityBars(snapshot.last7DayCounts, palette)
    }
}

internal fun overviewSupportLine(snapshot: KaniWidgetSnapshot, copy: KaniWidgetCopy): String =
    if (snapshot.state == KaniWidgetState.DUE_NOW && copy.extraLine.isNotEmpty()) {
        copy.extraLine
    } else {
        copy.body
    }

@Composable
private fun OverviewHeader(
    snapshot: KaniWidgetSnapshot,
    palette: KaniWidgetPalette,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetMascot(22)
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = WidgetTextCopy.appName(),
            style = TextStyle(
                color = palette.primaryText.toProvider(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (snapshot.state != KaniWidgetState.NOT_SET_UP && snapshot.state != KaniWidgetState.ERROR) {
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_flame_24),
                contentDescription = null,
                modifier = GlanceModifier.size(13.dp),
                colorFilter = ColorFilter.tint(palette.primaryText.toProvider()),
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = WidgetTextCopy.streakLabel(snapshot.streakDays),
                style = TextStyle(
                    color = palette.primaryText.toProvider(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WidgetMascot(sizeDp: Int) {
    Image(
        provider = ImageProvider(R.drawable.kani_widget_mascot),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.dp),
        contentScale = ContentScale.Fit,
    )
}

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
        Image(
            provider = ImageProvider(R.drawable.ic_arrow_forward_24),
            contentDescription = null,
            modifier = GlanceModifier.size(metrics.iconSizeDp.dp),
            colorFilter = ColorFilter.tint(palette.onPrimary.toProvider()),
        )
    }
}

@Composable
private fun OverviewActivityBars(dayCounts: List<Int>, palette: KaniWidgetPalette) {
    val visibleCounts = dayCounts.takeLast(StudyWidgetSnapshotLoader.STRIP_DAYS)
    val maximum = visibleCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val metrics = overviewActivityBarMetrics()
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(metrics.trackHeightDp.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        visibleCounts.forEachIndexed { index, count ->
            Box(
                modifier = GlanceModifier.defaultWeight().height(metrics.trackHeightDp.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val role = when {
                    count <= 0 -> palette.track
                    index == visibleCounts.lastIndex -> palette.primary
                    else -> heatCellRole(count, maximum, palette)
                }
                Box(
                    modifier = GlanceModifier
                        .width(metrics.barWidthDp.dp)
                        .height(overviewActivityBarHeight(count, maximum).dp)
                        .background(role.toProvider())
                        .cornerRadius(4.dp),
                ) {}
            }
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
fun kaniWidgetHomeIntent(context: Context): Intent {
    // The launcher is resolved from the package manager rather than named as a class, and
    // that is a module-boundary decision rather than a style one: `:widget` is extracted to
    // its own Android module (Goal 199's last step), and a widget that referenced the host
    // activity's type would depend on `:app` while `:app` depends on it — a cycle. Asking
    // the system which activity answers LAUNCHER keeps the dependency one-way.
    //
    // Still an *explicit* component intent, not an implicit one. `setPackage` plus the
    // resolved component means the tap is delivered to Kani's own launcher and cannot be
    // intercepted, which an action-only intent could not promise. The flags then reuse the
    // visible task; without them every tap would stack another copy of the app and Back
    // would reveal a stale duplicate underneath.
    val launcher = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(context.packageName)
    val component = context.packageManager
        .resolveActivity(launcher, 0)
        ?.activityInfo
        ?.let { ComponentName(it.packageName, it.name) }
    return Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(context.packageName)
        // A null component would mean the app has no launcher at all, which the manifest
        // test forbids; the package-scoped intent still reaches Kani if it ever happened.
        component?.let { setComponent(it) }
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/** Action-row tap: deep-links straight to Study when reviews are due. */
fun kaniWidgetLaunchIntent(context: Context, snapshot: KaniWidgetSnapshot): Intent =
    kaniWidgetHomeIntent(context).apply {
        if (snapshot.state == KaniWidgetState.DUE_NOW) {
            putExtra(KaniLaunchExtras.EXTRA_OPEN_STUDY, true)
        }
    }

/** Focus-kanji card tap: opens the selected glyph's existing in-app detail route. */
fun kaniFocusDetailIntent(context: Context, kanji: String): Intent =
    kaniWidgetHomeIntent(context).apply {
        putExtra(KaniLaunchExtras.EXTRA_OPEN_KANJI_DETAIL, kanji)
    }

/** Heatmap-card tap: opens the stats screen that hosts the full heatmap. */
fun kaniWidgetStatsIntent(context: Context): Intent =
    kaniWidgetHomeIntent(context).apply {
        putExtra(KaniLaunchExtras.EXTRA_OPEN_STATS, true)
    }

data class KaniWidgetCopy(
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
fun widgetCopy(snapshot: KaniWidgetSnapshot, isExpanded: Boolean): KaniWidgetCopy = when (snapshot.state) {
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
