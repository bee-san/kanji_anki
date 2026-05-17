package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.importing.ImportCandidateSelector
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository
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
        val gateway = FakeGateway(
            CollectionSnapshot(
                notes = listOf(sourceNote(noteId = 10), sourceNote(noteId = 11)),
                cards = listOf(
                    sourceCard(noteId = 10, suspended = false),
                    sourceCard(cardId = 21, noteId = 11, suspended = true),
                ),
            ),
        )
        val syncRuns = FakeSyncRunRepository()
        val sourceMirrorSync = FakeSourceMirrorSyncRepository()
        val useCase = RunSourceMirrorSyncUseCase(
            gateway,
            syncRuns,
            sourceMirrorSync,
            importSelector(),
            FakeClock(100, 150),
        )

        val id = useCase(ImportSettings())

        assertEquals(SyncRunId(1), id)
        assertTrue(syncRuns.inserted.isEmpty())
        assertEquals(SyncRunStatus.SUCCESS, sourceMirrorSync.syncRun.status)
        assertEquals(1, sourceMirrorSync.syncRun.activeNotesCount)
        assertEquals(1, sourceMirrorSync.syncRun.activeCardsCount)
        assertEquals(1, sourceMirrorSync.syncRun.suspendedCardsArchivedCount)
        assertEquals(2, sourceMirrorSync.syncRun.suspendedKanjiImportedCount)
        assertEquals(SyncRunId(1), sourceMirrorSync.notes.single { it.noteId == NoteId(10) }.lastSeenSyncId)
        assertEquals(SyncRunId(1), sourceMirrorSync.cards.single { it.cardId == CardId(20) }.lastSeenSyncId)
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
        val sourceMirrorSync = FakeSourceMirrorSyncRepository()
        val useCase = RunSourceMirrorSyncUseCase(
            gateway,
            syncRuns,
            sourceMirrorSync,
            importSelector(),
            FakeClock(10, 20),
        )

        val id = useCase(ImportSettings())

        assertEquals(SyncRunId(1), id)
        assertEquals(SyncRunStatus.CONFIG_ERROR, syncRuns.inserted.single().status)
        assertEquals("permanent_permission", syncRuns.inserted.single().errorCode)
        assertTrue(sourceMirrorSync.notes.isEmpty())
        assertTrue(sourceMirrorSync.cards.isEmpty())
    }

    private fun importSelector(): ImportCandidateSelector =
        ImportCandidateSelector { kanji ->
            when (kanji) {
                "日" -> 100
                "本" -> 200
                else -> null
            }
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

    private class FakeSourceMirrorSyncRepository : SourceMirrorSyncRepository {
        val notes = mutableListOf<SourceNote>()
        val cards = mutableListOf<SourceCard>()
        lateinit var syncRun: SyncRun

        override suspend fun recordSuccessfulSnapshot(
            syncRun: SyncRun,
            notes: List<SourceNote>,
            cards: List<SourceCard>,
        ): SyncRunId {
            val id = SyncRunId(1)
            this.syncRun = syncRun.copy(id = id)
            this.notes += notes.map { it.copy(lastSeenSyncId = id) }
            this.cards += cards.map { it.copy(lastSeenSyncId = id) }
            return id
        }
    }

    private fun sourceNote(noteId: Long = 10): SourceNote = SourceNote(
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
        cardId: Long = 20,
        noteId: Long = 10,
        suspended: Boolean = true,
    ): SourceCard = SourceCard(
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        deckName = "Mining",
        ord = 0,
        queue = if (suspended) -1 else 0,
        type = 2,
        due = 0,
        intervalDays = 0,
        reps = 0,
        lapses = 0,
        suspended = suspended,
        browserQueryMatched = false,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(0),
    )
}
