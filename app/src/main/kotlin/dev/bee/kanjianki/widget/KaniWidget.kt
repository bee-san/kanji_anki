package dev.bee.kanjianki.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.action.actionStartActivity
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
    val launchAction = if (snapshot.state == KaniWidgetState.DUE_NOW) {
        actionStartActivity<MainActivity>(actionParametersOf(OpenStudyKey to true))
    } else {
        actionStartActivity<MainActivity>()
    }
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

private val OpenStudyKey = ActionParameters.Key<Boolean>(MainActivityBase.EXTRA_OPEN_STUDY)

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
