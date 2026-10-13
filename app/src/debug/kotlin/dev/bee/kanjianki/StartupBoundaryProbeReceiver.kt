package dev.bee.kanjianki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/** Debug-only cold-process signal used by restore-boundary instrumentation. */
class StartupBoundaryProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_PROBE) return
        File(requireNotNull(context).filesDir, SIGNAL_FILE_NAME).writeText("receiver-created")
    }

    companion object {
        const val ACTION_PROBE = "dev.bee.kanjianki.debug.STARTUP_BOUNDARY_PROBE"
        const val SIGNAL_FILE_NAME = "startup-boundary-probe.signal"
    }
}
