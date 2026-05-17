package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.source.SourceCardDao
import dev.bee.kanjianki.data.source.SourceNoteDao
import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository

class RoomSourceMirrorSyncRepository internal constructor(
    private val syncRuns: SyncRunDao,
    private val sourceNotes: SourceNoteDao,
    private val sourceCards: SourceCardDao,
    private val runInTransaction: suspend (suspend () -> SyncRunId) -> SyncRunId,
) : SourceMirrorSyncRepository {
    constructor(database: KaniRoomDatabase) : this(
        syncRuns = database.syncRunDao(),
        sourceNotes = database.sourceNoteDao(),
        sourceCards = database.sourceCardDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
    )

    override suspend fun recordSuccessfulSnapshot(
        syncRun: SyncRun,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
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
        syncRunId
    }
}
