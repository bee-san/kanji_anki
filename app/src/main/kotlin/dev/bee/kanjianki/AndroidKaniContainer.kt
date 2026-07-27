package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.application.KaniContainer
import dev.bee.kanjianki.data.AndroidDeviceSettingsStore
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.SqliteHomeRepository
import dev.bee.kanjianki.data.SqliteSettingsRepository
import dev.bee.kanjianki.data.SqliteStatsRepository
import dev.bee.kanjianki.data.SqliteStudyRepository
import dev.bee.kanjianki.data.SqliteSyncRepository
import dev.bee.kanjianki.sync.SyncCancellation
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal fun interface AndroidContainerProvider {
    fun get(): AndroidKaniContainer
}

/** Android process graph. Activities and background components borrow these resources. */
internal class AndroidKaniContainer(
    context: Context,
) : KaniContainer {
    private val appContext = context.applicationContext

    val localStore: LocalStore = AppLocalStoreFactory.create(appContext)
    val ankiDroidGateway = AnkiDroidGateway(appContext)

    override val homeRepository = SqliteHomeRepository(localStore)
    override val studyRepository = SqliteStudyRepository(localStore)
    override val statsRepository = SqliteStatsRepository(localStore)
    override val settingsRepository = SqliteSettingsRepository(localStore)
    override val syncRepository = SqliteSyncRepository(localStore)
    override val deviceSettingsStore = AndroidDeviceSettingsStore(appContext)

    override val userIoExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "kani-user-io") }
    override val maintenanceExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kani-maintenance").apply { isDaemon = true }
        }
    val dispatchers = KaniDispatchers(userIoExecutor, maintenanceExecutor)
    private val resourceShutdown = ProcessResourceShutdown(
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
