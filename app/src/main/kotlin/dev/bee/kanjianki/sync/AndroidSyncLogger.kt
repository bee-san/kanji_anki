package dev.bee.kanjianki.sync

import android.util.Log
import dev.bee.kanjianki.AppDebugLog
import dev.bee.kanjianki.platform.AppLogEvent
import dev.bee.kanjianki.platform.AppLogLevel
import dev.bee.kanjianki.platform.AppLogger

internal object AndroidSyncLogger : AppLogger {
    private const val TAG = "ManualSyncEngine"

    override fun log(event: AppLogEvent) {
        when (event.level) {
            AppLogLevel.DEBUG -> Log.d(TAG, event.message, event.cause)
            AppLogLevel.INFO -> Log.i(TAG, event.message, event.cause)
            AppLogLevel.WARNING -> Log.w(TAG, event.message, event.cause)
            AppLogLevel.ERROR -> Log.e(TAG, event.message, event.cause)
        }
        val cause = event.cause
        if (cause == null) {
            AppDebugLog.log(event.message)
        } else {
            AppDebugLog.logError(event.message, cause)
        }
    }
}
