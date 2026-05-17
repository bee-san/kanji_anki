package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.importing.ImportCandidateSelector
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository

class RunSourceMirrorSyncUseCase(
    private val gateway: CollectionGateway,
    private val syncRuns: SyncRunRepository,
    private val sourceMirrorSync: SourceMirrorSyncRepository,
    private val importCandidateSelector: ImportCandidateSelector,
    private val clock: AppClock,
) {
    suspend operator fun invoke(settings: ImportSettings): SyncRunId {
        val startedAt = clock.nowMillis()
        return try {
            val snapshot = gateway.readCollection(settings)
            val importCandidates = importCandidateSelector.select(snapshot, settings)
            val finishedAt = clock.nowMillis()
            sourceMirrorSync.recordSuccessfulSnapshot(
                syncRun = successRun(startedAt, finishedAt, snapshot, importCandidates),
                notes = snapshot.notes,
                cards = snapshot.cards,
                importCandidates = importCandidates,
                settings = settings,
            )
        } catch (error: CollectionGatewayException) {
            val finishedAt = clock.nowMillis()
            syncRuns.insert(failureRun(startedAt, finishedAt, error))
        }
    }

    private fun successRun(
        startedAt: Long,
        finishedAt: Long,
        snapshot: CollectionSnapshot,
        importCandidates: List<ImportedKanjiCandidate>,
    ): SyncRun = SyncRun(
        id = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = SyncRunStatus.SUCCESS,
        activeNotesCount = snapshot.cards.filter(SourceCard::active).mapTo(mutableSetOf()) { it.noteId }.size,
        activeCardsCount = snapshot.cards.count(SourceCard::active),
        suspendedCardsArchivedCount = importCandidates.suspendedCardIds().size,
        suspendedKanjiImportedCount = importCandidates.count { it.hasSuspendedSource() },
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = null,
        errorMessage = null,
        removalMessage = "",
    )

    private fun List<ImportedKanjiCandidate>.suspendedCardIds() =
        flatMap { candidate ->
            candidate.sources.filter { it.suspended }.map { it.cardId }
        }.toSet()

    private fun ImportedKanjiCandidate.hasSuspendedSource(): Boolean =
        sources.any { it.suspended }

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

}
