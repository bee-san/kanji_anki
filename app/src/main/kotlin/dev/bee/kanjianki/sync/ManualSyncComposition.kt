package dev.bee.kanjianki.sync

import android.content.Context
import dev.bee.kanjianki.R
import dev.bee.kanjianki.ReadingExposureMediaReader
import dev.bee.kanjianki.application.ManualSyncQueuePlanner
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.data.DictionaryStore
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.SqliteSettingsRepository
import dev.bee.kanjianki.data.SqliteStudyRepository
import dev.bee.kanjianki.data.SqliteSyncRepository
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.time.AppClock
import dev.bee.kanjianki.widget.KaniWidgetUpdater
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking

internal interface SyncAssetReaders {
    @Throws(IOException::class)
    fun loadRanks(): JitenKanjiRanks

    fun loadDictionary(): DictionaryLookup?

    @Throws(IOException::class)
    fun loadSimilarKanjiIndex(): SimilarKanjiIndex

    fun loadReadingExposure(): ReadingExposureModels.ExposureIndex
}

internal data class SyncPostCommitEffects(
    val reminderRescheduler: Runnable,
    val widgetRefresher: Runnable,
)

internal fun createManualSyncEngine(
    context: Context,
    syncUseCases: SyncUseCases,
    gateway: CollectionGateway,
    settings: SettingsSnapshot,
    progress: SyncProgress.Listener? = SyncProgress.NONE,
    clock: AppClock? = AppClock.systemClock(),
    repairedWriteBackAuthorized: Boolean = false,
    confirmedRepairedNoteIds: Set<Long>? = null,
    sourceBindingGate: SyncSourceBindingGate = SyncSourceBindingGate.ALLOW_ALL,
): ManualSyncEngine {
    val appContext = context.applicationContext
    return ManualSyncEngine(
        syncUseCases = syncUseCases,
        gateway = gateway,
        settingsSnapshot = settings,
        progress = progress ?: SyncProgress.NONE,
        clock = AppClock.orSystem(clock),
        assetReaders = AndroidSyncAssetReaders(appContext),
        queuePlannerFactory = ::ManualSyncQueuePlanner,
        postCommitEffects = SyncPostCommitEffects(
            reminderRescheduler = Runnable { ReminderScheduler.schedule(appContext) },
            widgetRefresher = Runnable { KaniWidgetUpdater.requestUpdate(appContext) },
        ),
        repairedWriteBackAuthorized = repairedWriteBackAuthorized,
        confirmedRepairedNoteIds = confirmedRepairedNoteIds,
        sourceBindingGate = sourceBindingGate,
    )
}

/**
 * Storage-backed composition used by adapter and provider tests. Production
 * routes with a process container pass its already-constructed [SyncUseCases].
 */
internal fun createManualSyncEngine(
    context: Context,
    store: LocalStore,
    gateway: CollectionGateway,
    settings: RecordsSyncModels.Settings,
    progress: SyncProgress.Listener? = SyncProgress.NONE,
    clock: AppClock? = AppClock.systemClock(),
    repairedWriteBackAuthorized: Boolean = false,
    confirmedRepairedNoteIds: Set<Long>? = null,
    sourceBindingGate: SyncSourceBindingGate = SyncSourceBindingGate.ALLOW_ALL,
): ManualSyncEngine {
    val useCases = syncUseCases(store)
    val settingsSnapshot = runBlocking { useCases.loadSettings() }.copy(sync = settings)
    return createManualSyncEngine(
        context,
        useCases,
        gateway,
        settingsSnapshot,
        progress,
        clock,
        repairedWriteBackAuthorized,
        confirmedRepairedNoteIds,
        sourceBindingGate,
    )
}

internal fun syncUseCases(store: LocalStore): SyncUseCases =
    SyncUseCases(
        SqliteSyncRepository(store),
        SqliteStudyRepository(store),
        SqliteSettingsRepository(store),
    )

private class AndroidSyncAssetReaders(
    private val context: Context,
) : SyncAssetReaders {
    override fun loadRanks(): JitenKanjiRanks = DictionaryStore.open(context).jitenRanks()

    override fun loadDictionary(): DictionaryLookup? =
        try {
            DictionaryStore.open(context)
        } catch (_: IOException) {
            null
        }

    override fun loadSimilarKanjiIndex(): SimilarKanjiIndex =
        InputStreamReader(
            context.resources.openRawResource(R.raw.similar_kanji),
            StandardCharsets.UTF_8,
        ).use { reader -> SimilarKanjiIndex.parseTsv(reader) }

    override fun loadReadingExposure(): ReadingExposureModels.ExposureIndex =
        ReadingExposureMediaReader().read()
}
