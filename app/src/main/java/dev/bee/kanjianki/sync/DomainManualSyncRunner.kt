package dev.bee.kanjianki.sync

import dev.bee.kanjianki.LegacySyncRequestFactory
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository
import dev.bee.kanjianki.domain.sync.RunSourceMirrorSyncRequest
import dev.bee.kanjianki.domain.sync.RunSourceMirrorSyncUseCase
import dev.bee.kanjianki.domain.sync.SyncAlreadyRunningException
import dev.bee.kanjianki.domain.sync.SyncProgressListener
import dev.bee.kanjianki.domain.sync.SyncProgressSnapshot
import dev.bee.kanjianki.domain.sync.SyncProgressStage
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException

class DomainManualSyncRunner(
    private val requestFactory: (RecordsSyncModels.Settings) -> RunSourceMirrorSyncRequest,
    private val runSourceMirrorSync: suspend (RunSourceMirrorSyncRequest) -> SyncRunId,
    private val syncRunReader: suspend (SyncRunId) -> SyncRun?,
    private val dashboardRowCounter: suspend () -> Int,
) {
    constructor(
        requestFactory: LegacySyncRequestFactory,
        runSourceMirrorSync: RunSourceMirrorSyncUseCase,
        syncRuns: SyncRunRepository,
        studyDashboard: StudyDashboardRepository,
    ) : this(
        requestFactory = { settings -> requestFactory.request(settings) },
        runSourceMirrorSync = { request -> runSourceMirrorSync(request) },
        syncRunReader = { id -> syncRuns.get(id) },
        dashboardRowCounter = { studyDashboard.listTop(DASHBOARD_ROW_COUNT_LIMIT).size },
    )

    suspend fun run(
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener? = SyncProgress.NONE,
    ): ManualSyncEngine.SyncResult {
        val listener = progress ?: SyncProgress.NONE
        return try {
            val request = requestFactory(settings).copy(
                progress = LegacyProgressAdapter(listener),
            )
            val syncRunId = runSourceMirrorSync(request)
            val syncRun = syncRunReader(syncRunId)
                ?: return ManualSyncEngine.SyncResult.failed("Sync status was not recorded.")
            resultFor(syncRun)
        } catch (error: SyncAlreadyRunningException) {
            ManualSyncEngine.SyncResult.skipped(error.message ?: "Sync already running.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ManualSyncEngine.SyncResult.failed(error.message ?: error::class.simpleName ?: "Sync failed.")
        }
    }

    @JvmOverloads
    fun runBlocking(
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener? = SyncProgress.NONE,
    ): ManualSyncEngine.SyncResult = runBlocking {
        run(settings, progress)
    }

    private suspend fun resultFor(syncRun: SyncRun): ManualSyncEngine.SyncResult {
        if (syncRun.status != SyncRunStatus.SUCCESS) {
            return ManualSyncEngine.SyncResult.failed(syncRun.errorMessage ?: "Sync failed.")
        }
        return ManualSyncEngine.SyncResult.success(
            dashboardRowCounter().coerceAtLeast(0),
            syncRun.suspendedKanjiImportedCount,
            syncRun.removalMessage.orEmpty(),
            "",
        )
    }

    private class LegacyProgressAdapter(
        private val listener: SyncProgress.Listener,
    ) : SyncProgressListener {
        override fun onSyncProgress(progress: SyncProgressSnapshot) {
            try {
                listener.onSyncProgress(progress.toLegacyProgress())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // UI progress listeners are status side effects and must not decide sync outcome.
            }
        }

        private fun SyncProgressSnapshot.toLegacyProgress(): SyncProgress =
            if (stage == SyncProgressStage.SCANNING_CARDS && totalKnown) {
                SyncProgress.cardsScanned(scannedCards, totalCards)
            } else {
                SyncProgress.atStage(stage.toLegacyStage())
            }

        private fun SyncProgressStage.toLegacyStage(): SyncProgress.Stage =
            when (this) {
                SyncProgressStage.FINDING_NOTE_TYPE -> SyncProgress.Stage.FINDING_NOTE_TYPE
                SyncProgressStage.READING_NOTES -> SyncProgress.Stage.READING_NOTES
                SyncProgressStage.SCANNING_CARDS -> SyncProgress.Stage.SCANNING_CARDS
                SyncProgressStage.PROCESSING_IMPORTED_CARDS -> SyncProgress.Stage.PROCESSING_IMPORTED_CARDS
                SyncProgressStage.BUILDING_PRACTICE_QUEUE -> SyncProgress.Stage.BUILDING_PRACTICE_QUEUE
                SyncProgressStage.ARCHIVING_IMPORTED_CARDS -> SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS
            }
    }

    private companion object {
        const val DASHBOARD_ROW_COUNT_LIMIT = 100_000
    }
}
