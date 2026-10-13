package dev.bee.kanjianki.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class KaniFamilyWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        KaniWidgetBoundaryAlarm.onProvidersChanged(
            context,
            KaniWidgetRegistry.DEFAULT.hasInstalledWidgets(context),
        )
    }
}

class KaniWidgetReceiver : KaniFamilyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KaniWidget()
}

class QuickStudyWidgetReceiver : KaniFamilyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickStudyWidget()
}

class ActivityWidgetReceiver : KaniFamilyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActivityWidget()
}

class FocusKanjiWidgetReceiver : KaniFamilyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusKanjiWidget()
}
