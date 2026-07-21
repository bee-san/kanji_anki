package dev.bee.kanjianki.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KaniWidgetRefreshReceiver : BroadcastReceiver() {
    internal var coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var refreshInstalled: suspend (Context) -> Unit = { context ->
        KaniWidgetRegistry.DEFAULT.refreshInstalled(context)
    }
    internal var boundaryReset: (Context) -> Unit = KaniWidgetBoundaryAlarm::reset
    internal var boundaryFired: (Context) -> Unit = KaniWidgetBoundaryAlarm::markFired

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || !KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(intent?.action)) return
        val appContext = context.applicationContext
        prepareBoundaryState(appContext, intent)
        val pendingResult = goAsync()
        launchRefresh(appContext) { pendingResult.finish() }
    }

    internal fun prepareBoundaryState(context: Context, intent: Intent?) {
        when {
            intent?.getBooleanExtra(KaniWidgetBoundaryAlarm.EXTRA_BOUNDARY_TRIGGER, false) == true -> {
                boundaryFired(context)
            }
            intent?.action == Intent.ACTION_TIME_CHANGED ||
                intent?.action == Intent.ACTION_TIMEZONE_CHANGED ||
                intent?.action == Intent.ACTION_BOOT_COMPLETED ||
                intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED -> {
                boundaryReset(context)
            }
        }
    }

    internal fun launchRefresh(context: Context, onFinished: () -> Unit) {
        val refresh = coroutineScope.launch {
            try {
                refreshInstalled(context)
            } catch (error: Exception) {
                Log.w(TAG, "Widget family refresh failed", error)
            }
        }
        // A launch into an already-cancelled scope may never enter the coroutine
        // body, so completion rather than a body-level finally owns goAsync().
        refresh.invokeOnCompletion { onFinished() }
    }

    private companion object {
        const val TAG = "KaniWidgetRefresh"
    }
}
