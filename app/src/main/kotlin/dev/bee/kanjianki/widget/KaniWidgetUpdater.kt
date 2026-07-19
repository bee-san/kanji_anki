package dev.bee.kanjianki.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Routes every event source through one explicit, non-exported refresh receiver. */
internal object KaniWidgetUpdater {
    // S5320 warns about implicit broadcasts. This intent is explicitly component-bound to
    // an android:exported="false" receiver in this package and carries no caller-controlled data.
    @Suppress("kotlin:S5320")
    fun requestUpdate(context: Context?) {
        val appContext = context?.applicationContext ?: return
        if (!KaniWidgetRegistry.DEFAULT.hasInstalledWidgets(appContext)) return
        appContext.sendBroadcast(refreshIntent(appContext))
    }

    fun refreshIntent(context: Context): Intent =
        Intent(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH).apply {
            component = ComponentName(context, KaniWidgetRefreshReceiver::class.java)
            `package` = context.packageName
        }
}
