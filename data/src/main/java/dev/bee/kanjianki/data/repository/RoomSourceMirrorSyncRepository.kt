package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.history.SyncCardSnapshotDao
import dev.bee.kanjianki.data.history.SyncCardSnapshotEntity
import dev.bee.kanjianki.data.history.SyncNoteSnapshotDao
import dev.bee.kanjianki.data.history.SyncNoteSnapshotEntity
import dev.bee.kanjianki.data.importing.ImportDecisionDao
import dev.bee.kanjianki.data.importing.ImportRuleAuditDao
import dev.bee.kanjianki.data.importing.SuspendedArchiveDao
import dev.bee.kanjianki.data.importing.SuspendedArchiveEntity
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
    private val suspendedArchive: SuspendedArchiveDao,
    private val suspendedImports: SuspendedImportDao,
    private val suspendedSources: SuspendedSourceDao,
    private val syncNoteSnapshots: SyncNoteSnapshotDao,
    private val syncCardSnapshots: SyncCardSnapshotDao,
    private val runInTransaction: suspend (suspend () -> SyncRunId) -> SyncRunId,
) : SourceMirrorSyncRepository {
    constructor(database: KaniRoomDatabase) : this(
        syncRuns = database.syncRunDao(),
        sourceNotes = database.sourceNoteDao(),
        sourceCards = database.sourceCardDao(),
        importRuleAudits = database.importRuleAuditDao(),
        importDecisions = database.importDecisionDao(),
        suspendedArchive = database.suspendedArchiveDao(),
        suspendedImports = database.suspendedImportDao(),
        suspendedSources = database.suspendedSourceDao(),
        syncNoteSnapshots = database.syncNoteSnapshotDao(),
        syncCardSnapshots = database.syncCardSnapshotDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
    )

    override suspend fun recordSuccessfulSnapshot(
        syncRun: SyncRun,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        importCandidates: List<ImportedKanjiCandidate>,
        settings: ImportSettings,
    ): SyncRunId = runInTransaction {
        val finishedAt = requireNotNull(syncRun.finishedAt)
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
        recordImportEvidence(syncRunId, finishedAt, importCandidates, settings)
        recordSuspendedArchive(syncRunId, finishedAt, notes, cards, importCandidates)
        recordHistoricalSnapshots(syncRunId, syncRun.startedAt, finishedAt, notes, cards, settings)
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

    private suspend fun recordSuspendedArchive(
        syncRunId: SyncRunId,
        finishedAt: Long,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        importCandidates: List<ImportedKanjiCandidate>,
    ) {
        val selectedSuspendedCardIds = importCandidates.flatMap { candidate ->
            candidate.sources.filter { it.suspended }.map { it.cardId }
        }.toSet()
        if (selectedSuspendedCardIds.isEmpty()) {
            return
        }
        val notesById = notes.associateBy { it.noteId }
        val archiveRows = cards.mapNotNull { card ->
            if (!card.suspended || card.cardId !in selectedSuspendedCardIds) {
                return@mapNotNull null
            }
            val note = notesById[card.noteId] ?: return@mapNotNull null
            if (suspendedArchive.get(card.cardId.value) != null) {
                return@mapNotNull null
            }
            SuspendedArchiveEntity(
                cardId = card.cardId.value,
                noteId = card.noteId.value,
                deckName = card.deckName,
                modelName = note.modelName,
                expression = note.expression,
                reading = note.reading,
                meaning = note.meaning,
                sentence = note.sentence,
                fieldsJson = note.fieldsJson,
                archivedAt = finishedAt,
                archivedSyncId = syncRunId.value,
                restoredAt = null,
            )
        }
        if (archiveRows.isNotEmpty()) {
            suspendedArchive.upsertAll(archiveRows)
        }
    }

    private suspend fun recordHistoricalSnapshots(
        syncRunId: SyncRunId,
        startedAt: Long,
        finishedAt: Long,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        settings: ImportSettings,
    ) {
        val cardsByNoteId = cards.groupBy { it.noteId }
        syncNoteSnapshots.upsertAll(
            notes.map { note ->
                note.toSyncNoteSnapshotEntity(syncRunId, finishedAt, cardsByNoteId[note.noteId].orEmpty())
            },
        )
        val notesById = notes.associateBy { it.noteId }
        syncCardSnapshots.upsertAll(
            cards.map { card ->
                card.toSyncCardSnapshotEntity(
                    syncRunId = syncRunId,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    modelName = notesById[card.noteId]?.modelName.orEmpty(),
                    matureDays = settings.matureDays,
                )
            },
        )
    }

    private fun SourceNote.toSyncNoteSnapshotEntity(
        syncRunId: SyncRunId,
        finishedAt: Long,
        cards: List<SourceCard>,
    ): SyncNoteSnapshotEntity = SyncNoteSnapshotEntity(
        syncId = syncRunId.value,
        finishedAt = finishedAt,
        noteId = noteId.value,
        modelId = 0,
        modelName = modelName,
        deckIds = "",
        deckNames = cards.mapTo(linkedSetOf()) { it.deckName }.joinToString(" "),
        expression = expression,
        reading = reading,
        meaning = meaning,
        sentence = sentence,
        tags = tags,
        fieldsJson = fieldsJson,
        extractedKanji = extractKanji(expression).joinToString(""),
    )

    private fun SourceCard.toSyncCardSnapshotEntity(
        syncRunId: SyncRunId,
        startedAt: Long,
        finishedAt: Long,
        modelName: String,
        matureDays: Int,
    ): SyncCardSnapshotEntity = SyncCardSnapshotEntity(
        syncId = syncRunId.value,
        startedAt = startedAt,
        finishedAt = finishedAt,
        cardId = cardId.value,
        noteId = noteId.value,
        deckId = "",
        deckName = deckName,
        modelId = 0,
        modelName = modelName,
        ord = ord,
        queue = queue,
        type = type,
        due = due,
        intervalDays = intervalDays,
        reps = reps,
        lapses = lapses,
        suspended = if (suspended) 1 else 0,
        fsrsStability = fsrsStability,
        fsrsDifficulty = fsrsDifficulty,
        fsrsRetrievability = fsrsRetrievability,
        mature = if (mature(matureDays)) 1 else 0,
    )

    private fun extractKanji(value: String): List<String> {
        val out = linkedSetOf<String>()
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (isKanji(codePoint)) {
                out += String(Character.toChars(codePoint))
            }
            index += Character.charCount(codePoint)
        }
        return out.toList()
    }

    private fun isKanji(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2EBEF
}
