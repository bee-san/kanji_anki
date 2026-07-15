package dev.bee.kanjianki.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class KaniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KaniWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(intent.action)) {
            // Day/time boundary events re-render the widget so "due today"
            // semantics stay fresh without waiting for the hourly fallback.
            KaniWidgetUpdater.requestUpdate(context)
            return
        }
        super.onReceive(context, intent)
    }
}
