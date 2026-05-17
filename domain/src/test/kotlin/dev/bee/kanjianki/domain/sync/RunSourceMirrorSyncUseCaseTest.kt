package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSourceMirrorSyncUseCaseTest {
    @Test
    fun successfulReadWritesSyncRunThenSourceSnapshotWithGeneratedSyncId() = runBlocking {
        val gateway = FakeGateway(CollectionSnapshot(listOf(sourceNote()), listOf(sourceCard())))
        val syncRuns = FakeSyncRunRepository()
        val sourceMirror = FakeSourceMirrorRepository()
        val useCase = RunSourceMirrorSyncUseCase(gateway, syncRuns, sourceMirror, FakeClock(100, 150))

        val id = useCase(ImportSettings())

        assertEquals(SyncRunId(1), id)
        assertEquals(SyncRunStatus.SUCCESS, syncRuns.inserted.single().status)
        assertEquals(1, syncRuns.inserted.single().activeNotesCount)
        assertEquals(SyncRunId(1), sourceMirror.notes.single().lastSeenSyncId)
        assertEquals(SyncRunId(1), sourceMirror.cards.single().lastSeenSyncId)
    }

    @Test
    fun gatewayFailureWritesFailedSyncRunWithoutSourceSnapshot() = runBlocking {
        val gateway = FakeGateway(
            failure = CollectionGatewayException(
                errorCode = SyncErrorCode.PERMANENT_PERMISSION,
                permanent = true,
                message = "missing permission",
            ),
        )
        val syncRuns = FakeSyncRunRepository()
        val sourceMirror = FakeSourceMirrorRepository()
        val useCase = RunSourceMirrorSyncUseCase(gateway, syncRuns, sourceMirror, FakeClock(10, 20))

        val id = useCase(ImportSettings())

        assertEquals(SyncRunId(1), id)
        assertEquals(SyncRunStatus.CONFIG_ERROR, syncRuns.inserted.single().status)
        assertEquals("permanent_permission", syncRuns.inserted.single().errorCode)
        assertTrue(sourceMirror.notes.isEmpty())
        assertTrue(sourceMirror.cards.isEmpty())
    }

    private class FakeGateway(
        private val snapshot: CollectionSnapshot? = null,
        private val failure: CollectionGatewayException? = null,
    ) : CollectionGateway {
        override suspend fun readCollection(settings: ImportSettings): CollectionSnapshot {
            failure?.let { throw it }
            return requireNotNull(snapshot)
        }
    }

    private class FakeClock(private vararg val values: Long) : AppClock {
        private var index = 0

        override fun nowMillis(): Long = values[index++]
    }

    private class FakeSyncRunRepository : SyncRunRepository {
        val inserted = mutableListOf<SyncRun>()

        override fun observeLatest(): Flow<SyncRun?> = emptyFlow()

        override suspend fun get(id: SyncRunId): SyncRun? = inserted.firstOrNull { it.id == id }

        override suspend fun latest(): SyncRun? = inserted.lastOrNull()

        override suspend fun insert(syncRun: SyncRun): SyncRunId {
            val id = SyncRunId((inserted.size + 1).toLong())
            inserted += syncRun.copy(id = id)
            return id
        }

        override suspend fun update(syncRun: SyncRun) = Unit
    }

    private class FakeSourceMirrorRepository : SourceMirrorRepository {
        val notes = mutableListOf<SourceNote>()
        val cards = mutableListOf<SourceCard>()

        override fun observeNote(noteId: NoteId): Flow<SourceNote?> = emptyFlow()

        override suspend fun getNote(noteId: NoteId): SourceNote? = notes.firstOrNull { it.noteId == noteId }

        override suspend fun getCard(cardId: CardId): SourceCard? = cards.firstOrNull { it.cardId == cardId }

        override suspend fun listNotesForSync(syncRunId: SyncRunId): List<SourceNote> =
            notes.filter { it.lastSeenSyncId == syncRunId }

        override suspend fun listCardsForNote(noteId: NoteId): List<SourceCard> =
            cards.filter { it.noteId == noteId }

        override suspend fun listCardsForSync(syncRunId: SyncRunId): List<SourceCard> =
            cards.filter { it.lastSeenSyncId == syncRunId }

        override suspend fun upsertSnapshot(
            notes: List<SourceNote>,
            cards: List<SourceCard>,
        ) {
            this.notes += notes
            this.cards += cards
        }
    }

    private fun sourceNote(): SourceNote = SourceNote(
        noteId = NoteId(10),
        modelName = "Kiku",
        expression = "日本",
        reading = "にほん",
        meaning = "Japan",
        sentence = "",
        fieldsJson = "{}",
        tags = "",
        lastSeenSyncId = SyncRunId(0),
    )

    private fun sourceCard(): SourceCard = SourceCard(
        cardId = CardId(20),
        noteId = NoteId(10),
        deckName = "Mining",
        ord = 0,
        queue = -1,
        type = 2,
        due = 0,
        intervalDays = 0,
        reps = 0,
        lapses = 0,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(0),
    )
}
