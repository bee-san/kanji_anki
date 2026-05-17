package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.history.SyncCardSnapshotDao
import dev.bee.kanjianki.data.history.SyncCardSnapshotEntity
import dev.bee.kanjianki.data.history.SyncKanjiSnapshotDao
import dev.bee.kanjianki.data.history.SyncKanjiSnapshotEntity
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
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.data.StudyQueueMutationGate
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.inventory.KanjiInventoryDao
import dev.bee.kanjianki.data.inventory.KanjiInventoryEntity
import dev.bee.kanjianki.data.source.SourceCardDao
import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteDao
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.similar.SimilarKanjiPairEntity
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiPair
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.StudyQueueSeedBuilder
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
        val dashboardRows = FakeDashboardRowDao()
        val kanjiExamples = FakeKanjiExampleDao()
        val kanjiInventory = FakeKanjiInventoryDao(
            existing = listOf(
                KanjiInventoryEntity(
                    kanji = "古",
                    primaryMeaning = "old",
                    readings = "ふる",
                    browserSearch = "note:Kiku Expression:*古*",
                    searchText = "古 old",
                    sourceCount = 9,
                    exampleCount = 8,
                    firstSeenAt = 4,
                    lastSeenAt = 5,
                ),
            ),
        )
        val syncNoteSnapshots = FakeSyncNoteSnapshotDao()
        val syncCardSnapshots = FakeSyncCardSnapshotDao()
        val syncKanjiSnapshots = FakeSyncKanjiSnapshotDao()
        val studyItems = FakeStudyItemDao()
        studyItems.upserted += studyQueueItem("火").toEntity()
        val similarKanjiPairs = FakeSimilarKanjiPairDao(
            existing = listOf(
                SimilarKanjiPairEntity(
                    kanjiA = "古",
                    kanjiB = "日",
                    source = "fixture",
                    firstSeenAt = 7,
                    lastSeenAt = 8,
                ),
            ),
        )
        var transactions = 0
        val gateEvents = mutableListOf<String>()
        val seedExistingKanji = mutableListOf<List<String>>()
        val repository = RoomSourceMirrorSyncRepository(
            syncRuns = syncRuns,
            sourceNotes = notes,
            sourceCards = cards,
            importRuleAudits = audits,
            importDecisions = decisions,
            suspendedArchive = archive,
            suspendedImports = suspendedImports,
            suspendedSources = suspendedSources,
            dashboardRows = dashboardRows,
            kanjiExamples = kanjiExamples,
            kanjiInventory = kanjiInventory,
            syncNoteSnapshots = syncNoteSnapshots,
            syncCardSnapshots = syncCardSnapshots,
            syncKanjiSnapshots = syncKanjiSnapshots,
            studyItems = studyItems,
            similarKanjiPairs = similarKanjiPairs,
            studyQueueMutationGate = RecordingStudyQueueMutationGate(gateEvents),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block ->
                transactions++
                block()
            },
        )

        val id = repository.recordSuccessfulSnapshot(
            syncRun = successRun(),
            notes = listOf(
                sourceNote(1),
                sourceNote(2),
                sourceNote(3, expression = "かな", sentence = "語だけをかなにする。"),
            ),
            cards = listOf(
                sourceCard(
                    cardId = 10,
                    noteId = 1,
                    intervalDays = 9,
                    reps = 8,
                    lapses = 2,
                    suspended = true,
                    fsrsStability = 4.0,
                    fsrsDifficulty = 6.0,
                    fsrsRetrievability = 0.75,
                ),
                sourceCard(20, noteId = 2),
                sourceCard(
                    cardId = 30,
                    noteId = 3,
                    intervalDays = 45,
                    reps = 12,
                    lapses = 1,
                    suspended = false,
                    fsrsStability = 10.0,
                    fsrsDifficulty = 4.0,
                    fsrsRetrievability = 0.8,
                ),
            ),
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
            dashboardRows = listOf(dashboardRow("日")),
            settings = ImportSettings(importActiveCards = true),
            queueSeedBuilder = StudyQueueSeedBuilder { existingItems ->
                seedExistingKanji += existingItems.map { it.kanji }
                listOf(studyQueueItem("日"))
            },
            similarKanjiIndex = FakeSimilarKanjiIndex(
                listOf(
                    SimilarKanjiPair.canonical("日", "本", "fixture"),
                    SimilarKanjiPair.canonical("日", "古", "fixture"),
                    SimilarKanjiPair.canonical("日", "外", "fixture"),
                ),
            ),
        )

        assertEquals(SyncRunId(42), id)
        assertEquals(listOf("gate"), gateEvents)
        assertEquals(listOf(listOf("火")), seedExistingKanji)
        assertEquals(1, transactions)
        assertEquals(1, syncRuns.inserted.single().deletedNotesCount)
        assertEquals(1, syncRuns.inserted.single().deletedCardsCount)
        assertTrue(cards.deletedAll)
        assertTrue(notes.deletedAll)
        assertEquals(listOf(1L, 2L, 3L), notes.upserted.map { it.noteId })
        assertEquals(listOf(42L, 42L, 42L), notes.upserted.map { it.lastSeenSyncId })
        assertEquals(listOf(10L, 20L, 30L), cards.upserted.map { it.cardId })
        assertEquals(listOf(true, false, false), cards.upserted.map { it.suspended })
        assertEquals(listOf(false, true, false), cards.upserted.map { it.browserQueryMatched })
        assertEquals(listOf(42L, 42L, 42L), cards.upserted.map { it.lastSeenSyncId })
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
        assertEquals(listOf(1L, 2L, 3L), syncNoteSnapshots.upserted.map { it.noteId })
        assertEquals(listOf("日本", "日本", "語"), syncNoteSnapshots.upserted.map { it.extractedKanji })
        assertEquals(listOf(10L, 20L, 30L), syncCardSnapshots.upserted.map { it.cardId })
        assertEquals(listOf(1, 0, 0), syncCardSnapshots.upserted.map { it.suspended })
        assertEquals(listOf(0, 0, 1), syncCardSnapshots.upserted.map { it.mature })
        assertEquals(setOf("日", "本", "語"), syncKanjiSnapshots.upserted.map { it.kanji }.toSet())
        val historicalDay = syncKanjiSnapshots.upserted.single { it.kanji == "日" }
        assertEquals(42L, historicalDay.syncId)
        assertEquals(20L, historicalDay.finishedAt)
        assertEquals(1, historicalDay.activeCards)
        assertEquals(1, historicalDay.suspendedCards)
        assertEquals(4.5, historicalDay.averageIntervalDays, 0.0)
        assertEquals(2, historicalDay.totalLapses)
        assertEquals(8, historicalDay.totalReps)
        assertEquals(4.0, historicalDay.fsrsStabilityAvg!!, 0.0)
        assertEquals(6.0, historicalDay.fsrsDifficultyAvg!!, 0.0)
        assertEquals(0.75, historicalDay.fsrsRetrievabilityAvg!!, 0.0)
        assertEquals(22, historicalDay.weaknessScore)
        assertEquals("suspended_archive", historicalDay.reasonCode)
        val historicalBook = syncKanjiSnapshots.upserted.single { it.kanji == "本" }
        assertEquals(1, historicalBook.activeCards)
        assertEquals(1, historicalBook.suspendedCards)
        assertEquals(0, historicalBook.weaknessScore)
        assertEquals("", historicalBook.reasonCode)
        val historicalSentenceOnly = syncKanjiSnapshots.upserted.single { it.kanji == "語" }
        assertEquals(1, historicalSentenceOnly.activeCards)
        assertEquals(0, historicalSentenceOnly.suspendedCards)
        assertEquals(1, historicalSentenceOnly.matureSupportCount)
        assertEquals(45.0, historicalSentenceOnly.averageIntervalDays, 0.0)
        assertEquals(1, historicalSentenceOnly.totalLapses)
        assertEquals(12, historicalSentenceOnly.totalReps)
        assertEquals(10.0, historicalSentenceOnly.fsrsStabilityAvg!!, 0.0)
        assertTrue(dashboardRows.deletedAll)
        assertTrue(kanjiExamples.deletedAll)
        assertEquals(listOf("日"), dashboardRows.upserted.map { it.kanji })
        assertEquals(20L, dashboardRows.upserted.single().rebuiltAt)
        assertEquals(listOf(10L), kanjiExamples.upserted.map { it.cardId })
        assertEquals("日本へ行く。", kanjiExamples.upserted.single().sentence)
        assertEquals(setOf("古", "日", "本", "語"), kanjiInventory.upserted.map { it.kanji }.toSet())
        assertEquals(4L, kanjiInventory.upserted.single { it.kanji == "古" }.firstSeenAt)
        assertEquals(9, kanjiInventory.upserted.single { it.kanji == "古" }.sourceCount)
        assertEquals(20L, kanjiInventory.upserted.single { it.kanji == "日" }.lastSeenAt)
        assertTrue(kanjiInventory.upserted.single { it.kanji == "日" }.searchText.contains("日本"))
        assertTrue(studyItems.deletedAll)
        assertEquals(listOf("日"), studyItems.upserted.map { it.kanji })
        assertEquals(listOf("new"), studyItems.upserted.map { it.state })
        assertTrue(similarKanjiPairs.deletedAll)
        assertEquals(listOf("古日", "日本"), similarKanjiPairs.upserted.map { it.kanjiA + it.kanjiB })
        assertEquals(7L, similarKanjiPairs.upserted.single { it.kanjiA == "古" }.firstSeenAt)
        assertEquals(20L, similarKanjiPairs.upserted.single { it.kanjiA == "古" }.lastSeenAt)
        assertEquals(20L, similarKanjiPairs.upserted.single { it.kanjiA == "日" }.firstSeenAt)
    }

    @Test
    fun recordSuccessfulSnapshotRebuildsFromCleanResetState() = runBlocking {
        val syncRuns = FakeSyncRunDao(generatedId = 7)
        val notes = FakeSourceNoteDao(existingIds = emptyList())
        val cards = FakeSourceCardDao(existingIds = emptyList())
        val audits = FakeImportRuleAuditDao()
        val decisions = FakeImportDecisionDao()
        val archive = FakeSuspendedArchiveDao()
        val suspendedImports = FakeSuspendedImportDao()
        val suspendedSources = FakeSuspendedSourceDao()
        val dashboardRows = FakeDashboardRowDao()
        val kanjiExamples = FakeKanjiExampleDao()
        val kanjiInventory = FakeKanjiInventoryDao()
        val syncNoteSnapshots = FakeSyncNoteSnapshotDao()
        val syncCardSnapshots = FakeSyncCardSnapshotDao()
        val syncKanjiSnapshots = FakeSyncKanjiSnapshotDao()
        val studyItems = FakeStudyItemDao()
        val gateEvents = mutableListOf<String>()
        val repository = RoomSourceMirrorSyncRepository(
            syncRuns = syncRuns,
            sourceNotes = notes,
            sourceCards = cards,
            importRuleAudits = audits,
            importDecisions = decisions,
            suspendedArchive = archive,
            suspendedImports = suspendedImports,
            suspendedSources = suspendedSources,
            dashboardRows = dashboardRows,
            kanjiExamples = kanjiExamples,
            kanjiInventory = kanjiInventory,
            syncNoteSnapshots = syncNoteSnapshots,
            syncCardSnapshots = syncCardSnapshots,
            syncKanjiSnapshots = syncKanjiSnapshots,
            studyItems = studyItems,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            studyQueueMutationGate = RecordingStudyQueueMutationGate(gateEvents),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
        )

        val id = repository.recordSuccessfulSnapshot(
            syncRun = successRun(),
            notes = listOf(sourceNote(1)),
            cards = listOf(sourceCard(cardId = 10, noteId = 1, suspended = true)),
            importCandidates = listOf(
                importCandidate(
                    kanji = "日",
                    sources = listOf(sourceEvidence(kanji = "日", cardId = 10, noteId = 1, sourceType = ImportSource.SUSPENDED)),
                ),
            ),
            dashboardRows = listOf(dashboardRow("日")),
            settings = ImportSettings(),
            queueSeedBuilder = StudyQueueSeedBuilder { existingItems ->
                assertEquals(emptyList<StudyQueueItem>(), existingItems)
                listOf(studyQueueItem("日"))
            },
            similarKanjiIndex = FakeSimilarKanjiIndex(emptyList()),
        )

        assertEquals(SyncRunId(7), id)
        assertEquals(listOf("gate"), gateEvents)
        assertEquals(listOf(1L), notes.upserted.map { it.noteId })
        assertEquals(listOf(10L), cards.upserted.map { it.cardId })
        assertEquals(listOf("日"), dashboardRows.upserted.map { it.kanji })
        assertEquals(listOf("日"), kanjiExamples.upserted.map { it.kanji })
        assertEquals(listOf("日"), kanjiInventory.upserted.map { it.kanji })
        assertEquals(listOf("日"), studyItems.upserted.map { it.kanji })
        assertEquals(listOf("日"), suspendedImports.upserted.map { it.kanji })
        assertEquals(listOf(10L), archive.upserted.map { it.cardId })
        assertEquals(listOf("日"), decisions.upserted.map { it.kanji })
        assertEquals(listOf(7L), syncRuns.inserted.map { it.id })
        assertEquals(listOf(7L), syncNoteSnapshots.upserted.map { it.syncId })
        assertEquals(listOf(7L), syncCardSnapshots.upserted.map { it.syncId })
        assertEquals(listOf(7L), syncKanjiSnapshots.upserted.map { it.syncId }.distinct())
    }

    @Test
    fun retainedSuspendedImportCandidatesReadsOnlyActiveArchivedSourcesInCurrentRankRange() = runBlocking {
        val archive = FakeSuspendedArchiveDao(
            existing = mapOf(
                10L to suspendedArchiveEntity(
                    cardId = 10,
                    restoredAt = null,
                    expression = "日",
                    sentence = "日を見る。",
                ),
                20L to suspendedArchiveEntity(cardId = 20, restoredAt = 500),
            ),
        )
        val suspendedImports = FakeSuspendedImportDao(
            existing = mapOf(
                "日" to suspendedImportEntity("日", jitenRank = 120),
                "本" to suspendedImportEntity("本", jitenRank = 200),
                "外" to suspendedImportEntity("外", jitenRank = 50),
            ),
        )
        val suspendedSources = FakeSuspendedSourceDao()
        suspendedSources.upsertAll(
            listOf(
                suspendedSourceEntity(kanji = "日", cardId = 10, noteId = 1),
                suspendedSourceEntity(kanji = "本", cardId = 20, noteId = 2),
                suspendedSourceEntity(kanji = "外", cardId = 30, noteId = 3),
            ),
        )
        val repository = repositoryForRetainedImports(
            archive = archive,
            suspendedImports = suspendedImports,
            suspendedSources = suspendedSources,
        )

        val retained = repository.retainedSuspendedImportCandidates(
            ImportSettings(suspendedRankMin = 100, suspendedRankMax = 3000),
        )

        assertEquals(listOf("日"), retained.map { it.kanji })
        assertEquals(120, retained.single().jitenRank)
        assertEquals(3000, retained.single().rankRangeMax)
        val source = retained.single().sources.single()
        assertEquals(CardId(10), source.cardId)
        assertEquals(NoteId(1), source.noteId)
        assertEquals(ImportSource.SUSPENDED, source.sourceType)
        assertTrue(source.suspended)
        assertTrue(source.forcePractice)
        assertEquals(setOf(ImportSource.SUSPENDED), source.ruleTypes)
    }

    @Test
    fun retainedSuspendedImportCandidatesFallsBackToArchiveWhenSourceRowsAreMissing() = runBlocking {
        val repository = repositoryForRetainedImports(
            archive = FakeSuspendedArchiveDao(
                existing = mapOf(
                    10L to suspendedArchiveEntity(cardId = 10, restoredAt = null),
                ),
            ),
            suspendedImports = FakeSuspendedImportDao(
                existing = mapOf(
                    "日" to suspendedImportEntity("日", jitenRank = 120),
                ),
            ),
            suspendedSources = FakeSuspendedSourceDao(),
        )

        val retained = repository.retainedSuspendedImportCandidates(ImportSettings())

        assertEquals(listOf("日"), retained.map { it.kanji })
        val source = retained.single().sources.single()
        assertEquals(CardId(10), source.cardId)
        assertEquals(NoteId(10), source.noteId)
        assertEquals("日本", source.expression)
        assertEquals("日本へ行く。", source.sentence)
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

    private class FakeDashboardRowDao : DashboardRowDao {
        val upserted = mutableListOf<DashboardRowEntity>()
        var deletedAll = false

        override fun observeTop(limit: Int): Flow<List<DashboardRowEntity>> = emptyFlow()

        override fun observeAllOrdered(): Flow<List<DashboardRowEntity>> = emptyFlow()

        override suspend fun listTop(limit: Int): List<DashboardRowEntity> =
            upserted.sortedWith(
                compareByDescending<DashboardRowEntity> { it.weaknessScore }
                    .thenByDescending { it.suspendedExampleCount }
                    .thenBy { it.kanji },
            ).take(limit)

        override suspend fun listAllOrdered(): List<DashboardRowEntity> =
            upserted.sortedWith(
                compareByDescending<DashboardRowEntity> { it.weaknessScore }
                    .thenByDescending { it.suspendedExampleCount }
                    .thenBy { it.kanji },
            )

        override suspend fun get(kanji: String): DashboardRowEntity? =
            upserted.firstOrNull { it.kanji == kanji }

        override suspend fun upsertAll(rows: List<DashboardRowEntity>) {
            for (row in rows) {
                upserted.removeAll { it.kanji == row.kanji }
                upserted += row
            }
        }

        override suspend fun deleteAll() {
            deletedAll = true
            upserted.clear()
        }
    }

    private class FakeKanjiExampleDao : KanjiExampleDao {
        val upserted = mutableListOf<KanjiExampleEntity>()
        var deletedAll = false

        override suspend fun listForKanji(
            kanji: String,
            limit: Int,
        ): List<KanjiExampleEntity> =
            upserted.filter { it.kanji == kanji }.take(limit)

        override suspend fun listForTimeline(
            kanji: String,
            limit: Int,
        ): List<KanjiExampleEntity> =
            upserted.filter { it.kanji == kanji }.take(limit)

        override suspend fun upsertAll(examples: List<KanjiExampleEntity>) {
            upserted += examples
        }

        override suspend fun deleteAll() {
            deletedAll = true
            upserted.clear()
        }
    }

    private class FakeKanjiInventoryDao(
        existing: List<KanjiInventoryEntity> = emptyList(),
    ) : KanjiInventoryDao {
        val upserted = existing.toMutableList()

        override fun observeAll(): Flow<List<KanjiInventoryEntity>> = emptyFlow()

        override suspend fun get(kanji: String): KanjiInventoryEntity? =
            upserted.firstOrNull { it.kanji == kanji }

        override suspend fun listAll(): List<KanjiInventoryEntity> =
            upserted.sortedBy { it.kanji }

        override suspend fun upsertAll(items: List<KanjiInventoryEntity>) {
            for (item in items) {
                upserted.removeAll { it.kanji == item.kanji }
                upserted += item
            }
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

    private class FakeSyncKanjiSnapshotDao : SyncKanjiSnapshotDao {
        val upserted = mutableListOf<SyncKanjiSnapshotEntity>()

        override suspend fun listForKanji(kanji: String): List<SyncKanjiSnapshotEntity> =
            upserted.filter { it.kanji == kanji }.sortedWith(
                compareByDescending<SyncKanjiSnapshotEntity> { it.finishedAt }
                    .thenByDescending { it.syncId },
            )

        override suspend fun listForSync(syncId: Long): List<SyncKanjiSnapshotEntity> =
            upserted.filter { it.syncId == syncId }.sortedBy { it.kanji }

        override suspend fun upsertAll(rows: List<SyncKanjiSnapshotEntity>) {
            for (row in rows) {
                upserted.removeAll { it.syncId == row.syncId && it.kanji == row.kanji }
                upserted += row
            }
        }
    }

    private class FakeStudyItemDao : StudyItemDao {
        val upserted = mutableListOf<StudyItemEntity>()
        var deletedAll = false

        override fun observe(
            kanji: String,
            answerSignature: String,
        ): Flow<StudyItemEntity?> = emptyFlow()

        override suspend fun get(
            kanji: String,
            answerSignature: String,
        ): StudyItemEntity? = upserted.firstOrNull { it.kanji == kanji && it.answerSignature == answerSignature }

        override suspend fun listByState(state: String): List<StudyItemEntity> =
            upserted.filter { it.state == state }

        override suspend fun listByStates(states: List<String>): List<StudyItemEntity> =
            upserted.filter { it.state in states }

        override suspend fun listAll(): List<StudyItemEntity> = upserted.sortedBy { it.kanji }

        override suspend fun dueCount(
            state: String,
            nowMillis: Long,
        ): Int = upserted.count { it.state == state && it.dueAt <= nowMillis }

        override suspend fun upsert(item: StudyItemEntity) {
            upserted.removeAll { it.kanji == item.kanji && it.answerSignature == item.answerSignature }
            upserted += item
        }

        override suspend fun upsertAll(items: List<StudyItemEntity>) {
            for (item in items) {
                upsert(item)
            }
        }

        override suspend fun deleteAll() {
            deletedAll = true
            upserted.clear()
        }
    }

    private class FakeSimilarKanjiPairDao(
        existing: List<SimilarKanjiPairEntity> = emptyList(),
    ) : SimilarKanjiPairDao {
        val upserted = existing.toMutableList()
        var deletedAll = false

        override suspend fun listForKanji(kanji: String): List<SimilarKanjiPairEntity> =
            upserted.filter { it.kanjiA == kanji || it.kanjiB == kanji }

        override suspend fun kanjiWithSimilarNeighbors(): List<String> =
            upserted.flatMap { listOf(it.kanjiA, it.kanjiB) }.distinct().sorted()

        override suspend fun listAll(): List<SimilarKanjiPairEntity> =
            upserted.sortedWith(compareBy<SimilarKanjiPairEntity> { it.kanjiA }.thenBy { it.kanjiB }.thenBy { it.source })

        override suspend fun upsertAll(pairs: List<SimilarKanjiPairEntity>) {
            for (pair in pairs) {
                upserted.removeAll {
                    it.kanjiA == pair.kanjiA && it.kanjiB == pair.kanjiB && it.source == pair.source
                }
                upserted += pair
            }
        }

        override suspend fun deleteAll() {
            deletedAll = true
            upserted.clear()
        }
    }

    private class FakeSimilarKanjiIndex(
        private val pairs: List<SimilarKanjiPair>,
    ) : SimilarKanjiIndex {
        override fun pairsWithin(kanji: Collection<String>): List<SimilarKanjiPair> {
            val local = kanji.toSet()
            return pairs.filter { it.kanjiA in local && it.kanjiB in local }
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

    private fun dashboardRow(kanji: String): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = 120,
        primaryMeaning = "Japan",
        reading = "にほん",
        browserSearch = "note:Kiku Expression:*$kanji*",
        weaknessScore = 22,
        reasonCode = "suspended_archive",
        reasonText = "1 missed example made this a writing-practice target.",
        activeExampleCount = 0,
        suspendedExampleCount = 1,
        matureSupportCount = 0,
        examples = listOf(
            StudyExample(
                sourceType = "suspended",
                expression = "日本",
                reading = "にほん",
                meaning = "Japan",
                fsrsDifficulty = 6.0,
                fsrsRetrievability = 0.75,
                lapses = 2,
                intervalDays = 9,
                reps = 8,
                fsrsStability = 4.0,
                cardId = 10,
                noteId = 1,
                sentence = "日本へ行く。",
            ),
        ),
    )

    private fun repositoryForRetainedImports(
        archive: SuspendedArchiveDao,
        suspendedImports: SuspendedImportDao,
        suspendedSources: SuspendedSourceDao,
    ): RoomSourceMirrorSyncRepository = RoomSourceMirrorSyncRepository(
        syncRuns = FakeSyncRunDao(generatedId = 1),
        sourceNotes = FakeSourceNoteDao(existingIds = emptyList()),
        sourceCards = FakeSourceCardDao(existingIds = emptyList()),
        importRuleAudits = FakeImportRuleAuditDao(),
        importDecisions = FakeImportDecisionDao(),
        suspendedArchive = archive,
        suspendedImports = suspendedImports,
        suspendedSources = suspendedSources,
        dashboardRows = FakeDashboardRowDao(),
        kanjiExamples = FakeKanjiExampleDao(),
        kanjiInventory = FakeKanjiInventoryDao(),
        syncNoteSnapshots = FakeSyncNoteSnapshotDao(),
        syncCardSnapshots = FakeSyncCardSnapshotDao(),
        syncKanjiSnapshots = FakeSyncKanjiSnapshotDao(),
        studyItems = FakeStudyItemDao(),
        similarKanjiPairs = FakeSimilarKanjiPairDao(),
        studyQueueMutationGate = RecordingStudyQueueMutationGate(mutableListOf()),
        ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.DISABLED,
        runInTransaction = { block -> block() },
    )

    private fun suspendedImportEntity(
        kanji: String,
        jitenRank: Int?,
    ): SuspendedImportEntity = SuspendedImportEntity(
        kanji = kanji,
        jitenRank = jitenRank,
        rankKnown = if (jitenRank == null) 0 else 1,
        cutoffUsed = 3000,
        firstImportedAt = 10,
        lastSeenSyncId = 1,
    )

    private fun suspendedSourceEntity(
        kanji: String,
        cardId: Long,
        noteId: Long,
    ): SuspendedSourceEntity = SuspendedSourceEntity(
        kanji = kanji,
        cardId = cardId,
        noteId = noteId,
        expression = kanji,
        reading = "にち",
        meaning = "day",
        sentence = "${kanji}を見る。",
        syncId = 1,
    )

    private fun suspendedArchiveEntity(
        cardId: Long,
        restoredAt: Long?,
        expression: String = "日本",
        sentence: String = "日本へ行く。",
    ): SuspendedArchiveEntity = SuspendedArchiveEntity(
        cardId = cardId,
        noteId = cardId,
        deckName = "Mining",
        modelName = "Kiku",
        expression = expression,
        reading = "にほん",
        meaning = "Japan",
        sentence = sentence,
        fieldsJson = "{}",
        archivedAt = 20,
        archivedSyncId = 1,
        restoredAt = restoredAt,
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

    private fun sourceNote(
        noteId: Long,
        expression: String = "日本",
        sentence: String = "",
    ): SourceNote = SourceNote(
        noteId = NoteId(noteId),
        modelName = "Kiku",
        expression = expression,
        reading = "にほん",
        meaning = "Japan",
        sentence = sentence,
        fieldsJson = "{}",
        tags = "",
        lastSeenSyncId = SyncRunId(0),
    )

    private class RecordingStudyQueueMutationGate(
        private val events: MutableList<String>,
    ) : StudyQueueMutationGate {
        override suspend fun <T> mutate(block: suspend () -> T): T {
            events += "gate"
            return block()
        }
    }

    private fun sourceCard(
        cardId: Long,
        noteId: Long,
        intervalDays: Int = 0,
        reps: Int = 0,
        lapses: Int = 0,
        suspended: Boolean = cardId == 10L,
        fsrsStability: Double? = null,
        fsrsDifficulty: Double? = null,
        fsrsRetrievability: Double? = null,
    ): SourceCard = SourceCard(
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        deckName = "Mining",
        ord = 0,
        queue = if (cardId == 10L) -1 else 0,
        type = 2,
        due = 0,
        intervalDays = intervalDays,
        reps = reps,
        lapses = lapses,
        suspended = suspended,
        browserQueryMatched = cardId == 20L,
        fsrsStability = fsrsStability,
        fsrsDifficulty = fsrsDifficulty,
        fsrsRetrievability = fsrsRetrievability,
        lastSeenSyncId = SyncRunId(0),
    )

    private fun studyQueueItem(kanji: String): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = StudyItemState.NEW,
        dueAtMillis = 20,
        stability = 0.4,
        difficulty = 5.0,
        totalReviews = 0,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = "$kanji|sig",
        rung = StudyRung.KANJI_MEANING,
        phase = StudyPhase.NEW_LEARNING,
        createdAtMillis = 20,
    )
}
