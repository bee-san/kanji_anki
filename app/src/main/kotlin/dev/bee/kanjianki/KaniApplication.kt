package dev.bee.kanjianki

import android.app.Application
import androidx.work.Configuration
import dev.bee.kanjianki.application.RestoreGatedContainer
import dev.bee.kanjianki.backup.StagedRestoreApplier
import dev.bee.kanjianki.widget.WidgetHostBindings
import dev.bee.kanjianki.data.LocalStoreWidgetDataPort
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.widget.KaniWidgetEventHooks

class KaniApplication : Application(), Configuration.Provider {
    private val containerStartup = RestoreGatedContainer(
        restore = ::applyPendingRestoreAtStartup,
        allowsStartup = ::restoreAllowsStartup,
        blockedMessage = {
            "Restore cleanup must finish before Kani can start"
        },
        createContainer = { AndroidKaniContainer(this) },
    )
    private val workerFactory = KaniWorkerFactory(AndroidContainerProvider { container })

    internal val container: AndroidKaniContainer
        get() = containerStartup.container

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
        // Registered before any widget receiver can run: the system may deliver an
        // APPWIDGET_UPDATE immediately after a process kill, and an unregistered binding
        // renders the not-set-up state rather than the user's real queue.
        registerWidgetHostBindings()
        val restoreResult = containerStartup.start()
        try {
            KaniWidgetEventHooks.DEFAULT.restoreCompleted(
                this,
                applied = restoreResult == StagedRestoreApplier.Result.APPLIED,
            )
            // Debug-only: mirror study-load timing probes to a shareable file under
            // Android/data/dev.bee.kanjianki/files/kani-study-debug.log. No-op in release.
            StudyLoadDebugLog.init(this)
            // User-toggleable diagnostic log (Settings > Automation > Debug log). Resolves the
            // persisted switch on its own background thread, so this never blocks process start,
            // and writes an app-start line when the switch is on.
            AppDebugLog.init(this)
        } catch (failure: Throwable) {
            containerStartup.closeSuppressing(failure)
            throw failure
        }
    }

    override fun onTerminate() {
        containerStartup.close()
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

    /**
     * Hands `:widget` the two capabilities it cannot construct for itself.
     *
     * A widget receiver is entered by the system, so it has no call site to be injected at —
     * it has to find its dependencies. Registering them here inverts the direction that used
     * to make the modules circular: `:app` provides, `:widget` reads, and `:widget` names no
     * type of this module's.
     */
    private fun registerWidgetHostBindings() {
        WidgetHostBindings.openDataPort = { context ->
            runCatching {
                LocalStoreWidgetDataPort(context.requireKaniContainer().openLocalStore())
            }.getOrNull()
        }
        // Deliberately not `container.dispatchers.maintenance`: reading the container here
        // would defeat the point of registering before `containerStartup.start()`. The
        // registration has to happen first, because a widget update can be delivered before
        // startup finishes, but the container does not exist yet — and `RestoreGatedContainer`
        // throws rather than hand out a half-built one. So the binding stays a lambda the
        // reader resolves when it actually refreshes, by which point startup has run.
        WidgetHostBindings.refreshContext = { requireKaniContainer().dispatchers.maintenance }
        WidgetHostBindings.databaseIsCurrent = { context ->
            val file = context.getDatabasePath(LocalStoreSchema.DB_NAME)
            when {
                !file.isFile -> WidgetHostBindings.DatabaseState.MISSING
                databaseVersion(file.path) == LocalStoreSchema.DB_VERSION ->
                    WidgetHostBindings.DatabaseState.CURRENT
                else -> WidgetHostBindings.DatabaseState.WRONG_VERSION
            }
        }
    }

    /**
     * The on-disk schema version, or null when the file cannot be opened read-only.
     *
     * Read-only and swallowing failures on purpose: this runs from a widget refresh, and a
     * widget must never create or migrate the database — doing so from a background refresh
     * would race the app's own migration.
     */
    private fun databaseVersion(path: String): Int? = runCatching {
        android.database.sqlite.SQLiteDatabase
            .openDatabase(path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            .use { it.version }
    }.getOrNull()
}
