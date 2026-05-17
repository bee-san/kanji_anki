package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.StudyQueueMutationGate
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.similar.SimilarKanjiPairEntity
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyQueueRepositoryTest {
    @Test
    fun listAllForSeedingIncludesRetiredAndAnnotatesSimilarAvailability() = runBlocking {
        val repository = RoomStudyQueueRepository(
            studyItems = FakeStudyItemDao(
                listOf(
                    entity("裂", "review"),
                    entity("浅", "retired"),
                ),
            ),
            similarKanjiPairs = FakeSimilarKanjiPairDao(listOf("裂")),
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
            claimInTransaction = { block -> block() },
        )

        val items = repository.listAllForSeeding()

        assertEquals(listOf("浅", "裂"), items.map { it.kanji })
        assertEquals(listOf(StudyItemState.RETIRED, StudyItemState.REVIEW), items.map { it.state })
        assertEquals(listOf(false, true), items.map { it.hasSimilarKanji })
    }

    @Test
    fun replaceAllSeededDeletesBeforeInsertInsideTransaction() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("古", "review")))
        var transactions = 0
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            studyQueueMutationGate = RecordingStudyQueueMutationGate(dao.events),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block ->
                transactions++
                dao.events += "transaction"
                block()
            },
            claimInTransaction = { block -> block() },
        )

        repository.replaceAllSeeded(
            listOf(
                item("裂", state = StudyItemState.NEW),
                item("浅", state = StudyItemState.RETIRED),
            ),
        )

        assertEquals(1, transactions)
        assertEquals(listOf("gate", "transaction", "deleteAll", "upsertAll"), dao.events)
        assertEquals(listOf("裂", "浅"), dao.items.map { it.kanji })
        assertEquals(listOf("new", "retired"), dao.items.map { it.state })
    }

    @Test
    fun claimActiveTokenPersistsGeneratedTokenInsideTransaction() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("裂", "review")))
        var claimTransactions = 0
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(listOf("裂")),
            studyQueueMutationGate = RecordingStudyQueueMutationGate(dao.events),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
            claimInTransaction = { block ->
                claimTransactions++
                dao.events += "claimTransaction"
                block()
            },
        )

        val claimed = repository.claimActiveToken(item("裂", StudyItemState.REVIEW), "token-1")

        assertEquals(1, claimTransactions)
        assertEquals("token-1", claimed?.activeToken)
        assertTrue(claimed?.hasSimilarKanji == true)
        assertEquals("token-1", dao.items.single().activeToken)
        assertEquals(listOf("gate", "claimTransaction"), dao.events)
    }

    @Test
    fun claimActiveTokenPersistsAlignedSelectedItemInsideTransaction() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("裂", "review", rung = "similar_kanji")))
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
            claimInTransaction = { block -> block() },
        )

        val claimed = repository.claimActiveToken(
            item("裂", StudyItemState.REVIEW, rung = StudyRung.TYPE_MEANING),
            "token-1",
        )

        assertEquals(StudyRung.TYPE_MEANING, claimed?.rung)
        assertEquals("type_meaning", dao.items.single().rung)
        assertEquals("token-1", dao.items.single().activeToken)
    }

    @Test
    fun claimActiveTokenRejectsStaleSelectedItemWithoutOverwritingCurrentState() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("裂", "review", dueAt = 20)))
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
            claimInTransaction = { block -> block() },
        )

        val claimed = repository.claimActiveToken(
            item("裂", StudyItemState.REVIEW, dueAtMillis = 10, rung = StudyRung.TYPE_MEANING),
            "token-1",
        )

        assertNull(claimed)
        assertEquals(20, dao.items.single().dueAt)
        assertEquals("kanji_meaning", dao.items.single().rung)
        assertNull(dao.items.single().activeToken)
    }

    @Test
    fun claimActiveTokenKeepsExistingToken() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("裂", "review", activeToken = "existing-token")))
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
            claimInTransaction = { block -> block() },
        )

        val claimed = repository.claimActiveToken(item("裂", StudyItemState.REVIEW), "new-token")

        assertEquals("existing-token", claimed?.activeToken)
        assertEquals("existing-token", dao.items.single().activeToken)
        assertEquals(emptyList<String>(), dao.events)
    }

    @Test
    fun claimActiveTokenReturnsNullWhenItemStoppedBeingActive() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("裂", "retired")))
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            studyQueueMutationGate = PassThroughStudyQueueMutationGate,
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
            runInTransaction = { block -> block() },
            claimInTransaction = { block -> block() },
        )

        val claimed = repository.claimActiveToken(item("裂", StudyItemState.RETIRED), "token-1")

        assertNull(claimed)
        assertNull(dao.items.single().activeToken)
    }

    @Test
    fun disabledOwnershipPolicyBlocksRuntimeReadsAndWrites() = runBlocking {
        val dao = FakeStudyItemDao(listOf(entity("裂", "review")))
        val repository = RoomStudyQueueRepository(
            studyItems = dao,
            similarKanjiPairs = FakeSimilarKanjiPairDao(listOf("裂")),
            studyQueueMutationGate = RecordingStudyQueueMutationGate(dao.events),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.DISABLED,
            runInTransaction = { block ->
                dao.events += "transaction"
                block()
            },
            claimInTransaction = { block ->
                dao.events += "claimTransaction"
                block()
            },
        )

        assertEquals(emptyList<StudyQueueItem>(), repository.listActive())
        assertEquals(emptyList<StudyQueueItem>(), repository.listAllForSeeding())
        assertEquals(0, repository.dueCount(StudyItemState.REVIEW, nowMillis = 20))
        assertNull(repository.claimActiveToken(item("裂", StudyItemState.REVIEW), "token-1"))
        assertEquals(false, repository.updateReviewedItem(item("裂", StudyItemState.REVIEW)))
        repository.replaceAllSeeded(listOf(item("浅", StudyItemState.NEW)))

        assertEquals(listOf("裂"), dao.items.map { it.kanji })
        assertEquals(emptyList<String>(), dao.events)
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

        override suspend fun listByState(state: String): List<StudyItemEntity> =
            items.filter { it.state == state }.sortedWith(compareBy<StudyItemEntity> { it.dueAt }.thenBy { it.kanji })

        override suspend fun listByStates(states: List<String>): List<StudyItemEntity> =
            items.filter { it.state in states }.sortedWith(compareBy<StudyItemEntity> { it.dueAt }.thenBy { it.kanji })

        override suspend fun latestForKanji(kanji: String): StudyItemEntity? =
            items.filter { it.kanji == kanji }
                .sortedWith(compareBy<StudyItemEntity> { it.state == "retired" }.thenBy { it.dueAt })
                .firstOrNull()

        override suspend fun listAll(): List<StudyItemEntity> =
            items.sortedWith(compareBy<StudyItemEntity> { it.state }.thenBy { it.dueAt }.thenBy { it.kanji }.thenBy { it.answerSignature })

        override suspend fun dueCount(
            state: String,
            nowMillis: Long,
        ): Int = items.count { it.state == state && it.dueAt <= nowMillis }

        override suspend fun upsert(item: StudyItemEntity) {
            items.removeAll { it.kanji == item.kanji && it.answerSignature == item.answerSignature }
            items += item
        }

        override suspend fun upsertAll(items: List<StudyItemEntity>) {
            events += "upsertAll"
            for (item in items) {
                upsert(item)
            }
        }

        override suspend fun deleteAll() {
            events += "deleteAll"
            items.clear()
        }
    }

    private class FakeSimilarKanjiPairDao(
        private val kanjiWithSimilar: List<String> = emptyList(),
    ) : SimilarKanjiPairDao {
        override suspend fun listForKanji(kanji: String): List<SimilarKanjiPairEntity> = emptyList()

        override suspend fun kanjiWithSimilarNeighbors(): List<String> = kanjiWithSimilar

        override suspend fun listAll(): List<SimilarKanjiPairEntity> = emptyList()

        override suspend fun upsertAll(pairs: List<SimilarKanjiPairEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private fun item(
        kanji: String,
        state: StudyItemState,
        rung: StudyRung = StudyRung.KANJI_MEANING,
        dueAtMillis: Long = 10,
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = dueAtMillis,
        stability = 0.4,
        difficulty = 5.0,
        totalReviews = 0,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = "$kanji|sig",
        rung = rung,
        phase = StudyPhase.NEW_LEARNING,
        createdAtMillis = 10,
    )

    private fun entity(
        kanji: String,
        state: String,
        activeToken: String? = null,
        rung: String = "kanji_meaning",
        dueAt: Long = 10,
    ): StudyItemEntity = StudyItemEntity(
        kanji = kanji,
        state = state,
        dueAt = dueAt,
        stability = 0.4,
        difficulty = 5.0,
        totalReviews = 0,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        recognitionStage = 0,
        consecutiveFailedRecognitionDays = 0,
        lastFailedRecognitionDay = 0,
        writingRemediationPending = 0,
        suppressedByTaskType = "",
        suppressedAt = 0,
        matureIntervalDays = 0,
        answerSignature = "$kanji|sig",
        typingMeaningMemory = "",
        meaningKanjiMemory = "",
        kanjiMeaningMemory = "",
        fontMeaningMemory = "",
        wordReadingMemory = "",
        writingRemediationMemory = "",
        rung = rung,
        phase = "new_learning",
        realPassStreak = 0,
        realAgainStreak = 0,
        lastRealReviewDueAt = 0,
        similarKanjiMemory = "",
        activeToken = activeToken,
        createdAt = 10,
    )
}
