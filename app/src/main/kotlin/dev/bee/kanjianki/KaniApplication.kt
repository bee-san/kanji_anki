package dev.bee.kanjianki

import android.app.Application
import androidx.work.Configuration

class KaniApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setJobSchedulerJobIdRange(10_000, 11_000)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Debug-only: mirror study-load timing probes to a shareable file under
        // Android/data/dev.bee.kanjianki/files/kani-study-debug.log. No-op in release.
        StudyLoadDebugLog.init(this)
    }
}
