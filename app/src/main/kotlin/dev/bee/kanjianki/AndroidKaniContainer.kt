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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Android process graph. Activities and background components borrow these resources. */
internal class AndroidKaniContainer(
    context: Context,
) : KaniContainer {
    private val closed = AtomicBoolean()
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

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        userIoExecutor.shutdownNow()
        maintenanceExecutor.shutdownNow()
        localStore.close()
    }
}
