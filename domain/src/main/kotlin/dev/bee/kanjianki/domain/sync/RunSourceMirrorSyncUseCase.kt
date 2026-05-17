package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository

class RunSourceMirrorSyncUseCase(
    private val gateway: CollectionGateway,
    private val syncRuns: SyncRunRepository,
    private val sourceMirror: SourceMirrorRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(settings: ImportSettings): SyncRunId {
        val startedAt = clock.nowMillis()
        return try {
            val snapshot = gateway.readCollection(settings)
            val finishedAt = clock.nowMillis()
            val syncRunId = syncRuns.insert(successRun(startedAt, finishedAt, snapshot))
            sourceMirror.upsertSnapshot(
                notes = snapshot.notes.map { it.withLastSeenSyncId(syncRunId) },
                cards = snapshot.cards.map { it.withLastSeenSyncId(syncRunId) },
            )
            syncRunId
        } catch (error: CollectionGatewayException) {
            val finishedAt = clock.nowMillis()
            syncRuns.insert(failureRun(startedAt, finishedAt, error))
        }
    }

    private fun successRun(
        startedAt: Long,
        finishedAt: Long,
        snapshot: CollectionSnapshot,
    ): SyncRun = SyncRun(
        id = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = SyncRunStatus.SUCCESS,
        activeNotesCount = snapshot.notes.size,
        activeCardsCount = snapshot.cards.size,
        suspendedCardsArchivedCount = 0,
        suspendedKanjiImportedCount = 0,
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = null,
        errorMessage = null,
        removalMessage = "",
    )

    private fun failureRun(
        startedAt: Long,
        finishedAt: Long,
        error: CollectionGatewayException,
    ): SyncRun = SyncRun(
        id = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = if (error.permanent) {
            SyncRunStatus.CONFIG_ERROR
        } else {
            SyncRunStatus.RETRYABLE_ERROR
        },
        activeNotesCount = 0,
        activeCardsCount = 0,
        suspendedCardsArchivedCount = 0,
        suspendedKanjiImportedCount = 0,
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = error.errorCode.wireName,
        errorMessage = error.message,
        removalMessage = "",
    )

    private fun SourceNote.withLastSeenSyncId(syncRunId: SyncRunId): SourceNote =
        copy(lastSeenSyncId = syncRunId)

    private fun SourceCard.withLastSeenSyncId(syncRunId: SyncRunId): SourceCard =
        copy(lastSeenSyncId = syncRunId)
}
