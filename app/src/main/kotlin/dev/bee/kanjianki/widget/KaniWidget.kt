package dev.bee.kanjianki.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.text.TextStyle
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
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
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(ioDispatcher) {
            KaniWidgetSnapshotLoader.load(context)
        }
        provideContent {
            KaniWidgetContent(snapshot)
        }
    }
}

@Composable
private fun KaniWidgetContent(snapshot: KaniWidgetSnapshot) {
    val copy = widgetCopy(snapshot)
    val launchAction = actionStartActivity(kaniWidgetLaunchIntent(LocalContext.current, snapshot))
    val isExpanded = LocalSize.current.height >= 120.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFFFF8F2)))
            .clickable(launchAction)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = WidgetTextCopy.appName(),
            style = TextStyle(
                color = ColorProvider(Color(0xFFB94962)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = copy.title,
            style = TextStyle(
                color = ColorProvider(Color(0xFF2B2525)),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = copy.body,
            style = TextStyle(
                color = ColorProvider(Color(0xFF625A5A)),
                fontSize = 13.sp,
            ),
        )
        if (isExpanded && snapshot.last7DayCounts.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            ActivityStrip(snapshot.last7DayCounts)
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = copy.action,
            style = TextStyle(
                color = ColorProvider(Color(0xFFB94962)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun ActivityStrip(dayCounts: List<Int>) {
    val maxCount = dayCounts.maxOrNull() ?: 1
    Row(
        modifier = GlanceModifier.padding(vertical = 2.dp),
    ) {
        dayCounts.forEachIndexed { index, count ->
            if (index > 0) Spacer(GlanceModifier.width(4.dp))
            val alpha = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0.15f, 1.0f) else 0.15f
            val cellColor = if (count == 0) Color(0xFFEDE8E4) else Color(0xFFB94962).copy(alpha = alpha)
            Box(
                modifier = GlanceModifier
                    .size(16.dp)
                    .background(ColorProvider(cellColor)),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

/**
 * Reuse the visible Kani task when the widget is tapped. Without these flags,
 * every tap starts another MainActivity and Back can reveal a stale duplicate
 * screen underneath it instead of returning to the previous app.
 */
internal fun kaniWidgetLaunchIntent(context: Context, snapshot: KaniWidgetSnapshot): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (snapshot.state == KaniWidgetState.DUE_NOW) {
            putExtra(MainActivityBase.EXTRA_OPEN_STUDY, true)
        }
    }

private data class WidgetCopy(
    val title: String,
    val body: String,
    val action: String,
)

private fun widgetCopy(snapshot: KaniWidgetSnapshot): WidgetCopy = when (snapshot.state) {
    KaniWidgetState.NOT_SET_UP -> WidgetCopy(
        WidgetTextCopy.notSetUpTitle(),
        WidgetTextCopy.notSetUpBody(),
        WidgetTextCopy.openKaniLabel(),
    )
    KaniWidgetState.NOTHING_DUE -> WidgetCopy(
        WidgetTextCopy.nothingDueTitle(),
        WidgetTextCopy.nothingDueBody(snapshot.streakDays, snapshot.nextUsefulAtMillis),
        WidgetTextCopy.openKaniLabel(),
    )
    KaniWidgetState.DUE_NOW -> WidgetCopy(
        WidgetTextCopy.dueCountLabel(snapshot.dueCount),
        WidgetTextCopy.streakLabel(snapshot.streakDays),
        WidgetTextCopy.studyNowLabel(),
    )
}
