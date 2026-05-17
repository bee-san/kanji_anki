package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.source.SourceCardDao
import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteDao
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSourceMirrorSyncRepositoryTest {
    @Test
    fun recordSuccessfulSnapshotRunsTransactionAndReplacesSourceMirror() = runBlocking {
        val syncRuns = FakeSyncRunDao(generatedId = 42)
        val notes = FakeSourceNoteDao(existingIds = listOf(1, 99))
        val cards = FakeSourceCardDao(existingIds = listOf(10, 999))
        var transactions = 0
        val repository = RoomSourceMirrorSyncRepository(
            syncRuns = syncRuns,
            sourceNotes = notes,
            sourceCards = cards,
            runInTransaction = { block ->
                transactions++
                block()
            },
        )

        val id = repository.recordSuccessfulSnapshot(
            syncRun = successRun(),
            notes = listOf(sourceNote(1), sourceNote(2)),
            cards = listOf(sourceCard(10, noteId = 1), sourceCard(20, noteId = 2)),
        )

        assertEquals(SyncRunId(42), id)
        assertEquals(1, transactions)
        assertEquals(1, syncRuns.inserted.single().deletedNotesCount)
        assertEquals(1, syncRuns.inserted.single().deletedCardsCount)
        assertTrue(cards.deletedAll)
        assertTrue(notes.deletedAll)
        assertEquals(listOf(1L, 2L), notes.upserted.map { it.noteId })
        assertEquals(listOf(42L, 42L), notes.upserted.map { it.lastSeenSyncId })
        assertEquals(listOf(10L, 20L), cards.upserted.map { it.cardId })
        assertEquals(listOf(true, false), cards.upserted.map { it.suspended })
        assertEquals(listOf(false, true), cards.upserted.map { it.browserQueryMatched })
        assertEquals(listOf(42L, 42L), cards.upserted.map { it.lastSeenSyncId })
    }

    private class FakeSyncRunDao(
        private val generatedId: Long,
    ) : SyncRunDao {
        val inserted = mutableListOf<SyncRunEntity>()

        override fun observeLatest(): Flow<SyncRunEntity?> = emptyFlow()

        override suspend fun get(id: Long): SyncRunEntity? = inserted.firstOrNull { it.id == id }

        override suspend fun latest(): SyncRunEntity? = inserted.lastOrNull()

        override suspend fun insert(syncRun: SyncRunEntity): Long {
            inserted += syncRun.copy(id = generatedId)
            return generatedId
        }

        override suspend fun update(syncRun: SyncRunEntity) = Unit
    }

    private class FakeSourceNoteDao(
        private val existingIds: List<Long>,
    ) : SourceNoteDao {
        val upserted = mutableListOf<SourceNoteEntity>()
        var deletedAll = false

        override fun observe(noteId: Long): Flow<SourceNoteEntity?> = emptyFlow()

        override suspend fun get(noteId: Long): SourceNoteEntity? = upserted.firstOrNull { it.noteId == noteId }

        override suspend fun listForSync(syncId: Long): List<SourceNoteEntity> =
            upserted.filter { it.lastSeenSyncId == syncId }

        override suspend fun listIds(): List<Long> = existingIds

        override suspend fun upsert(note: SourceNoteEntity) {
            upserted += note
        }

        override suspend fun upsertAll(notes: List<SourceNoteEntity>) {
            upserted += notes
        }

        override suspend fun deleteAll() {
            deletedAll = true
            upserted.clear()
        }
    }

    private class FakeSourceCardDao(
        private val existingIds: List<Long>,
    ) : SourceCardDao {
        val upserted = mutableListOf<SourceCardEntity>()
        var deletedAll = false

        override fun observe(cardId: Long): Flow<SourceCardEntity?> = emptyFlow()

        override suspend fun get(cardId: Long): SourceCardEntity? = upserted.firstOrNull { it.cardId == cardId }

        override suspend fun listForNote(noteId: Long): List<SourceCardEntity> =
            upserted.filter { it.noteId == noteId }

        override suspend fun listForSync(syncId: Long): List<SourceCardEntity> =
            upserted.filter { it.lastSeenSyncId == syncId }

        override suspend fun listIds(): List<Long> = existingIds

        override suspend fun upsert(card: SourceCardEntity) {
            upserted += card
        }

        override suspend fun upsertAll(cards: List<SourceCardEntity>) {
            upserted += cards
        }

        override suspend fun deleteAll() {
            deletedAll = true
            upserted.clear()
        }
    }

    private fun successRun(): SyncRun = SyncRun(
        id = null,
        startedAt = 10,
        finishedAt = 20,
        status = SyncRunStatus.SUCCESS,
        activeNotesCount = 2,
        activeCardsCount = 2,
        suspendedCardsArchivedCount = 0,
        suspendedKanjiImportedCount = 0,
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = null,
        errorMessage = null,
        removalMessage = "",
    )

    private fun sourceNote(noteId: Long): SourceNote = SourceNote(
        noteId = NoteId(noteId),
        modelName = "Kiku",
        expression = "日本",
        reading = "にほん",
        meaning = "Japan",
        sentence = "",
        fieldsJson = "{}",
        tags = "",
        lastSeenSyncId = SyncRunId(0),
    )

    private fun sourceCard(
        cardId: Long,
        noteId: Long,
    ): SourceCard = SourceCard(
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        deckName = "Mining",
        ord = 0,
        queue = if (cardId == 10L) -1 else 0,
        type = 2,
        due = 0,
        intervalDays = 0,
        reps = 0,
        lapses = 0,
        suspended = cardId == 10L,
        browserQueryMatched = cardId == 20L,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(0),
    )
}
