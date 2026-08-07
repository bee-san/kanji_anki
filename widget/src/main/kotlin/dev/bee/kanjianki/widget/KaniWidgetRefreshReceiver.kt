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
    internal var coroutineScope: CoroutineScope? = null
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
        // The app's maintenance dispatcher when it has registered one, and a background
        // default otherwise: a refresh has to run somewhere, and skipping it would leave a
        // stale widget on the home screen.
        val scope = coroutineScope ?: CoroutineScope(
            // `runCatching`, because resolving the host's dispatcher now reads the process
            // container: a refresh delivered before startup finished would throw, and a stale
            // widget is a worse outcome than doing this one refresh on a plain dispatcher.
            SupervisorJob() +
                (
                    WidgetHostBindings.refreshContext
                        ?.let { resolve -> runCatching(resolve).getOrNull() }
                        ?: Dispatchers.Default
                    ),
        )
        val refresh = scope.launch {
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
