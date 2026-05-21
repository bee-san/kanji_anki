package dev.bee.kanjianki

import android.app.Application
import androidx.work.Configuration

class KaniApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setJobSchedulerJobIdRange(10_000, 11_000)
            .build()
}
