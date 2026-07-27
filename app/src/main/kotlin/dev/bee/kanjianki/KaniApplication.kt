package dev.bee.kanjianki

import android.app.Application
import androidx.work.Configuration
import dev.bee.kanjianki.backup.StagedRestoreApplier
import dev.bee.kanjianki.widget.KaniWidgetEventHooks

class KaniApplication : Application(), Configuration.Provider {
    private var ownedContainer: AndroidKaniContainer? = null
    private val workerFactory = KaniWorkerFactory(AndroidContainerProvider { container })

    internal val container: AndroidKaniContainer
        get() = checkNotNull(ownedContainer) {
            "Kani container is unavailable before staged restore completes"
        }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setJobSchedulerJobIdRange(10_000, 11_000)
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // A validated restore is swapped in before any receiver, worker, activity, or
        // diagnostic toggle can open LocalStore. The applier is idempotent, so an
        // interrupted swap resumes safely on the next process start.
        val restoreResult = applyPendingRestoreAtStartup()
        check(restoreAllowsStartup(restoreResult)) {
            // The database replacement committed, but stale WAL/SHM cleanup did not.
            // Stop before any initializer or Android component can open SQLite.
            "Restore cleanup must finish before Kani can start"
        }
        val processContainer = AndroidKaniContainer(this)
        ownedContainer = processContainer
        try {
            KaniWidgetEventHooks.DEFAULT.restoreCompleted(this, restoreResult)
            // Debug-only: mirror study-load timing probes to a shareable file under
            // Android/data/dev.bee.kanjianki/files/kani-study-debug.log. No-op in release.
            StudyLoadDebugLog.init(this)
            // User-toggleable diagnostic log (Settings > Automation > Debug log). Resolves the
            // persisted switch on its own background thread, so this never blocks process start,
            // and writes an app-start line when the switch is on.
            AppDebugLog.init(this)
        } catch (failure: Throwable) {
            ownedContainer = null
            processContainer.close()
            throw failure
        }
    }

    override fun onTerminate() {
        ownedContainer?.close()
        ownedContainer = null
        super.onTerminate()
    }

    /**
     * The exact pre-component restore hook used by [onCreate]. Instrumentation cannot
     * kill and relaunch its own runner process without invalidating the test, so the
     * staged-restore contract test invokes this idempotent hook on the real application
     * instance before launching an ActivityScenario.
     */
    internal fun applyPendingRestoreAtStartup(): StagedRestoreApplier.Result =
        StagedRestoreApplier.apply(this)

    internal companion object {
        @JvmStatic
        fun restoreAllowsStartup(result: StagedRestoreApplier.Result): Boolean {
            return result != StagedRestoreApplier.Result.BLOCK_STARTUP
        }
    }
}
