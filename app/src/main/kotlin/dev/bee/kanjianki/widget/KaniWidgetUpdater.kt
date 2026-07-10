package dev.bee.kanjianki.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Event-driven refresh; deliberately no periodic widget worker. */
internal object KaniWidgetUpdater {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestUpdate(context: Context?) {
        val appContext = context?.applicationContext ?: return
        val component = ComponentName(appContext, KaniWidgetReceiver::class.java)
        if (AppWidgetManager.getInstance(appContext).getAppWidgetIds(component).isEmpty()) {
            return
        }
        updateScope.launch {
            KaniWidget().updateAll(appContext)
        }
    }
}
