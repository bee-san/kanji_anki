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
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import dev.bee.kanjianki.core.WidgetTextCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class QuickStudyWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    companion object {
        internal val RESPONSIVE_SIZES = setOf(
            DpSize(56.dp, 56.dp),
            DpSize(120.dp, 56.dp),
            DpSize(180.dp, 72.dp),
        )
    }

    override val sizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val snapshot = withContext(ioDispatcher) {
            StudyWidgetSnapshotLoader.load(context, nowMillis)
        }
        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(context, nowMillis, snapshot.nextUsefulAtMillis)
        provideContent { QuickStudyWidgetContent(snapshot) }
    }
}

internal enum class KaniWidgetDestination {
    HOME,
    STUDY,
    STATS,
}

internal enum class QuickStudyTier {
    TINY,
    COMPACT,
    WIDE,
}

internal data class QuickStudyLayout(
    val tier: QuickStudyTier,
    val showBrand: Boolean,
    val showStatus: Boolean,
    val showAction: Boolean,
    val showSeparateAction: Boolean,
    val heroFontSp: Int,
    val statusFontSp: Int = 12,
    val actionFontSp: Int = 13,
    val actionWidthDp: Int,
    val actionMaxLines: Int,
)

internal fun quickStudyLayout(
    widthDp: Float,
    heightDp: Float,
    fontScale: Float,
): QuickStudyLayout {
    val tier = when {
        widthDp < 100f -> QuickStudyTier.TINY
        widthDp < 160f || heightDp < 68f -> QuickStudyTier.COMPACT
        else -> QuickStudyTier.WIDE
    }
    val largeFont = fontScale >= 1.3f
    val veryLargeFont = fontScale >= 1.8f
    return QuickStudyLayout(
        tier = tier,
        showBrand = tier == QuickStudyTier.WIDE && !largeFont,
        showStatus = true,
        showAction = true,
        showSeparateAction = tier != QuickStudyTier.TINY && !veryLargeFont,
        actionWidthDp = if (tier == QuickStudyTier.WIDE) 72 else 56,
        actionMaxLines = if (tier == QuickStudyTier.COMPACT) 2 else 1,
        heroFontSp = when {
            veryLargeFont -> 16
            tier == QuickStudyTier.TINY -> 22
            else -> 28
        },
    )
}

internal data class QuickStudyPresentation(
    val hero: String,
    val status: String,
    val action: String,
    val contentDescription: String,
    val destination: KaniWidgetDestination,
    val showSeparateAction: Boolean,
)

internal fun quickStudyPresentation(
    snapshot: KaniWidgetSnapshot,
    layout: QuickStudyLayout,
): QuickStudyPresentation {
    val action = if (snapshot.state == KaniWidgetState.DUE_NOW) {
        if (layout.tier == QuickStudyTier.COMPACT) {
            WidgetTextCopy.studyLabel()
        } else {
            WidgetTextCopy.studyNowLabel()
        }
    } else {
        WidgetTextCopy.openKaniLabel()
    }
    val destination = if (snapshot.state == KaniWidgetState.DUE_NOW) {
        KaniWidgetDestination.STUDY
    } else {
        KaniWidgetDestination.HOME
    }
    val hero: String
    val status: String
    val spokenStatus: String
    when (snapshot.state) {
        KaniWidgetState.DUE_NOW -> {
            hero = WidgetTextCopy.visualCountLabel(snapshot.dueCount)
            status = if (layout.tier == QuickStudyTier.TINY) {
                WidgetTextCopy.studyLabel()
            } else {
                WidgetTextCopy.quickDueStatus()
            }
            spokenStatus = WidgetTextCopy.dueCountLabel(snapshot.dueCount)
        }
        KaniWidgetState.NOTHING_DUE -> {
            hero = "0"
            status = WidgetTextCopy.quickCaughtUpStatus()
            spokenStatus = WidgetTextCopy.nothingDueTitle()
        }
        KaniWidgetState.NOT_SET_UP -> {
            hero = "—"
            status = WidgetTextCopy.quickSetupStatus()
            spokenStatus = WidgetTextCopy.notSetUpTitle()
        }
        KaniWidgetState.ERROR -> {
            hero = "!"
            status = WidgetTextCopy.quickErrorStatus()
            spokenStatus = WidgetTextCopy.errorTitle()
        }
    }
    return QuickStudyPresentation(
        hero = hero,
        status = status,
        action = action,
        contentDescription = WidgetTextCopy.quickStudyDescription(spokenStatus, action),
        destination = destination,
        showSeparateAction = layout.showSeparateAction,
    )
}

@Composable
internal fun QuickStudyWidgetContent(snapshot: KaniWidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val layout = quickStudyLayout(
        widthDp = size.width.value,
        heightDp = size.height.value,
        fontScale = context.resources.configuration.fontScale,
    )
    val presentation = quickStudyPresentation(snapshot, layout)
    val palette = KaniWidgetPalette.forChoice(snapshot.themeChoice)
    val launchAction = actionStartActivity(
        when (presentation.destination) {
            KaniWidgetDestination.STUDY -> kaniWidgetLaunchIntent(context, snapshot)
            KaniWidgetDestination.HOME -> kaniWidgetHomeIntent(context)
            KaniWidgetDestination.STATS -> kaniWidgetStatsIntent(context)
        },
    )
    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(palette.background.toGlanceColor())
        .cornerRadius(16.dp)
        .padding(if (layout.tier == QuickStudyTier.WIDE) 6.dp else 4.dp)
        .clickable(launchAction)
        .semantics { contentDescription = presentation.contentDescription }

    if (presentation.showSeparateAction) {
        Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickStudyHero(
                presentation = presentation,
                layout = layout,
                palette = palette,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            QuickStudyAction(
                label = presentation.action,
                palette = palette,
                fontSizeSp = layout.actionFontSp,
                widthDp = layout.actionWidthDp,
                maxLines = layout.actionMaxLines,
            )
        }
    } else {
        Box(
            modifier = cardModifier,
            contentAlignment = Alignment.Center,
        ) {
            QuickStudyHero(
                presentation = presentation,
                layout = layout,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
                useActionAsStatus = layout.tier != QuickStudyTier.TINY,
            )
        }
    }
}

@Composable
private fun QuickStudyHero(
    presentation: QuickStudyPresentation,
    layout: QuickStudyLayout,
    palette: KaniWidgetPalette,
    modifier: GlanceModifier,
    useActionAsStatus: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (layout.showBrand) {
            Text(
                text = WidgetTextCopy.appName(),
                style = TextStyle(
                    color = palette.muted.toGlanceColor(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Text(
            text = presentation.hero,
            style = TextStyle(
                color = palette.ink.toGlanceColor(),
                fontSize = layout.heroFontSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (layout.showStatus) {
            Text(
                text = if (useActionAsStatus) presentation.action else presentation.status,
                style = TextStyle(
                    color = palette.primaryText.toGlanceColor(),
                    fontSize = (if (useActionAsStatus) layout.actionFontSp else layout.statusFontSp).sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickStudyAction(
    label: String,
    palette: KaniWidgetPalette,
    fontSizeSp: Int,
    widthDp: Int,
    maxLines: Int,
) {
    Box(
        modifier = GlanceModifier
            .width(widthDp.dp)
            .height(48.dp)
            .background(palette.primary.toGlanceColor())
            .cornerRadius(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = palette.onPrimary.toGlanceColor(),
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = maxLines,
        )
    }
}

internal fun KaniWidgetColorRole.toGlanceColor(): ColorProvider = ColorProvider(day = day, night = night)
