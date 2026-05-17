package dev.bee.kanjianki.data.repository

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
            runInTransaction = { block -> block() },
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
            runInTransaction = { block ->
                transactions++
                dao.events += "transaction"
                block()
            },
        )

        repository.replaceAllSeeded(
            listOf(
                item("裂", state = StudyItemState.NEW),
                item("浅", state = StudyItemState.RETIRED),
            ),
        )

        assertEquals(1, transactions)
        assertEquals(listOf("transaction", "deleteAll", "upsertAll"), dao.events)
        assertEquals(listOf("裂", "浅"), dao.items.map { it.kanji })
        assertEquals(listOf("new", "retired"), dao.items.map { it.state })
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
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = 10,
        stability = 0.4,
        difficulty = 5.0,
        totalReviews = 0,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = "$kanji|sig",
        rung = StudyRung.KANJI_MEANING,
        phase = StudyPhase.NEW_LEARNING,
        createdAtMillis = 10,
    )

    private fun entity(
        kanji: String,
        state: String,
    ): StudyItemEntity = StudyItemEntity(
        kanji = kanji,
        state = state,
        dueAt = 10,
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
        rung = "kanji_meaning",
        phase = "new_learning",
        realPassStreak = 0,
        realAgainStreak = 0,
        lastRealReviewDueAt = 0,
        similarKanjiMemory = "",
        activeToken = null,
        createdAt = 10,
    )
}
