package dev.bee.kanjianki.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class QuickStudyWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(72.dp, 72.dp), DpSize(180.dp, 72.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val snapshot = withContext(ioDispatcher) { StudyWidgetSnapshotLoader.load(context, nowMillis) }
        KaniWidgetBoundaryAlarm.scheduleStudyBoundary(context, nowMillis, snapshot.nextUsefulAtMillis)
        provideContent { KaniWidgetContent(snapshot) }
    }
}

internal class ActivityWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 120.dp), DpSize(250.dp, 130.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val snapshot = withContext(ioDispatcher) { ActivityWidgetSnapshotLoader.load(context, nowMillis) }
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, nowMillis)
        provideContent { LegacyActivityWidgetContent(snapshot, KaniWidgetInstanceOptions()) }
    }
}

internal class FocusKanjiWidget(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 120.dp), DpSize(250.dp, 130.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val focus = withContext(ioDispatcher) { FocusKanjiWidgetSnapshotLoader.load(context, nowMillis) }
        KaniWidgetBoundaryAlarm.scheduleDailyBoundary(context, nowMillis)
        val fallback = when (focus.state) {
            FocusKanjiWidgetState.NOT_SET_UP -> KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP)
            FocusKanjiWidgetState.ERROR -> KaniWidgetSnapshot(KaniWidgetState.ERROR)
            FocusKanjiWidgetState.EMPTY -> KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE)
            FocusKanjiWidgetState.READY -> KaniWidgetSnapshot(
                state = if (focus.isDueNow) KaniWidgetState.DUE_NOW else KaniWidgetState.NOTHING_DUE,
                dueCount = if (focus.isDueNow) 1 else 0,
            )
        }
        provideContent { KaniWidgetContent(fallback) }
    }
}
