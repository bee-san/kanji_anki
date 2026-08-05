package dev.bee.kanjianki

import android.content.Context
import android.app.Application
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.application.KaniContainer
import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.application.StatsUseCases
import dev.bee.kanjianki.application.StudyUseCases
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.data.AndroidDeviceSettingsStore
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.SqliteHomeRepository
import dev.bee.kanjianki.data.SqliteSettingsRepository
import dev.bee.kanjianki.data.SqliteSourceBindingStore
import dev.bee.kanjianki.data.SqliteStatsRepository
import dev.bee.kanjianki.data.SqliteStudyRepository
import dev.bee.kanjianki.data.SqliteSyncRepository
import dev.bee.kanjianki.sync.SyncCancellation
import dev.bee.kanjianki.sync.AndroidSourceBindingGate
import dev.bee.kanjianki.platform.android.AndroidAppLifecycle
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal fun interface AndroidContainerProvider {
    fun get(): AndroidKaniContainer
}

/** Android process graph. Activities and background components borrow these resources. */
internal class AndroidKaniContainer(
    context: Context,
) : KaniContainer {
    /**
     * The process context, for the platform adapters that genuinely need one.
     *
     * Visible rather than private because sync composition needs it: asset readers, the
     * reminder re-arm, and the widget refresh are all `Context`-shaped, and the host that
     * builds a sync engine holds this container and no Activity.
     */
    val appContext: Context = context.applicationContext

    val localStore: LocalStore = AppLocalStoreFactory.create(appContext)
    val ankiDroidGateway = AnkiDroidGateway(appContext)

    override val homeRepository = SqliteHomeRepository(localStore)
    override val studyRepository = SqliteStudyRepository(localStore)
    override val statsRepository = SqliteStatsRepository(localStore)
    override val settingsRepository = SqliteSettingsRepository(localStore)
    override val syncRepository = SqliteSyncRepository(localStore)
    val sourceBindingStore = SqliteSourceBindingStore(localStore)
    val sourceBindingGate = AndroidSourceBindingGate(sourceBindingStore)
    override val deviceSettingsStore = AndroidDeviceSettingsStore(appContext)
    override val appLifecycle = AndroidAppLifecycle(appContext as Application)
    val homeUseCases = HomeUseCases(
        homeRepository,
        studyRepository,
        settingsRepository,
        syncRepository,
    )
    val settingsUseCases = SettingsUseCases(settingsRepository)
    val statsUseCases = StatsUseCases(statsRepository)
    val studyUseCases = StudyUseCases(studyRepository)
    val syncUseCases = SyncUseCases(syncRepository, studyRepository, settingsRepository)

    override val userIoExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "kani-user-io") }
    override val maintenanceExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kani-maintenance").apply { isDaemon = true }
        }
    val dispatchers = KaniDispatchers(userIoExecutor, maintenanceExecutor)
    private val resourceShutdown = ProcessResourceShutdown(
        appLifecycle::close,
        { userIoExecutor.shutdownNow() },
        { maintenanceExecutor.shutdownNow() },
        localStore::close,
    )

    fun openLocalStore(): LocalStore = AppLocalStoreFactory.create(appContext)

    fun newAnkiDroidGateway(cancellation: SyncCancellation): AnkiDroidGateway =
        AnkiDroidGateway(appContext, cancellation)

    override fun close() = resourceShutdown.close()
}

internal fun Context.requireKaniContainer(): AndroidKaniContainer {
    val application = applicationContext as? KaniApplication
        ?: error("Kani process dependencies require KaniApplication")
    return application.container
}
