package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.history.KanjiTimelineEventDao
import dev.bee.kanjianki.data.history.KanjiTimelineEventEntity
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.inventory.KanjiInventoryDao
import dev.bee.kanjianki.data.inventory.KanjiInventoryEntity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyKanjiDetailRepositoryTest {
    @Test
    fun timelineForKanjiReadsJoinedDetailInsideOneTransaction() = runBlocking {
        var transactions = 0
        val timelineEvents = FakeKanjiTimelineEventDao(
            event(id = 2L, occurredAt = 200L),
            event(id = 1L, occurredAt = 100L),
        )
        val repository = RoomStudyKanjiDetailRepository(
            dashboardRows = FakeDashboardRowDao(row("日")),
            kanjiExamples = FakeKanjiExampleDao(example("日")),
            kanjiInventory = FakeKanjiInventoryDao(inventory("日")),
            localSuspensions = FakeLocalKanjiSuspensionDao("日"),
            studyItems = FakeStudyItemDao(
                item("日", state = "retired", dueAt = 10L),
                item("日", state = "review", dueAt = 500L),
            ),
            timelineEvents = timelineEvents,
            similarKanjiPairs = FakeSimilarKanjiPairDao("日"),
            runInTransaction = { block ->
                transactions++
                block()
            },
        )

        val detail = repository.timelineForKanji(" 日 ", eventLimit = 50)

        assertEquals(1, transactions)
        assertEquals("日", detail.inventoryItem?.kanji)
        assertTrue(detail.inventoryItem?.suspended == true)
        assertEquals("sun", detail.currentRow?.primaryMeaning)
        assertEquals("日本", detail.currentRow?.examples?.single()?.expression)
        assertEquals("review", detail.currentStudyItem?.state?.wireName)
        assertTrue(detail.currentStudyItem?.hasSimilarKanji == true)
        assertEquals(listOf(1L, 2L), detail.events.map { it.id })
        assertEquals(listOf(100L, 200L), detail.events.map { it.occurredAtMillis })
        assertEquals(50, timelineEvents.lastLimit)
    }

    @Test
    fun blankKanjiAndZeroEventLimitReturnEmptyTimelineWithoutTimelineRead() = runBlocking {
        val timelineEvents = FakeKanjiTimelineEventDao(event(id = 1L, occurredAt = 100L))
        val repository = RoomStudyKanjiDetailRepository(
            dashboardRows = FakeDashboardRowDao(null),
            kanjiExamples = FakeKanjiExampleDao(),
            kanjiInventory = FakeKanjiInventoryDao(),
            localSuspensions = FakeLocalKanjiSuspensionDao(),
            studyItems = FakeStudyItemDao(),
            timelineEvents = timelineEvents,
            similarKanjiPairs = FakeSimilarKanjiPairDao(),
            runInTransaction = { block -> block() },
        )

        val blank = repository.timelineForKanji(" ", eventLimit = 50)
        val zeroLimit = repository.timelineForKanji("日", eventLimit = 0)

        assertNull(blank.inventoryItem)
        assertNull(blank.currentRow)
        assertNull(blank.currentStudyItem)
        assertTrue(blank.events.isEmpty())
        assertTrue(zeroLimit.events.isEmpty())
        assertEquals(0, timelineEvents.latestCalls)
    }

    private class FakeDashboardRowDao(
        private val row: DashboardRowEntity?,
    ) : DashboardRowDao {
        override fun observeTop(limit: Int): Flow<List<DashboardRowEntity>> = emptyFlow()

        override fun observeAllOrdered(): Flow<List<DashboardRowEntity>> = emptyFlow()

        override suspend fun listTop(limit: Int): List<DashboardRowEntity> = row?.let(::listOf).orEmpty()

        override suspend fun listAllOrdered(): List<DashboardRowEntity> = row?.let(::listOf).orEmpty()

        override suspend fun get(kanji: String): DashboardRowEntity? = row?.takeIf { it.kanji == kanji }

        override suspend fun upsertAll(rows: List<DashboardRowEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeKanjiExampleDao(
        private vararg val examples: KanjiExampleEntity,
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

    private class FakeKanjiInventoryDao(
        private vararg val rows: KanjiInventoryEntity,
    ) : KanjiInventoryDao {
        override fun observeAll(): Flow<List<KanjiInventoryEntity>> = emptyFlow()

        override suspend fun get(kanji: String): KanjiInventoryEntity? =
            rows.firstOrNull { it.kanji == kanji }

        override suspend fun listAll(): List<KanjiInventoryEntity> =
            rows.sortedBy { it.kanji }

        override suspend fun listLimited(limit: Int): List<KanjiInventoryEntity> =
            listAll().take(limit)

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<KanjiInventoryEntity> = listAll()
            .filter { it.searchText.contains(query) }
            .take(limit)

        override suspend fun upsertAll(items: List<KanjiInventoryEntity>) = Unit
    }

    private class FakeLocalKanjiSuspensionDao(
        vararg kanji: String,
    ) : LocalKanjiSuspensionDao {
        private val entries = kanji.associateWith { LocalKanjiSuspensionEntity(it, 100L) }

        override fun observeAll(): Flow<List<LocalKanjiSuspensionEntity>> = emptyFlow()

        override suspend fun listAll(): List<LocalKanjiSuspensionEntity> =
            entries.values.sortedBy { it.kanji }

        override suspend fun get(kanji: String): LocalKanjiSuspensionEntity? =
            entries[kanji]

        override suspend fun upsert(suspension: LocalKanjiSuspensionEntity) = Unit

        override suspend fun delete(kanji: String) = Unit
    }

    private class FakeStudyItemDao(
        private vararg val items: StudyItemEntity,
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

        override suspend fun latestForKanji(kanji: String): StudyItemEntity? =
            items.filter { it.kanji == kanji }
                .sortedWith(compareBy<StudyItemEntity> { it.state == "retired" }.thenBy { it.dueAt })
                .firstOrNull()

        override suspend fun listAll(): List<StudyItemEntity> =
            items.sortedBy { it.kanji }

        override suspend fun dueCount(
            state: String,
            nowMillis: Long,
        ): Int = items.count { it.state == state && it.dueAt <= nowMillis }

        override suspend fun upsert(item: StudyItemEntity) = Unit

        override suspend fun upsertAll(items: List<StudyItemEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeKanjiTimelineEventDao(
        private vararg val events: KanjiTimelineEventEntity,
    ) : KanjiTimelineEventDao {
        var latestCalls = 0
        var lastLimit = -1

        override suspend fun listForKanji(kanji: String): List<KanjiTimelineEventEntity> =
            events.filter { it.kanji == kanji }

        override suspend fun listLatestForKanji(
            kanji: String,
            limit: Int,
        ): List<KanjiTimelineEventEntity> {
            latestCalls++
            lastLimit = limit
            return events.filter { it.kanji == kanji }.take(limit)
        }

        override suspend fun upsert(event: KanjiTimelineEventEntity) = Unit
    }

    private class FakeSimilarKanjiPairDao(
        private vararg val kanjiWithSimilar: String,
    ) : SimilarKanjiPairDao {
        override suspend fun listForKanji(kanji: String): List<SimilarKanjiPairEntity> =
            if (kanji in kanjiWithSimilar) {
                listOf(SimilarKanjiPairEntity(kanji, "目", "test", 100L, 100L))
            } else {
                emptyList()
            }

        override suspend fun kanjiWithSimilarNeighbors(): List<String> = kanjiWithSimilar.toList()

        override suspend fun listAll(): List<SimilarKanjiPairEntity> = emptyList()

        override suspend fun upsertAll(pairs: List<SimilarKanjiPairEntity>) = Unit

        override suspend fun deleteAll() = Unit
    }

    private fun row(kanji: String): DashboardRowEntity = DashboardRowEntity(
        kanji = kanji,
        jitenRank = 42,
        primaryMeaning = "sun",
        reading = "にち",
        browserSearch = "nid:1",
        weaknessScore = 88,
        reasonCode = "suspended",
        reasonText = "Needs practice",
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
        expression = "日本",
        reading = "にほん",
        meaning = "Japan",
        sentence = "日本へ行く。",
        mature = 1,
        lapses = 2,
        intervalDays = 21,
        reps = 9,
        fsrsStability = 13.0,
        fsrsDifficulty = 7.5,
        fsrsRetrievability = 0.41,
    )

    private fun inventory(kanji: String): KanjiInventoryEntity = KanjiInventoryEntity(
        kanji = kanji,
        primaryMeaning = "sun",
        readings = "にち",
        browserSearch = "nid:1",
        searchText = "$kanji sun にち",
        sourceCount = 1,
        exampleCount = 1,
        firstSeenAt = 100L,
        lastSeenAt = 200L,
    )

    private fun item(
        kanji: String,
        state: String,
        dueAt: Long,
    ): StudyItemEntity = StudyItemEntity(
        kanji = kanji,
        state = state,
        dueAt = dueAt,
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
        matureIntervalDays = 12,
        answerSignature = "$kanji|sig|$state",
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
        activeToken = "tok",
        createdAt = 100L,
    )

    private fun event(
        id: Long,
        occurredAt: Long,
    ): KanjiTimelineEventEntity = KanjiTimelineEventEntity(
        id = id,
        kanji = "日",
        occurredAt = occurredAt,
        eventType = "review",
        title = "Reviewed",
        detail = "Saved.",
        sourceExpression = "日本",
        sourceReading = "にほん",
        rating = "good",
        writingRequired = 1,
        writingPassed = 1,
        manualOverride = 0,
        weaknessScore = 88,
        matureSupportCount = 1,
        syncId = 7L,
        dedupeKey = "review:$id",
    )
}
