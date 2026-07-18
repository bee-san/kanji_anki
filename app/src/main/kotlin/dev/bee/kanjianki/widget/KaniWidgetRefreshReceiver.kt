package dev.bee.kanjianki.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Plain receiver that keeps goAsync work alive until every installed family is refreshed. */
class KaniWidgetRefreshReceiver : BroadcastReceiver() {
    internal var coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var refreshInstalled: suspend (Context) -> Unit = { context ->
        KaniWidgetRegistry.DEFAULT.refreshInstalled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(intent.action)) return
        val pendingResult = goAsync()
        launchRefresh(context.applicationContext) { pendingResult.finish() }
    }

    internal fun launchRefresh(context: Context, onFinished: () -> Unit) {
        coroutineScope.launch {
            try {
                refreshInstalled(context)
            } catch (error: Exception) {
                Log.w(TAG, "Widget family refresh failed", error)
            } finally {
                onFinished()
            }
        }
    }

    private companion object {
        const val TAG = "KaniWidgetRefresh"
    }
}
