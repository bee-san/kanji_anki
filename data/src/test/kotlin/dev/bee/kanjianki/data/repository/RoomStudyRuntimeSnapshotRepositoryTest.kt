package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.similar.SimilarKanjiPairEntity
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyRuntimeSnapshotRepositoryTest {
    @Test
    fun activeSnapshotReadsRowsAndItemsInsideOneTransaction() = runBlocking {
        var transactions = 0
        val repository = RoomStudyRuntimeSnapshotRepository(
            dashboardRows = FakeDashboardRowDao(listOf(row("裂"), row("浅"))),
            kanjiExamples = FakeKanjiExampleDao(listOf(example("裂"))),
            localSuspensions = FakeLocalKanjiSuspensionDao(listOf(LocalKanjiSuspensionEntity("浅", 100L))),
            studyItems = FakeStudyItemDao(
                listOf(
                    item("裂", state = "review"),
                    item("古", state = "retired"),
                ),
            ),
            similarKanjiPairs = FakeSimilarKanjiPairDao(listOf("裂")),
            runInTransaction = { block ->
                transactions++
                block()
            },
        )

        val snapshot = repository.activeSnapshot(dashboardLimit = 10)

        assertEquals(1, transactions)
        assertEquals(listOf("裂"), snapshot.rows.map { it.kanji })
        val example = snapshot.rows.single().examples.single()
        assertEquals(123L, example.cardId)
        assertEquals(456L, example.noteId)
        assertEquals("裂ける。", example.sentence)
        assertEquals(listOf("裂"), snapshot.items.map { it.kanji })
        assertTrue(snapshot.items.single().hasSimilarKanji)
    }

    private class FakeDashboardRowDao(
        private val rows: List<DashboardRowEntity>,
    ) : DashboardRowDao {
        override fun observeTop(limit: Int): Flow<List<DashboardRowEntity>> = emptyFlow()

        override suspend fun listTop(limit: Int): List<DashboardRowEntity> = rows.take(limit)

        override suspend fun get(kanji: String): DashboardRowEntity? = rows.firstOrNull { it.kanji == kanji }

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

        override suspend fun upsertAll(examples: List<KanjiExampleEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeLocalKanjiSuspensionDao(
        private val suspensions: List<LocalKanjiSuspensionEntity>,
    ) : LocalKanjiSuspensionDao {
        override fun observeAll(): Flow<List<LocalKanjiSuspensionEntity>> = emptyFlow()

        override suspend fun listAll(): List<LocalKanjiSuspensionEntity> = suspensions

        override suspend fun get(kanji: String): LocalKanjiSuspensionEntity? =
            suspensions.firstOrNull { it.kanji == kanji }

        override suspend fun upsert(suspension: LocalKanjiSuspensionEntity) = Unit

        override suspend fun delete(kanji: String) = Unit
    }

    private class FakeStudyItemDao(
        private val items: List<StudyItemEntity>,
    ) : StudyItemDao {
        override fun observe(
            kanji: String,
            answerSignature: String,
        ): Flow<StudyItemEntity?> = emptyFlow()

        override suspend fun get(
            kanji: String,
            answerSignature: String,
        ): StudyItemEntity? = items.firstOrNull { it.kanji == kanji && it.answerSignature == answerSignature }

        override suspend fun listByState(state: String): List<StudyItemEntity> =
            items.filter { it.state == state }

        override suspend fun listByStates(states: List<String>): List<StudyItemEntity> =
            items.filter { it.state in states }

        override suspend fun listAll(): List<StudyItemEntity> = items

        override suspend fun dueCount(
            state: String,
            nowMillis: Long,
        ): Int = items.count { it.state == state && it.dueAt <= nowMillis }

        override suspend fun upsert(item: StudyItemEntity) = Unit

        override suspend fun upsertAll(items: List<StudyItemEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeSimilarKanjiPairDao(
        private val kanjiWithSimilar: List<String>,
    ) : SimilarKanjiPairDao {
        override suspend fun listForKanji(kanji: String): List<SimilarKanjiPairEntity> = emptyList()

        override suspend fun kanjiWithSimilarNeighbors(): List<String> = kanjiWithSimilar

        override suspend fun listAll(): List<SimilarKanjiPairEntity> = emptyList()

        override suspend fun upsertAll(pairs: List<SimilarKanjiPairEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private fun row(kanji: String): DashboardRowEntity = DashboardRowEntity(
        kanji = kanji,
        jitenRank = 100,
        primaryMeaning = "split",
        reading = "さく",
        browserSearch = kanji,
        weaknessScore = 10,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = 1,
        matureSupportCount = 0,
        rebuiltAt = 100L,
    )

    private fun example(kanji: String): KanjiExampleEntity = KanjiExampleEntity(
        kanji = kanji,
        sourceType = "suspended",
        cardId = 123L,
        noteId = 456L,
        expression = "裂ける",
        reading = "さける",
        meaning = "split",
        sentence = "裂ける。",
        mature = 1,
        lapses = 2,
        intervalDays = 21,
        reps = 9,
        fsrsStability = 13.0,
        fsrsDifficulty = 7.5,
        fsrsRetrievability = 0.41,
    )

    private fun item(
        kanji: String,
        state: String,
    ): StudyItemEntity = StudyItemEntity(
        kanji = kanji,
        state = state,
        dueAt = 100L,
        stability = 4.0,
        difficulty = 6.0,
        totalReviews = 3,
        lapses = 1,
        learningStep = 0,
        writingLevel = 0,
        recognitionStage = 0,
        consecutiveFailedRecognitionDays = 0,
        lastFailedRecognitionDay = 0L,
        writingRemediationPending = 0,
        suppressedByTaskType = "",
        suppressedAt = 0L,
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
        lastRealReviewDueAt = 0L,
        similarKanjiMemory = "",
        activeToken = null,
        createdAt = 10L,
    )
}
