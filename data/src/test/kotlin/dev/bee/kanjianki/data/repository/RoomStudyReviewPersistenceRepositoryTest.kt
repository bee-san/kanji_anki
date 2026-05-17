package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.history.KanjiTimelineEventDao
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.data.StudyQueueMutationGate
import dev.bee.kanjianki.data.history.KanjiTimelineEventEntity
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.study.ReviewLogDao
import dev.bee.kanjianki.data.study.ReviewLogEntity
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.data.study.StudyTaskLogDao
import dev.bee.kanjianki.data.study.StudyTaskLogEntity
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceInput
import dev.bee.kanjianki.domain.repository.StudyReviewTaskCompletion
import dev.bee.kanjianki.domain.scheduler.StudyReviewRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyReviewPersistenceRepositoryTest {
    @Test
    fun acceptedReviewUpdatesItemAndWritesAuditRowsInOneTransaction() = runBlocking {
        val studyItems = FakeStudyItemDao(listOf(entity("裂")))
        val reviewLogs = FakeReviewLogDao()
        val studyTaskLogs = FakeStudyTaskLogDao()
        val timelineEvents = FakeKanjiTimelineEventDao()
        val repository = RoomStudyReviewPersistenceRepository(
            studyItems = studyItems,
            reviewLogs = reviewLogs,
            studyTaskLogs = studyTaskLogs,
            timelineEvents = timelineEvents,
            dashboardRows = FakeDashboardRowDao(row("裂")),
            kanjiExamples = FakeKanjiExampleDao(listOf(example("裂", sourceType = "active"))),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            studyQueueMutationGate = RecordingStudyQueueMutationGate(studyItems.events),
            runInTransaction = { block ->
                studyItems.events += "transaction"
                block()
            },
        )

        val saved = repository.saveAppliedReview(
            input(
                before = item(memoryReviewCount = 1),
                after = item(
                    totalReviews = 2,
                    dueAtMillis = 123_456L,
                    activeToken = null,
                    memoryReviewCount = 2,
                ),
            ),
        )

        assertTrue(saved)
        assertEquals(listOf("gate", "transaction", "upsert"), studyItems.events)
        assertEquals(2, studyItems.items.single().totalReviews)
        assertEquals(123_456L, studyItems.items.single().dueAt)
        assertNull(studyItems.items.single().activeToken)
        assertEquals("review-token", reviewLogs.logs.single().token)
        assertEquals("good", reviewLogs.logs.single().rating)
        assertEquals("kanji_meaning", reviewLogs.logs.single().taskType)
        assertEquals("裂|meaning", reviewLogs.logs.single().answerSignature)
        assertEquals("裂 means split", reviewLogs.logs.single().prompt)
        assertTrue(reviewLogs.logs.single().memoryBefore.isNotEmpty())
        assertTrue(reviewLogs.logs.single().memoryAfter.isNotEmpty())
        assertTrue(reviewLogs.logs.single().schedulerStateAfterJson.contains("\"total_reviews\":2"))
        assertEquals("review_passed", timelineEvents.events.single().eventType)
        assertEquals("Review passed", timelineEvents.events.single().title)
        assertEquals("active-expression", timelineEvents.events.single().sourceExpression)
        assertEquals(42, timelineEvents.events.single().weaknessScore)
        assertEquals(3, timelineEvents.events.single().matureSupportCount)
        assertEquals("review:review-token", timelineEvents.events.single().dedupeKey)
        assertEquals("task:review-token", studyTaskLogs.logs.single().taskKey)
        assertEquals("kanji_meaning", studyTaskLogs.logs.single().taskType)
        assertEquals("good", studyTaskLogs.logs.single().outcome)
        assertEquals(30L * 60L * 1000L, studyTaskLogs.logs.single().activeElapsedMs)
    }

    @Test
    fun duplicateReviewTokenDoesNotUpdateItemOrTimeline() = runBlocking {
        val studyItems = FakeStudyItemDao(listOf(entity("裂")))
        val reviewLogs = FakeReviewLogDao(conflictingTokens = setOf("review-token"))
        val timelineEvents = FakeKanjiTimelineEventDao()
        val repository = RoomStudyReviewPersistenceRepository(
            studyItems = studyItems,
            reviewLogs = reviewLogs,
            studyTaskLogs = FakeStudyTaskLogDao(),
            timelineEvents = timelineEvents,
            dashboardRows = FakeDashboardRowDao(row("裂")),
            kanjiExamples = FakeKanjiExampleDao(listOf(example("裂"))),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            runInTransaction = { block -> block() },
        )

        val saved = repository.saveAppliedReview(
            input(
                before = item(memoryReviewCount = 1),
                after = item(totalReviews = 2, memoryReviewCount = 2),
            ),
        )

        assertFalse(saved)
        assertEquals(emptyList<String>(), studyItems.events)
        assertEquals(1, studyItems.items.single().totalReviews)
        assertEquals(emptyList<ReviewLogEntity>(), reviewLogs.logs)
        assertEquals(emptyList<KanjiTimelineEventEntity>(), timelineEvents.events)
    }

    @Test
    fun staleReviewTokenDoesNotOverwriteCurrentStudyItem() = runBlocking {
        val studyItems = FakeStudyItemDao(listOf(entity("裂", activeToken = "newer-token")))
        val reviewLogs = FakeReviewLogDao()
        val timelineEvents = FakeKanjiTimelineEventDao()
        val repository = RoomStudyReviewPersistenceRepository(
            studyItems = studyItems,
            reviewLogs = reviewLogs,
            studyTaskLogs = FakeStudyTaskLogDao(),
            timelineEvents = timelineEvents,
            dashboardRows = FakeDashboardRowDao(row("裂")),
            kanjiExamples = FakeKanjiExampleDao(listOf(example("裂"))),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            runInTransaction = { block -> block() },
        )

        val saved = repository.saveAppliedReview(
            input(
                before = item(memoryReviewCount = 1),
                after = item(totalReviews = 2, memoryReviewCount = 2),
            ),
        )

        assertFalse(saved)
        assertEquals("newer-token", studyItems.items.single().activeToken)
        assertEquals(1, studyItems.items.single().totalReviews)
        assertEquals(emptyList<ReviewLogEntity>(), reviewLogs.logs)
        assertEquals(emptyList<KanjiTimelineEventEntity>(), timelineEvents.events)
    }

    @Test
    fun missingStudyItemDoesNotWriteReviewAuditRows() = runBlocking {
        val reviewLogs = FakeReviewLogDao()
        val timelineEvents = FakeKanjiTimelineEventDao()
        val repository = RoomStudyReviewPersistenceRepository(
            studyItems = FakeStudyItemDao(),
            reviewLogs = reviewLogs,
            studyTaskLogs = FakeStudyTaskLogDao(),
            timelineEvents = timelineEvents,
            dashboardRows = FakeDashboardRowDao(row("裂")),
            kanjiExamples = FakeKanjiExampleDao(listOf(example("裂"))),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            runInTransaction = { block -> block() },
        )

        val saved = repository.saveAppliedReview(
            input(
                before = item(memoryReviewCount = 1),
                after = item(totalReviews = 2, memoryReviewCount = 2),
            ),
        )

        assertFalse(saved)
        assertEquals(emptyList<ReviewLogEntity>(), reviewLogs.logs)
        assertEquals(emptyList<KanjiTimelineEventEntity>(), timelineEvents.events)
    }

    @Test
    fun disabledOwnershipPolicyDoesNotWriteRoomReviewState() = runBlocking {
        val studyItems = FakeStudyItemDao(listOf(entity("裂")))
        val reviewLogs = FakeReviewLogDao()
        val timelineEvents = FakeKanjiTimelineEventDao()
        val repository = RoomStudyReviewPersistenceRepository(
            studyItems = studyItems,
            reviewLogs = reviewLogs,
            studyTaskLogs = FakeStudyTaskLogDao(),
            timelineEvents = timelineEvents,
            dashboardRows = FakeDashboardRowDao(row("裂")),
            kanjiExamples = FakeKanjiExampleDao(listOf(example("裂"))),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.DISABLED,
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            runInTransaction = { block -> block() },
        )

        val saved = repository.saveAppliedReview(
            input(
                before = item(memoryReviewCount = 1),
                after = item(totalReviews = 2, memoryReviewCount = 2),
            ),
        )

        assertFalse(saved)
        assertEquals(1, studyItems.items.single().totalReviews)
        assertEquals(emptyList<ReviewLogEntity>(), reviewLogs.logs)
        assertEquals(emptyList<KanjiTimelineEventEntity>(), timelineEvents.events)
    }

    private object PassThroughStudyQueueMutationGate : StudyQueueMutationGate {
        override suspend fun <T> mutate(block: suspend () -> T): T = block()
    }

    private class RecordingStudyQueueMutationGate(
        private val events: MutableList<String>,
    ) : StudyQueueMutationGate {
        override suspend fun <T> mutate(block: suspend () -> T): T {
            events += "gate"
            return block()
        }
    }

    private class FakeStudyItemDao(
        initial: List<StudyItemEntity> = emptyList(),
    ) : StudyItemDao {
        val items = initial.toMutableList()
        val events = mutableListOf<String>()

        override fun observe(
            kanji: String,
            answerSignature: String,
        ): Flow<StudyItemEntity?> = emptyFlow()

        override suspend fun get(
            kanji: String,
            answerSignature: String,
        ): StudyItemEntity? = items.firstOrNull { it.kanji == kanji && it.answerSignature == answerSignature }

        override suspend fun listByState(state: String): List<StudyItemEntity> = emptyList()

        override suspend fun listByStates(states: List<String>): List<StudyItemEntity> = emptyList()

        override suspend fun listAll(): List<StudyItemEntity> = items

        override suspend fun dueCount(
            state: String,
            nowMillis: Long,
        ): Int = 0

        override suspend fun upsert(item: StudyItemEntity) {
            events += "upsert"
            items.removeAll { it.kanji == item.kanji && it.answerSignature == item.answerSignature }
            items += item
        }

        override suspend fun upsertAll(items: List<StudyItemEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeReviewLogDao(
        private val conflictingTokens: Set<String> = emptySet(),
    ) : ReviewLogDao {
        val logs = mutableListOf<ReviewLogEntity>()

        override suspend fun listForKanji(kanji: String): List<ReviewLogEntity> = emptyList()

        override suspend fun listSince(fromMillis: Long): List<ReviewLogEntity> = emptyList()

        override suspend fun insert(log: ReviewLogEntity): Long {
            if (log.token in conflictingTokens) {
                return -1L
            }
            logs += log
            return logs.size.toLong()
        }
    }

    private class FakeStudyTaskLogDao : StudyTaskLogDao {
        val logs = mutableListOf<StudyTaskLogEntity>()

        override suspend fun listAnsweredSince(fromMillis: Long): List<StudyTaskLogEntity> = emptyList()

        override suspend fun insert(log: StudyTaskLogEntity): Long {
            logs += log
            return logs.size.toLong()
        }
    }

    private class FakeKanjiTimelineEventDao : KanjiTimelineEventDao {
        val events = mutableListOf<KanjiTimelineEventEntity>()

        override suspend fun listForKanji(kanji: String): List<KanjiTimelineEventEntity> = emptyList()

        override suspend fun upsert(event: KanjiTimelineEventEntity) {
            events += event
        }
    }

    private class FakeDashboardRowDao(
        private val row: DashboardRowEntity?,
    ) : DashboardRowDao {
        override fun observeTop(limit: Int): Flow<List<DashboardRowEntity>> = emptyFlow()

        override suspend fun listTop(limit: Int): List<DashboardRowEntity> = row?.let(::listOf).orEmpty()

        override suspend fun get(kanji: String): DashboardRowEntity? = row?.takeIf { it.kanji == kanji }

        override suspend fun upsertAll(rows: List<DashboardRowEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeKanjiExampleDao(
        private val examples: List<KanjiExampleEntity>,
    ) : KanjiExampleDao {
        override suspend fun listForKanji(
            kanji: String,
            limit: Int,
        ): List<KanjiExampleEntity> = examples.filter { it.kanji == kanji }.take(limit)

        override suspend fun listForTimeline(
            kanji: String,
            limit: Int,
        ): List<KanjiExampleEntity> = examples.filter { it.kanji == kanji }.take(limit)

        override suspend fun upsertAll(examples: List<KanjiExampleEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private fun input(
        before: StudyQueueItem,
        after: StudyQueueItem,
    ): StudyReviewPersistenceInput = StudyReviewPersistenceInput(
        before = before,
        after = after,
        request = StudyReviewRequest(
            kanji = "裂",
            rating = StudyRating.GOOD,
            token = "review-token",
            taskType = "kanji_meaning",
            answerSignature = "裂|meaning",
            prompt = "裂 means split",
        ),
        appliedRating = StudyRating.GOOD,
        reviewedAtMillis = 86_400_000L,
        taskCompletion = StudyReviewTaskCompletion(
            taskKey = "task:review-token",
            kanji = "裂",
            taskType = "kanji_meaning",
            startedAtMillis = -1L,
            activeElapsedMillis = 45L * 60L * 1000L,
        ),
    )

    private fun item(
        totalReviews: Int = 1,
        dueAtMillis: Long = 10_000L,
        activeToken: String? = "review-token",
        memoryReviewCount: Int,
    ): StudyQueueItem = StudyQueueItem(
        kanji = "裂",
        state = StudyItemState.REVIEW,
        dueAtMillis = dueAtMillis,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = totalReviews,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        matureIntervalDays = 1,
        answerSignature = "裂|meaning",
        rung = StudyRung.KANJI_MEANING,
        phase = StudyPhase.REVIEW,
        activeToken = activeToken,
        memories = TaskMemoryBank(
            kanjiMeaningMemory = TaskMemory.from(
                state = "review",
                dueAtMillis = dueAtMillis,
                stability = 1.0,
                difficulty = 5.0,
                totalReviews = memoryReviewCount,
                lapses = 0,
                learningStep = 0,
                lastRating = "good",
                matureIntervalDays = 1,
            ),
        ),
    )

    private fun entity(
        kanji: String,
        activeToken: String? = "review-token",
    ): StudyItemEntity = StudyItemEntity(
        kanji = kanji,
        state = "review",
        dueAt = 10_000L,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = 1,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        recognitionStage = 0,
        consecutiveFailedRecognitionDays = 0,
        lastFailedRecognitionDay = 0L,
        writingRemediationPending = 0,
        suppressedByTaskType = "",
        suppressedAt = 0L,
        matureIntervalDays = 1,
        answerSignature = "裂|meaning",
        typingMeaningMemory = "",
        meaningKanjiMemory = "",
        kanjiMeaningMemory = "",
        fontMeaningMemory = "",
        wordReadingMemory = "",
        writingRemediationMemory = "",
        rung = "kanji_meaning",
        phase = "review",
        realPassStreak = 0,
        realAgainStreak = 0,
        lastRealReviewDueAt = 0L,
        similarKanjiMemory = "",
        activeToken = activeToken,
        createdAt = 1L,
    )

    private fun row(kanji: String): DashboardRowEntity = DashboardRowEntity(
        kanji = kanji,
        jitenRank = 10,
        primaryMeaning = "split",
        reading = "レツ",
        browserSearch = kanji,
        weaknessScore = 42,
        reasonCode = "weak",
        reasonText = "Weak support",
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = 3,
        rebuiltAt = 1L,
    )

    private fun example(
        kanji: String,
        sourceType: String = "suspended",
    ): KanjiExampleEntity = KanjiExampleEntity(
        kanji = kanji,
        sourceType = sourceType,
        cardId = 10L,
        noteId = 20L,
        expression = "$sourceType-expression",
        reading = "$sourceType-reading",
        meaning = "split",
        sentence = "example sentence",
        mature = 0,
        lapses = 0,
        intervalDays = 0,
        reps = 0,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
    )
}
