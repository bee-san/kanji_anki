package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.importing.ImportDecisionDao
import dev.bee.kanjianki.data.importing.ImportRuleAuditDao
import dev.bee.kanjianki.data.importing.SuspendedImportDao
import dev.bee.kanjianki.data.importing.SuspendedSourceDao
import dev.bee.kanjianki.data.source.SourceCardDao
import dev.bee.kanjianki.data.source.SourceNoteDao
import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository

class RoomSourceMirrorSyncRepository internal constructor(
    private val syncRuns: SyncRunDao,
    private val sourceNotes: SourceNoteDao,
    private val sourceCards: SourceCardDao,
    private val importRuleAudits: ImportRuleAuditDao,
    private val importDecisions: ImportDecisionDao,
    private val suspendedImports: SuspendedImportDao,
    private val suspendedSources: SuspendedSourceDao,
    private val runInTransaction: suspend (suspend () -> SyncRunId) -> SyncRunId,
) : SourceMirrorSyncRepository {
    constructor(database: KaniRoomDatabase) : this(
        syncRuns = database.syncRunDao(),
        sourceNotes = database.sourceNoteDao(),
        sourceCards = database.sourceCardDao(),
        importRuleAudits = database.importRuleAuditDao(),
        importDecisions = database.importDecisionDao(),
        suspendedImports = database.suspendedImportDao(),
        suspendedSources = database.suspendedSourceDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
    )

    override suspend fun recordSuccessfulSnapshot(
        syncRun: SyncRun,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        importCandidates: List<ImportedKanjiCandidate>,
        settings: ImportSettings,
    ): SyncRunId = runInTransaction {
        val previousNoteIds = sourceNotes.listIds().toSet()
        val previousCardIds = sourceCards.listIds().toSet()
        val currentNoteIds = notes.mapTo(mutableSetOf()) { it.noteId.value }
        val currentCardIds = cards.mapTo(mutableSetOf()) { it.cardId.value }
        val syncRunId = SyncRunId(
            syncRuns.insert(
                syncRun.copy(
                    id = null,
                    deletedNotesCount = (previousNoteIds - currentNoteIds).size,
                    deletedCardsCount = (previousCardIds - currentCardIds).size,
                ).toEntity(),
            ),
        )
        sourceCards.deleteAll()
        sourceNotes.deleteAll()
        sourceNotes.upsertAll(notes.map { it.copy(lastSeenSyncId = syncRunId).toEntity() })
        sourceCards.upsertAll(cards.map { it.copy(lastSeenSyncId = syncRunId).toEntity() })
        recordImportEvidence(syncRunId, requireNotNull(syncRun.finishedAt), importCandidates, settings)
        syncRunId
    }

    private suspend fun recordImportEvidence(
        syncRunId: SyncRunId,
        finishedAt: Long,
        importCandidates: List<ImportedKanjiCandidate>,
        settings: ImportSettings,
    ) {
        importRuleAudits.upsert(settings.toRuleAuditEntity(syncRunId, finishedAt))
        if (importCandidates.isNotEmpty()) {
            importDecisions.upsertAll(
                importCandidates.map { candidate ->
                    candidate.toImportDecisionEntity(syncRunId, settings, finishedAt)
                },
            )
        }

        val suspendedCandidates = importCandidates.mapNotNull { it.suspendedOnly() }
        if (suspendedCandidates.isEmpty()) {
            return
        }
        suspendedImports.upsertAll(
            suspendedCandidates.map { candidate ->
                val existing = suspendedImports.get(candidate.kanji)
                candidate.toSuspendedImportEntity(
                    syncRunId = syncRunId,
                    firstImportedAt = existing?.firstImportedAt ?: finishedAt,
                )
            },
        )
        for (candidate in suspendedCandidates) {
            suspendedSources.deleteForKanji(candidate.kanji)
        }
        suspendedSources.upsertAll(
            suspendedCandidates.flatMap { candidate ->
                candidate.toSuspendedSourceEntities(syncRunId)
            },
        )
    }
}
