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
import androidx.glance.action.Action
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.bee.kanjianki.core.WidgetTextCopy
import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FocusKanjiWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    companion object {
        internal val RESPONSIVE_SIZES = setOf(
            DpSize(120.dp, 120.dp),
            DpSize(180.dp, 120.dp),
            DpSize(250.dp, 130.dp),
        )
    }

    override val sizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val focus = withContext(ioDispatcher) {
            FocusKanjiWidgetSnapshotLoader.load(context, nowMillis)
        }
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, nowMillis)
        provideContent { FocusKanjiWidgetContent(focus) }
    }
}

internal data class FocusKanjiLayout(
    val tier: FocusKanjiLayoutTier,
    val glyphFontSp: Int,
) {
    val isWide: Boolean
        get() = tier == FocusKanjiLayoutTier.WIDE
}

internal enum class FocusKanjiLayoutTier {
    GLYPH_ONLY,
    COMPACT,
    WIDE,
}

private const val MAX_COMPACT_READING_CHARS = 8
private const val MAX_WIDE_READING_CHARS = 8
private const val MAX_COMPACT_MEANING_CHARS = 7
private const val MAX_WIDE_MEANING_CHARS = 12
internal const val FOCUS_ACTION_FONT_SP = 13

internal fun focusKanjiLayout(
    widthDp: Float,
    heightDp: Float,
    fontScale: Float,
): FocusKanjiLayout {
    val tier = when {
        fontScale >= 1.7f -> FocusKanjiLayoutTier.GLYPH_ONLY
        widthDp >= 230f && heightDp >= 120f && fontScale < 1.5f -> FocusKanjiLayoutTier.WIDE
        widthDp >= 120f && heightDp >= 110f -> FocusKanjiLayoutTier.COMPACT
        else -> FocusKanjiLayoutTier.GLYPH_ONLY
    }
    return FocusKanjiLayout(tier, if (tier == FocusKanjiLayoutTier.WIDE) 52 else 44)
}

internal fun focusVisibleReading(
    readings: String,
    tier: FocusKanjiLayoutTier,
): String? {
    val maximum = when (tier) {
        FocusKanjiLayoutTier.GLYPH_ONLY -> return null
        FocusKanjiLayoutTier.COMPACT -> MAX_COMPACT_READING_CHARS
        FocusKanjiLayoutTier.WIDE -> MAX_WIDE_READING_CHARS
    }
    return readings.takeIf { it.isNotBlank() && it.length <= maximum }
}

internal fun focusVisibleMeaning(
    meaning: String,
    tier: FocusKanjiLayoutTier,
): String? {
    val maximum = when (tier) {
        FocusKanjiLayoutTier.GLYPH_ONLY -> return null
        FocusKanjiLayoutTier.COMPACT -> MAX_COMPACT_MEANING_CHARS
        FocusKanjiLayoutTier.WIDE -> MAX_WIDE_MEANING_CHARS
    }
    val normalized = meaning.trim()
    if (normalized.isEmpty() || normalized.length <= maximum) {
        return normalized.takeIf(String::isNotEmpty)
    }

    val characters = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(normalized) }
    val end = characters.preceding(maximum + 1).takeIf { it > 0 }
        ?: characters.following(0).takeIf { it != BreakIterator.DONE }
        ?: return null
    val rawClip = normalized.substring(0, end)
    val clipped = rawClip.trimEnd()
    if (rawClip.lastOrNull()?.isWhitespace() == true) return clipped

    val wordBoundary = clipped.lastIndexOf(' ')
    return if (wordBoundary >= maximum / 2) {
        clipped.substring(0, wordBoundary).trimEnd()
    } else {
        clipped
    }
}

@Composable
internal fun FocusKanjiWidgetContent(snapshot: FocusKanjiWidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val layout = focusKanjiLayout(
        widthDp = size.width.value,
        heightDp = size.height.value,
        fontScale = context.resources.configuration.fontScale,
    )
    val palette = KaniWidgetPalette.forChoice(snapshot.themeChoice)
    when (snapshot.state) {
        FocusKanjiWidgetState.READY -> FocusKanjiReadyContent(snapshot, layout, palette)
        FocusKanjiWidgetState.NOT_SET_UP,
        FocusKanjiWidgetState.EMPTY,
        FocusKanjiWidgetState.ERROR,
        -> FocusKanjiFallbackContent(snapshot.state, layout, palette)
    }
}

@Composable
private fun FocusKanjiReadyContent(
    snapshot: FocusKanjiWidgetSnapshot,
    layout: FocusKanjiLayout,
    palette: KaniWidgetPalette,
) {
    val context = LocalContext.current
    val detailsAction = actionStartActivity(kaniFocusDetailIntent(context, snapshot.kanji))
    val description = WidgetTextCopy.focusKanjiDescription(
        snapshot.kanji,
        snapshot.primaryMeaning,
        snapshot.readings,
        snapshot.isDueNow,
        layout.isWide && snapshot.isDueNow,
    )
    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(palette.background.toGlanceColor())
        .cornerRadius(16.dp)
        .padding(if (layout.isWide) 8.dp else 6.dp)

    if (layout.tier == FocusKanjiLayoutTier.GLYPH_ONLY) {
        Box(
            modifier = cardModifier
                .clickable(detailsAction)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            FocusKanjiGlyph(snapshot.kanji, layout.glyphFontSp, palette)
        }
        return
    }

    if (layout.tier == FocusKanjiLayoutTier.COMPACT) {
        FocusKanjiCompactContent(snapshot, description, detailsAction, palette, cardModifier)
        return
    }

    Row(
        modifier = cardModifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusKanjiGlyph(snapshot.kanji, layout.glyphFontSp, palette)
            Column(
                modifier = GlanceModifier.defaultWeight().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                focusVisibleMeaning(snapshot.primaryMeaning, FocusKanjiLayoutTier.WIDE)?.let { meaning ->
                    Text(
                        text = meaning,
                        style = TextStyle(
                            color = palette.ink.toGlanceColor(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 2,
                    )
                }
                focusVisibleReading(snapshot.readings, FocusKanjiLayoutTier.WIDE)?.let { reading ->
                    Text(
                        text = reading,
                        style = TextStyle(
                            color = palette.muted.toGlanceColor(),
                            fontSize = 13.sp,
                        ),
                        maxLines = 1,
                    )
                }
                if (snapshot.isDueNow) {
                    Text(
                        text = WidgetTextCopy.focusDueStatus(),
                        style = TextStyle(
                            color = palette.primaryText.toGlanceColor(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Column(
            modifier = GlanceModifier.width(64.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FocusKanjiAction(
                WidgetTextCopy.focusDetailsLabel(),
                detailsAction,
                palette,
            )
            if (snapshot.isDueNow) {
                val studySnapshot = KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 1)
                FocusKanjiAction(
                    WidgetTextCopy.studyNowLabel(),
                    actionStartActivity(kaniWidgetLaunchIntent(context, studySnapshot)),
                    palette,
                )
            }
        }
    }
}

@Composable
private fun FocusKanjiCompactContent(
    snapshot: FocusKanjiWidgetSnapshot,
    description: String,
    detailsAction: Action,
    palette: KaniWidgetPalette,
    cardModifier: GlanceModifier,
) {
    Column(
        modifier = cardModifier
            .clickable(detailsAction)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusKanjiGlyph(snapshot.kanji, 44, palette)
        focusVisibleMeaning(snapshot.primaryMeaning, FocusKanjiLayoutTier.COMPACT)?.let { meaning ->
            Text(
                text = meaning,
                style = TextStyle(
                    color = palette.ink.toGlanceColor(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        focusVisibleReading(snapshot.readings, FocusKanjiLayoutTier.COMPACT)?.let { reading ->
            Text(
                text = reading,
                style = TextStyle(color = palette.muted.toGlanceColor(), fontSize = 12.sp),
                maxLines = 1,
            )
        }
        if (snapshot.isDueNow) {
            Text(
                text = WidgetTextCopy.focusDueStatus(),
                style = TextStyle(
                    color = palette.primaryText.toGlanceColor(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Text(
            text = WidgetTextCopy.focusDetailsLabel(),
            style = TextStyle(
                color = palette.primaryText.toGlanceColor(),
                fontSize = FOCUS_ACTION_FONT_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun FocusKanjiGlyph(
    kanji: String,
    fontSizeSp: Int,
    palette: KaniWidgetPalette,
) {
    Text(
        text = kanji,
        style = TextStyle(
            color = palette.ink.toGlanceColor(),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

@Composable
private fun FocusKanjiAction(
    label: String,
    action: Action,
    palette: KaniWidgetPalette,
) {
    Box(
        modifier = GlanceModifier
            .width(64.dp)
            .height(48.dp)
            .clickable(action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = palette.primaryText.toGlanceColor(),
                fontSize = FOCUS_ACTION_FONT_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
    }
}

private data class FocusFallbackCopy(
    val title: String,
    val body: String,
)

@Composable
private fun FocusKanjiFallbackContent(
    state: FocusKanjiWidgetState,
    layout: FocusKanjiLayout,
    palette: KaniWidgetPalette,
) {
    val context = LocalContext.current
    val copy = when (state) {
        FocusKanjiWidgetState.NOT_SET_UP -> FocusFallbackCopy(
            WidgetTextCopy.notSetUpTitle(),
            WidgetTextCopy.notSetUpBody(),
        )
        FocusKanjiWidgetState.EMPTY -> FocusFallbackCopy(
            WidgetTextCopy.focusEmptyTitle(),
            WidgetTextCopy.focusEmptyBody(),
        )
        FocusKanjiWidgetState.ERROR -> FocusFallbackCopy(
            WidgetTextCopy.errorTitle(),
            WidgetTextCopy.errorBody(),
        )
        FocusKanjiWidgetState.READY -> error("Ready focus must use ready content")
    }
    val action = WidgetTextCopy.openKaniLabel()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background.toGlanceColor())
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionStartActivity(kaniWidgetHomeIntent(context)))
            .semantics {
                contentDescription = WidgetTextCopy.widgetDescription(copy.title, copy.body)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = copy.title,
            style = TextStyle(
                color = palette.ink.toGlanceColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        if (layout.isWide) {
            Text(
                text = copy.body,
                style = TextStyle(color = palette.muted.toGlanceColor(), fontSize = 12.sp),
                maxLines = 2,
            )
        }
        Text(
            text = action,
            style = TextStyle(
                color = palette.primaryText.toGlanceColor(),
                fontSize = FOCUS_ACTION_FONT_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}
