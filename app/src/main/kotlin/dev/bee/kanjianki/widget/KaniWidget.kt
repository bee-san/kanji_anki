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
import androidx.glance.currentState
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
import androidx.glance.text.TextStyle
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
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
         * Very wide placements (tablet/foldable rows) lay the text and the
         * 7-day activity strip side by side instead of stacking them.
         */
        internal val WIDE_SIZE = DpSize(340.dp, 130.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, EXPANDED_SIZE, WIDE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(ioDispatcher) {
            KaniWidgetSnapshotLoader.load(context)
        }
        // Event-driven boundary refresh: if useful work arrives within the
        // hourly-fallback window, one inexact alarm re-renders on time.
        KaniWidgetBoundaryAlarm.scheduleIfUseful(
            context,
            System.currentTimeMillis(),
            snapshot.nextUsefulAtMillis,
        )
        provideContent {
            val prefs = currentState<Preferences>()
            val options = KaniWidgetInstanceOptions.fromStorageValues(
                prefs[stringPreferencesKey(KaniWidgetInstanceOptions.STYLE_PREF_KEY)],
                prefs[stringPreferencesKey(KaniWidgetInstanceOptions.THEME_OVERRIDE_PREF_KEY)],
            )
            KaniWidgetContent(snapshot, options)
        }
    }
}

@Composable
private fun KaniWidgetContent(
    snapshot: KaniWidgetSnapshot,
    options: KaniWidgetInstanceOptions = KaniWidgetInstanceOptions(),
) {
    val size = LocalSize.current
    val isExpanded = size.height >= 120.dp
    val isWide = isExpanded && size.width >= KaniWidget.WIDE_SIZE.width
    val copy = widgetCopy(snapshot, isExpanded)
    val palette = KaniWidgetPalette.forChoice(options.resolveTheme(snapshot.themeChoice))
    val homeAction = actionStartActivity(kaniWidgetHomeIntent(LocalContext.current))
    val studyAction = actionStartActivity(kaniWidgetLaunchIntent(LocalContext.current, snapshot))
    val description = WidgetTextCopy.widgetDescription(copy.title, copy.body)
    val showHeatmap = options.style == KaniWidgetStyle.HEATMAP &&
        isExpanded &&
        snapshot.state != KaniWidgetState.NOT_SET_UP &&
        snapshot.last35DayCounts.isNotEmpty()
    if (showHeatmap) {
        // The heatmap needs more vertical room, so it trades the 16dp padding
        // for a denser 12dp frame. Tapping it opens the stats screen that
        // hosts the full heatmap.
        val statsAction = actionStartActivity(kaniWidgetStatsIntent(LocalContext.current))
        HeatmapContent(
            snapshot = snapshot,
            palette = palette,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.background.toProvider())
                .clickable(statsAction)
                .padding(12.dp)
                .semantics { contentDescription = description },
        )
        return
    }
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(palette.background.toProvider())
        .clickable(homeAction)
        .padding(16.dp)
        .semantics { contentDescription = description }
    if (isWide) {
        Row(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTextBlock(copy, palette, studyAction)
            }
            if (snapshot.last7DayCounts.isNotEmpty()) {
                Spacer(GlanceModifier.width(16.dp))
                ActivityStrip(snapshot.last7DayCounts, palette)
            }
        }
    } else {
        Column(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetTextBlock(
                copy,
                palette,
                studyAction,
                showStrip = isExpanded,
                dayCounts = snapshot.last7DayCounts,
            )
        }
    }
}

@Composable
private fun WidgetTextBlock(
    copy: KaniWidgetCopy,
    palette: KaniWidgetPalette,
    actionRowAction: Action,
    showStrip: Boolean = false,
    dayCounts: List<Int> = emptyList(),
) {
    Text(
        text = WidgetTextCopy.appName(),
        style = TextStyle(
            color = palette.primary.toProvider(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
    Spacer(GlanceModifier.height(3.dp))
    Text(
        text = copy.title,
        style = TextStyle(
            color = palette.ink.toProvider(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    Spacer(GlanceModifier.height(3.dp))
    Text(
        text = copy.body,
        style = TextStyle(
            color = palette.muted.toProvider(),
            fontSize = 13.sp,
        ),
    )
    if (copy.extraLine.isNotEmpty()) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = copy.extraLine,
            style = TextStyle(
                color = palette.muted.toProvider(),
                fontSize = 12.sp,
            ),
        )
    }
    if (showStrip && dayCounts.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        ActivityStrip(dayCounts, palette)
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(
        text = copy.action,
        modifier = GlanceModifier
            .clickable(actionRowAction)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = copy.action },
        style = TextStyle(
            color = palette.primary.toProvider(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun ActivityStrip(dayCounts: List<Int>, palette: KaniWidgetPalette) {
    val maxCount = dayCounts.maxOrNull() ?: 1
    Row(
        modifier = GlanceModifier.padding(vertical = 2.dp),
    ) {
        dayCounts.forEachIndexed { index, count ->
            if (index > 0) Spacer(GlanceModifier.width(4.dp))
            val alpha = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0.15f, 1.0f) else 0.15f
            val cellRole = if (count == 0) palette.track else palette.primary.withAlpha(alpha)
            Box(
                modifier = GlanceModifier
                    .size(16.dp)
                    .background(cellRole.toProvider()),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

@Composable
private fun HeatmapContent(
    snapshot: KaniWidgetSnapshot,
    palette: KaniWidgetPalette,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = heatmapHeaderLine(snapshot),
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

/** Header for the heatmap style: brand, due count, and streak in one row. */
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
    val cells = dayCounts.takeLast(KaniWidgetSnapshotLoader.HEATMAP_DAYS)
    val maxCount = cells.maxOrNull() ?: 1
    Column {
        cells.chunked(KaniWidgetSnapshotLoader.STRIP_DAYS).forEachIndexed { rowIndex, week ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(2.dp))
            Row {
                week.forEachIndexed { index, count ->
                    if (index > 0) Spacer(GlanceModifier.width(2.dp))
                    val alpha = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0.15f, 1.0f) else 0.15f
                    val cellRole = if (count == 0) palette.track else palette.primary.withAlpha(alpha)
                    Box(
                        modifier = GlanceModifier
                            .size(13.dp)
                            .background(cellRole.toProvider()),
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
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
    KaniWidgetState.NOTHING_DUE -> KaniWidgetCopy(
        WidgetTextCopy.nothingDueTitle(),
        WidgetTextCopy.nothingDueBody(snapshot.streakDays, snapshot.nextUsefulAtMillis),
        WidgetTextCopy.openKaniLabel(),
        extraLine = if (isExpanded) WidgetTextCopy.bestStreakLabel(snapshot.bestStreakDays) else "",
    )
    KaniWidgetState.DUE_NOW -> {
        val reviewCount = snapshot.dueCount - snapshot.newDueCount
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
