package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.history.SyncCardSnapshotDao
import dev.bee.kanjianki.data.history.SyncCardSnapshotEntity
import dev.bee.kanjianki.data.history.SyncNoteSnapshotDao
import dev.bee.kanjianki.data.history.SyncNoteSnapshotEntity
import dev.bee.kanjianki.data.importing.ImportDecisionDao
import dev.bee.kanjianki.data.importing.ImportDecisionEntity
import dev.bee.kanjianki.data.importing.ImportRuleAuditDao
import dev.bee.kanjianki.data.importing.ImportRuleAuditEntity
import dev.bee.kanjianki.data.importing.SuspendedArchiveDao
import dev.bee.kanjianki.data.importing.SuspendedArchiveEntity
import dev.bee.kanjianki.data.importing.SuspendedImportDao
import dev.bee.kanjianki.data.importing.SuspendedImportEntity
import dev.bee.kanjianki.data.importing.SuspendedSourceDao
import dev.bee.kanjianki.data.importing.SuspendedSourceEntity
import dev.bee.kanjianki.data.source.SourceCardDao
import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteDao
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
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
        val audits = FakeImportRuleAuditDao()
        val decisions = FakeImportDecisionDao()
        val archive = FakeSuspendedArchiveDao()
        val suspendedImports = FakeSuspendedImportDao(
            existing = mapOf(
                "日" to SuspendedImportEntity(
                    kanji = "日",
                    jitenRank = 120,
                    rankKnown = 1,
                    cutoffUsed = 3000,
                    firstImportedAt = 5,
                    lastSeenSyncId = 3,
                ),
            ),
        )
        val suspendedSources = FakeSuspendedSourceDao()
        val syncNoteSnapshots = FakeSyncNoteSnapshotDao()
        val syncCardSnapshots = FakeSyncCardSnapshotDao()
        var transactions = 0
        val repository = RoomSourceMirrorSyncRepository(
            syncRuns = syncRuns,
            sourceNotes = notes,
            sourceCards = cards,
            importRuleAudits = audits,
            importDecisions = decisions,
            suspendedArchive = archive,
            suspendedImports = suspendedImports,
            suspendedSources = suspendedSources,
            syncNoteSnapshots = syncNoteSnapshots,
            syncCardSnapshots = syncCardSnapshots,
            runInTransaction = { block ->
                transactions++
                block()
            },
        )

        val id = repository.recordSuccessfulSnapshot(
            syncRun = successRun(),
            notes = listOf(sourceNote(1), sourceNote(2)),
            cards = listOf(sourceCard(10, noteId = 1), sourceCard(20, noteId = 2)),
            importCandidates = listOf(
                importCandidate(
                    kanji = "日",
                    sources = listOf(
                        sourceEvidence(kanji = "日", cardId = 10, noteId = 1, sourceType = ImportSource.SUSPENDED),
                        sourceEvidence(kanji = "日", cardId = 20, noteId = 2, sourceType = ImportSource.ACTIVE),
                    ),
                ),
                importCandidate(
                    kanji = "本",
                    jitenRank = 200,
                    sources = listOf(sourceEvidence(kanji = "本", cardId = 20, noteId = 2, sourceType = ImportSource.ACTIVE)),
                ),
            ),
            settings = ImportSettings(importActiveCards = true),
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
        assertEquals(42L, audits.upserted.single().syncId)
        assertEquals("active suspended", audits.upserted.single().enabledSources)
        assertEquals(listOf("日", "本"), decisions.upserted.map { it.kanji })
        assertEquals("multiple_import_rules", decisions.upserted.first { it.kanji == "日" }.reasonCode)
        assertEquals("10 20", decisions.upserted.first { it.kanji == "日" }.sourceCardIds)
        assertEquals(listOf("日"), suspendedImports.upserted.map { it.kanji })
        assertEquals(5L, suspendedImports.upserted.single().firstImportedAt)
        assertEquals(listOf("日"), suspendedSources.deletedForKanji)
        assertEquals(listOf(10L), suspendedSources.upserted.map { it.cardId })
        assertEquals(42L, suspendedSources.upserted.single().syncId)
        assertEquals(listOf(10L), archive.upserted.map { it.cardId })
        assertEquals("Kiku", archive.upserted.single().modelName)
        assertEquals(20L, archive.upserted.single().archivedAt)
        assertEquals(42L, archive.upserted.single().archivedSyncId)
        assertEquals(listOf(1L, 2L), syncNoteSnapshots.upserted.map { it.noteId })
        assertEquals(listOf("日本", "日本"), syncNoteSnapshots.upserted.map { it.extractedKanji })
        assertEquals(listOf(10L, 20L), syncCardSnapshots.upserted.map { it.cardId })
        assertEquals(listOf(1, 0), syncCardSnapshots.upserted.map { it.suspended })
        assertEquals(listOf(0, 0), syncCardSnapshots.upserted.map { it.mature })
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

    private class FakeImportRuleAuditDao : ImportRuleAuditDao {
        val upserted = mutableListOf<ImportRuleAuditEntity>()

        override suspend fun get(syncId: Long): ImportRuleAuditEntity? =
            upserted.firstOrNull { it.syncId == syncId }

        override suspend fun latest(): ImportRuleAuditEntity? =
            upserted.maxByOrNull { it.syncId }

        override suspend fun upsert(audit: ImportRuleAuditEntity) {
            upserted.removeAll { it.syncId == audit.syncId }
            upserted += audit
        }
    }

    private class FakeImportDecisionDao : ImportDecisionDao {
        val upserted = mutableListOf<ImportDecisionEntity>()

        override suspend fun listForSync(syncId: Long): List<ImportDecisionEntity> =
            upserted.filter { it.syncId == syncId }

        override suspend fun listForKanji(kanji: String): List<ImportDecisionEntity> =
            upserted.filter { it.kanji == kanji }.sortedByDescending { it.syncId }

        override suspend fun upsertAll(decisions: List<ImportDecisionEntity>) {
            for (decision in decisions) {
                upserted.removeAll { it.syncId == decision.syncId && it.kanji == decision.kanji }
                upserted += decision
            }
        }
    }

    private class FakeSuspendedArchiveDao(
        private val existing: Map<Long, SuspendedArchiveEntity> = emptyMap(),
    ) : SuspendedArchiveDao {
        val upserted = mutableListOf<SuspendedArchiveEntity>()

        override fun observe(cardId: Long): Flow<SuspendedArchiveEntity?> = emptyFlow()

        override suspend fun get(cardId: Long): SuspendedArchiveEntity? =
            upserted.firstOrNull { it.cardId == cardId } ?: existing[cardId]

        override suspend fun listActive(): List<SuspendedArchiveEntity> =
            (existing.values + upserted).filter { it.restoredAt == null }
                .sortedWith(compareByDescending<SuspendedArchiveEntity> { it.archivedAt }.thenBy { it.cardId })

        override suspend fun upsert(entry: SuspendedArchiveEntity) {
            upserted.removeAll { it.cardId == entry.cardId }
            upserted += entry
        }

        override suspend fun upsertAll(entries: List<SuspendedArchiveEntity>) {
            for (entry in entries) {
                upsert(entry)
            }
        }
    }

    private class FakeSuspendedImportDao(
        private val existing: Map<String, SuspendedImportEntity> = emptyMap(),
    ) : SuspendedImportDao {
        val upserted = mutableListOf<SuspendedImportEntity>()

        override fun observe(kanji: String): Flow<SuspendedImportEntity?> = emptyFlow()

        override suspend fun get(kanji: String): SuspendedImportEntity? =
            upserted.firstOrNull { it.kanji == kanji } ?: existing[kanji]

        override suspend fun listRanked(): List<SuspendedImportEntity> =
            (existing.values + upserted).sortedWith(compareBy<SuspendedImportEntity> { it.jitenRank ?: Int.MAX_VALUE }.thenBy { it.kanji })

        override suspend fun upsert(entry: SuspendedImportEntity) {
            upserted.removeAll { it.kanji == entry.kanji }
            upserted += entry
        }

        override suspend fun upsertAll(entries: List<SuspendedImportEntity>) {
            for (entry in entries) {
                upsert(entry)
            }
        }
    }

    private class FakeSuspendedSourceDao : SuspendedSourceDao {
        val deletedForKanji = mutableListOf<String>()
        val upserted = mutableListOf<SuspendedSourceEntity>()

        override suspend fun listForKanji(kanji: String): List<SuspendedSourceEntity> =
            upserted.filter { it.kanji == kanji }.sortedBy { it.cardId }

        override suspend fun listForSync(syncId: Long): List<SuspendedSourceEntity> =
            upserted.filter { it.syncId == syncId }.sortedWith(compareBy<SuspendedSourceEntity> { it.kanji }.thenBy { it.cardId })

        override suspend fun upsertAll(sources: List<SuspendedSourceEntity>) {
            for (source in sources) {
                upserted.removeAll { it.kanji == source.kanji && it.cardId == source.cardId }
                upserted += source
            }
        }

        override suspend fun deleteForKanji(kanji: String) {
            deletedForKanji += kanji
            upserted.removeAll { it.kanji == kanji }
        }
    }

    private class FakeSyncNoteSnapshotDao : SyncNoteSnapshotDao {
        val upserted = mutableListOf<SyncNoteSnapshotEntity>()

        override suspend fun listForSync(syncId: Long): List<SyncNoteSnapshotEntity> =
            upserted.filter { it.syncId == syncId }.sortedBy { it.noteId }

        override suspend fun upsertAll(notes: List<SyncNoteSnapshotEntity>) {
            for (note in notes) {
                upserted.removeAll { it.syncId == note.syncId && it.noteId == note.noteId }
                upserted += note
            }
        }
    }

    private class FakeSyncCardSnapshotDao : SyncCardSnapshotDao {
        val upserted = mutableListOf<SyncCardSnapshotEntity>()

        override suspend fun listForSync(syncId: Long): List<SyncCardSnapshotEntity> =
            upserted.filter { it.syncId == syncId }.sortedBy { it.cardId }

        override suspend fun upsertAll(cards: List<SyncCardSnapshotEntity>) {
            for (card in cards) {
                upserted.removeAll { it.syncId == card.syncId && it.cardId == card.cardId }
                upserted += card
            }
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

    private fun importCandidate(
        kanji: String,
        jitenRank: Int = 120,
        sources: List<ImportSourceEvidence>,
    ): ImportedKanjiCandidate = ImportedKanjiCandidate(
        kanji = kanji,
        jitenRank = jitenRank,
        rankRangeMax = 3000,
        sources = sources,
    )

    private fun sourceEvidence(
        kanji: String,
        cardId: Long,
        noteId: Long,
        sourceType: ImportSource,
    ): ImportSourceEvidence = ImportSourceEvidence(
        kanji = kanji,
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        expression = "日本",
        reading = "にほん",
        meaning = "Japan",
        sentence = "日本へ行く。",
        sourceType = sourceType,
        suspended = sourceType == ImportSource.SUSPENDED,
        forcePractice = sourceType != ImportSource.ACTIVE,
        mature = false,
        lapses = 0,
        intervalDays = 0,
        reps = 0,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        ruleTypes = setOf(sourceType),
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
