package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.importing.ImportCandidateSelector
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiPair
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

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
            dashboardBuilder(),
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
        assertEquals(listOf("日", "本"), sourceMirrorSync.importCandidates.map { it.kanji })
        assertEquals(listOf("日", "本"), sourceMirrorSync.dashboardRows.map { it.kanji })
        assertEquals(SyncRunId(1), sourceMirrorSync.notes.single { it.noteId == NoteId(10) }.lastSeenSyncId)
        assertEquals(SyncRunId(1), sourceMirrorSync.cards.single { it.cardId == CardId(20) }.lastSeenSyncId)
    }

    @Test
    fun successfulReadSeedsQueueWhenQueueSettingsAreSupplied() = runBlocking {
        val gateway = FakeGateway(
            CollectionSnapshot(
                notes = listOf(sourceNote(noteId = 10), sourceNote(noteId = 11)),
                cards = listOf(
                    sourceCard(noteId = 10, suspended = false),
                    sourceCard(cardId = 21, noteId = 11, suspended = true),
                ),
            ),
        )
        val sourceMirrorSync = FakeSourceMirrorSyncRepository()
        val studyQueue = FakeStudyQueueRepository(existing = listOf(studyQueueItem("火")))
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = gateway,
            syncRuns = FakeSyncRunRepository(),
            sourceMirrorSync = sourceMirrorSync,
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(100, 150),
            studyQueue = studyQueue,
        )

        val id = useCase(
            RunSourceMirrorSyncRequest(
                importSettings = ImportSettings(),
                queueSeedContext = SyncStudyQueueSeedContext(
                    settings = StudyQueueSeedSettings(
                        activeQueueCap = 10,
                        newPerDay = 10,
                        matureSupportThreshold = 2,
                    ),
                    startOfDayMillis = 50,
                ),
            ),
        )

        assertEquals(SyncRunId(1), id)
        assertEquals(1, studyQueue.listAllForSeedingCalls)
        assertEquals(listOf("日", "本", "火"), sourceMirrorSync.seededQueueItems!!.map { it.kanji })
        assertEquals(StudyItemState.NEW, sourceMirrorSync.seededQueueItems!!.single { it.kanji == "日" }.state)
        assertEquals(150L, sourceMirrorSync.seededQueueItems!!.single { it.kanji == "日" }.createdAtMillis)
        assertEquals(StudyItemState.RETIRED, sourceMirrorSync.seededQueueItems!!.single { it.kanji == "火" }.state)
    }

    @Test
    fun successfulReadComputesAdaptivePlanAfterDashboardRowsAndLocalSuspensions() = runBlocking {
        val sourceMirrorSync = FakeSourceMirrorSyncRepository()
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = FakeGateway(
                CollectionSnapshot(
                    notes = listOf(sourceNote(noteId = 10), sourceNote(noteId = 11)),
                    cards = listOf(
                        sourceCard(noteId = 10, suspended = false),
                        sourceCard(cardId = 21, noteId = 11, suspended = true),
                    ),
                ),
            ),
            syncRuns = FakeSyncRunRepository(),
            sourceMirrorSync = sourceMirrorSync,
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(100, 150),
            studyQueue = FakeStudyQueueRepository(),
        )

        useCase(
            RunSourceMirrorSyncRequest(
                importSettings = ImportSettings(),
                queueSeedContext = SyncStudyQueueSeedContext(
                    settings = StudyQueueSeedSettings(
                        activeQueueCap = 10,
                        newPerDay = 10,
                        matureSupportThreshold = 2,
                    ),
                    startOfDayMillis = 50,
                    locallySuspendedKanji = setOf("本"),
                    adaptiveContext = SyncAdaptivePlanContext(
                        recentStats = AdaptiveReviewStats(total = 8, good = 8, writingRequired = 4),
                        currentStreakDays = 5,
                        workloadPolicy = AdaptiveWorkloadPolicy.manual(0),
                    ),
                ),
            ),
        )

        assertEquals(listOf("日"), sourceMirrorSync.seededQueueItems!!.map { it.kanji })
    }

    @Test
    fun successfulReadPassesSimilarKanjiIndexToSourceRepository() = runBlocking {
        val sourceMirrorSync = FakeSourceMirrorSyncRepository()
        val similarIndex = FakeSimilarKanjiIndex()
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = FakeGateway(
                CollectionSnapshot(
                    notes = listOf(sourceNote(noteId = 10)),
                    cards = listOf(sourceCard(noteId = 10, suspended = false)),
                ),
            ),
            syncRuns = FakeSyncRunRepository(),
            sourceMirrorSync = sourceMirrorSync,
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(100, 150),
        )

        useCase(
            RunSourceMirrorSyncRequest(
                importSettings = ImportSettings(),
                similarKanjiIndex = similarIndex,
            ),
        )

        assertEquals(similarIndex, sourceMirrorSync.similarKanjiIndex)
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
            dashboardBuilder(),
            FakeClock(10, 20),
        )

        val id = useCase(ImportSettings())

        assertEquals(SyncRunId(1), id)
        assertEquals(SyncRunStatus.CONFIG_ERROR, syncRuns.inserted.single().status)
        assertEquals("permanent_permission", syncRuns.inserted.single().errorCode)
        assertTrue(sourceMirrorSync.notes.isEmpty())
        assertTrue(sourceMirrorSync.cards.isEmpty())
    }

    @Test
    fun gatewayFailureDoesNotSeedQueue() = runBlocking {
        val studyQueue = FakeStudyQueueRepository()
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = FakeGateway(
                failure = CollectionGatewayException(
                    errorCode = SyncErrorCode.RETRYABLE,
                    permanent = false,
                    message = "provider busy",
                ),
            ),
            syncRuns = FakeSyncRunRepository(),
            sourceMirrorSync = FakeSourceMirrorSyncRepository(),
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(10, 20),
            studyQueue = studyQueue,
        )

        useCase(
            RunSourceMirrorSyncRequest(
                importSettings = ImportSettings(),
                queueSeedContext = SyncStudyQueueSeedContext(
                    settings = StudyQueueSeedSettings(
                        activeQueueCap = 10,
                        newPerDay = 10,
                        matureSupportThreshold = 2,
                    ),
                    startOfDayMillis = 0,
                ),
            ),
        )

        assertEquals(0, studyQueue.listAllForSeedingCalls)
    }

    @Test
    fun repositoryFailureWritesRetryableSyncRunWithoutSourceSnapshot() = runBlocking {
        val syncRuns = FakeSyncRunRepository()
        val sourceMirrorSync = FakeSourceMirrorSyncRepository(
            failure = IllegalStateException("room unavailable"),
        )
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = FakeGateway(
                CollectionSnapshot(
                    notes = listOf(sourceNote(noteId = 10)),
                    cards = listOf(sourceCard(noteId = 10, suspended = true)),
                ),
            ),
            syncRuns = syncRuns,
            sourceMirrorSync = sourceMirrorSync,
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(10, 20, 30),
        )

        val id = useCase(ImportSettings())

        assertEquals(SyncRunId(1), id)
        assertEquals(SyncRunStatus.RETRYABLE_ERROR, syncRuns.inserted.single().status)
        assertEquals("unexpected", syncRuns.inserted.single().errorCode)
        assertEquals("room unavailable", syncRuns.inserted.single().errorMessage)
        assertTrue(sourceMirrorSync.notes.isEmpty())
        assertTrue(sourceMirrorSync.cards.isEmpty())
    }

    @Test
    fun cancellationIsRethrownWithoutFailedSyncRun() {
        val syncRuns = FakeSyncRunRepository()
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = FakeGateway(unexpectedFailure = CancellationException("cancelled")),
            syncRuns = syncRuns,
            sourceMirrorSync = FakeSourceMirrorSyncRepository(),
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(10),
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                useCase(ImportSettings())
            }
        }
        assertTrue(syncRuns.inserted.isEmpty())
    }

    @Test
    fun concurrentRunFailsFastWithoutWritingSkippedSyncRun() = runBlocking {
        val syncRuns = FakeSyncRunRepository()
        val gateway = BlockingGateway(
            CollectionSnapshot(
                notes = listOf(sourceNote(noteId = 10)),
                cards = listOf(sourceCard(noteId = 10, suspended = true)),
            ),
        )
        val useCase = RunSourceMirrorSyncUseCase(
            gateway = gateway,
            syncRuns = syncRuns,
            sourceMirrorSync = FakeSourceMirrorSyncRepository(),
            importCandidateSelector = importSelector(),
            dashboardBuilder = dashboardBuilder(),
            clock = FakeClock(10, 20),
        )

        val firstRun = async { useCase(ImportSettings()) }
        gateway.started.await()

        val secondError = runCatching { useCase(ImportSettings()) }.exceptionOrNull()

        assertTrue(secondError is SyncAlreadyRunningException)
        assertTrue(syncRuns.inserted.isEmpty())
        gateway.release.complete(Unit)
        assertEquals(SyncRunId(1), firstRun.await())
    }

    private fun importSelector(): ImportCandidateSelector =
        ImportCandidateSelector { kanji ->
            when (kanji) {
                "日" -> 100
                "本" -> 200
                else -> null
            }
        }

    private fun dashboardBuilder(): SyncDashboardBuilder =
        SyncDashboardBuilder { kanji ->
            when (kanji) {
                "日" -> 100
                "本" -> 200
                else -> null
            }
        }

    private class FakeGateway(
        private val snapshot: CollectionSnapshot? = null,
        private val failure: CollectionGatewayException? = null,
        private val unexpectedFailure: Exception? = null,
    ) : CollectionGateway {
        override suspend fun readCollection(settings: ImportSettings): CollectionSnapshot {
            failure?.let { throw it }
            unexpectedFailure?.let { throw it }
            return requireNotNull(snapshot)
        }
    }

    private class BlockingGateway(
        private val snapshot: CollectionSnapshot,
    ) : CollectionGateway {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun readCollection(settings: ImportSettings): CollectionSnapshot {
            started.complete(Unit)
            release.await()
            return snapshot
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

    private class FakeSourceMirrorSyncRepository(
        private val failure: Exception? = null,
    ) : SourceMirrorSyncRepository {
        val notes = mutableListOf<SourceNote>()
        val cards = mutableListOf<SourceCard>()
        val importCandidates = mutableListOf<ImportedKanjiCandidate>()
        val dashboardRows = mutableListOf<StudyDashboardRow>()
        var seededQueueItems: List<StudyQueueItem>? = null
        var similarKanjiIndex: SimilarKanjiIndex? = null
        lateinit var syncRun: SyncRun

        override suspend fun recordSuccessfulSnapshot(
            syncRun: SyncRun,
            notes: List<SourceNote>,
            cards: List<SourceCard>,
            importCandidates: List<ImportedKanjiCandidate>,
            dashboardRows: List<StudyDashboardRow>,
            settings: ImportSettings,
            seededQueueItems: List<StudyQueueItem>?,
            similarKanjiIndex: SimilarKanjiIndex?,
        ): SyncRunId {
            failure?.let { throw it }
            val id = SyncRunId(1)
            this.syncRun = syncRun.copy(id = id)
            this.notes += notes.map { it.copy(lastSeenSyncId = id) }
            this.cards += cards.map { it.copy(lastSeenSyncId = id) }
            this.importCandidates += importCandidates
            this.dashboardRows += dashboardRows
            this.seededQueueItems = seededQueueItems
            this.similarKanjiIndex = similarKanjiIndex
            return id
        }
    }

    private class FakeSimilarKanjiIndex : SimilarKanjiIndex {
        override fun pairsWithin(kanji: Collection<String>): List<SimilarKanjiPair> = emptyList()
    }

    private class FakeStudyQueueRepository(
        private val existing: List<StudyQueueItem> = emptyList(),
    ) : StudyQueueRepository {
        var listAllForSeedingCalls = 0

        override suspend fun listActive(): List<StudyQueueItem> =
            existing.filter { it.state != StudyItemState.RETIRED }

        override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> =
            existing.filter { it.state == state }

        override suspend fun listAllForSeeding(): List<StudyQueueItem> {
            listAllForSeedingCalls++
            return existing
        }

        override suspend fun replaceAllSeeded(items: List<StudyQueueItem>) = Unit

        override suspend fun updateReviewedItem(item: StudyQueueItem): Boolean = false

        override suspend fun dueCount(
            state: StudyItemState,
            nowMillis: Long,
        ): Int = 0
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

    private fun studyQueueItem(kanji: String): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = StudyItemState.REVIEW,
        dueAtMillis = 0,
        stability = 3.0,
        difficulty = 6.0,
        totalReviews = 1,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = "$kanji|old",
        rung = StudyRung.KANJI_MEANING,
        phase = StudyPhase.REVIEW,
        createdAtMillis = 0,
    )
}
