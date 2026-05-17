package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class LoadNextStudySessionUseCaseTest {
    @Test
    fun loadsDashboardRowsAndActiveQueueStatesForNextSession() = runBlocking {
        val now = 1_000L
        val dashboardRepository = FakeStudyDashboardRepository(
            rows = listOf(row("裂"), row("語")),
        )
        val queueRepository = FakeStudyQueueRepository(
            itemsByState = mapOf(
                StudyItemState.NEW to listOf(
                    item(
                        kanji = "語",
                        state = StudyItemState.NEW,
                        phase = StudyPhase.NEW_LEARNING,
                        totalReviews = 0,
                    ),
                ),
                StudyItemState.REVIEW to listOf(item("裂", state = StudyItemState.REVIEW)),
                StudyItemState.RETIRED to listOf(item("休", state = StudyItemState.RETIRED)),
            ),
        )

        assertEquals(120, LoadNextStudySessionRequest(nowMillis = now).dashboardLimit)

        val session = useCase(queueRepository, dashboardRepository)(
            LoadNextStudySessionRequest(
                nowMillis = now,
                dashboardLimit = 12,
                allowedKanji = setOf("裂"),
            ),
        )

        assertEquals("裂", session?.item?.kanji)
        assertEquals("token-裂", session?.token)
        assertEquals("token-裂", session?.item?.activeToken)
        assertEquals(12, dashboardRepository.lastListActiveLimit)
        assertEquals(1, queueRepository.listActiveCalls)
        assertEquals(listOf("裂" to "token-裂"), queueRepository.claimedTokens)
    }

    @Test
    fun requestOptionsFlowIntoSessionSelection() = runBlocking {
        val dashboardRepository = FakeStudyDashboardRepository(
            rows = listOf(
                row("弱", weakness = 90),
                row("頻", weakness = 10),
            ),
        )
        val queueRepository = FakeStudyQueueRepository(
            itemsByState = mapOf(
                StudyItemState.NEW to listOf(
                    item(
                        kanji = "頻",
                        state = StudyItemState.NEW,
                        phase = StudyPhase.NEW_LEARNING,
                        totalReviews = 0,
                    ),
                    item(
                        kanji = "弱",
                        state = StudyItemState.NEW,
                        phase = StudyPhase.NEW_LEARNING,
                        totalReviews = 0,
                    ),
                ),
            ),
        )

        val session = useCase(queueRepository, dashboardRepository)(
            LoadNextStudySessionRequest(
                nowMillis = 1_000L,
                newCardSortMode = NewCardSortMode.KANI_WEAKNESS,
            ),
        )

        assertEquals("弱", session?.item?.kanji)
    }

    @Test
    fun returnsPersistedClaimedTokenBeforeShowingSession() = runBlocking {
        val dashboardRepository = FakeStudyDashboardRepository(rows = listOf(row("裂")))
        val queueRepository = FakeStudyQueueRepository(
            itemsByState = mapOf(
                StudyItemState.REVIEW to listOf(item("裂", activeToken = "persisted-token")),
            ),
        )

        val session = useCase(queueRepository, dashboardRepository)(
            LoadNextStudySessionRequest(nowMillis = 1_000L),
        )

        assertNotNull(session)
        assertEquals("persisted-token", session?.token)
        assertEquals("persisted-token", session?.item?.activeToken)
        assertEquals(listOf("裂" to "persisted-token"), queueRepository.claimedTokens)
    }

    @Test
    fun preservesSelectorAlignedRungAfterClaimingSessionToken() = runBlocking {
        val dashboardRepository = FakeStudyDashboardRepository(rows = listOf(row("裂")))
        val queueRepository = FakeStudyQueueRepository(
            itemsByState = mapOf(
                StudyItemState.REVIEW to listOf(
                    item("裂", rung = StudyRung.SIMILAR_KANJI, hasSimilarKanji = false),
                ),
            ),
        )

        val session = useCase(queueRepository, dashboardRepository)(
            LoadNextStudySessionRequest(nowMillis = 1_000L),
        )

        assertEquals(StudyRung.WRITE_KANJI, session?.item?.rung)
        assertEquals(StudyRung.WRITE_KANJI.wireName, session?.taskType)
        assertEquals(StudyRung.WRITE_KANJI, queueRepository.claimedItems.single().rung)
    }

    @Test
    fun returnsNullWhenRepositoriesHaveNoActiveDueSession() = runBlocking {
        val dashboardRepository = FakeStudyDashboardRepository(rows = listOf(row("裂")))
        val queueRepository = FakeStudyQueueRepository(
            itemsByState = mapOf(
                StudyItemState.REVIEW to listOf(
                    item("裂", dueAtMillis = 30_000L),
                ),
            ),
        )

        val session = useCase(queueRepository, dashboardRepository)(
            LoadNextStudySessionRequest(
                nowMillis = 1_000L,
                studyAheadMillis = 10_000L,
            ),
        )

        assertNull(session)
    }

    private fun useCase(
        queueRepository: StudyQueueRepository,
        dashboardRepository: StudyDashboardRepository,
    ): LoadNextStudySessionUseCase = LoadNextStudySessionUseCase(
        studyQueueRepository = queueRepository,
        studyDashboardRepository = dashboardRepository,
        selector = StudySessionSelector(tokenFactory = { item -> "token-${item.kanji}" }),
    )

    private fun item(
        kanji: String,
        state: StudyItemState = StudyItemState.REVIEW,
        dueAtMillis: Long = 1_000L,
        phase: StudyPhase = StudyPhase.REVIEW,
        totalReviews: Int = 1,
        activeToken: String? = null,
        rung: StudyRung = StudyRung.KANJI_MEANING,
        hasSimilarKanji: Boolean = false,
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = dueAtMillis,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = totalReviews,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        rung = rung,
        phase = phase,
        hasSimilarKanji = hasSimilarKanji,
        activeToken = activeToken,
    )

    private fun row(
        kanji: String,
        weakness: Int = 10,
    ): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = 100,
        primaryMeaning = "meaning",
        reading = "reading",
        browserSearch = "search",
        weaknessScore = weakness,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = 0,
    )

    private class FakeStudyQueueRepository(
        itemsByState: Map<StudyItemState, List<StudyQueueItem>>,
    ) : StudyQueueRepository {
        private val items = itemsByState.values.flatten()
            .associateBy { it.kanji to it.answerSignature }
            .toMutableMap()
        var listActiveCalls = 0
        val claimedTokens = mutableListOf<Pair<String, String>>()
        val claimedItems = mutableListOf<StudyQueueItem>()

        override suspend fun listActive(): List<StudyQueueItem> {
            listActiveCalls += 1
            return listOf(
                StudyItemState.NEW,
                StudyItemState.LEARNING,
                StudyItemState.REVIEW,
            ).flatMap { state -> items.values.filter { it.state == state } }
        }

        override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> {
            return items.values.filter { it.state == state }
        }

        override suspend fun listAllForSeeding(): List<StudyQueueItem> = items.values.toList()

        override suspend fun replaceAllSeeded(items: List<StudyQueueItem>) = Unit

        override suspend fun claimActiveToken(
            item: StudyQueueItem,
            token: String,
        ): StudyQueueItem? {
            val key = item.kanji to item.answerSignature
            val current = items[key] ?: return null
            val claimed = current.activeToken?.takeIf { it.isNotEmpty() }?.let { current }
                ?: item.copy(activeToken = token)
            items[key] = claimed
            claimedItems += claimed
            claimedTokens += item.kanji to (claimed.activeToken ?: "")
            return claimed
        }

        override suspend fun updateReviewedItem(item: StudyQueueItem): Boolean = true

        override suspend fun dueCount(
            state: StudyItemState,
            nowMillis: Long,
        ): Int = items.values.count { it.state == state && it.dueAtMillis <= nowMillis }
    }

    private class FakeStudyDashboardRepository(
        private val rows: List<StudyDashboardRow>,
    ) : StudyDashboardRepository {
        private val locallySuspended = mutableSetOf<String>()
        var lastListActiveLimit: Int? = null

        override fun observeTop(limit: Int): Flow<List<StudyDashboardRow>> = flowOf(rows.take(limit))

        override fun observeActive(limit: Int): Flow<List<StudyDashboardRow>> =
            flowOf(rows.filterNot { locallySuspended.contains(it.kanji) }.take(limit))

        override suspend fun listTop(limit: Int): List<StudyDashboardRow> = rows.take(limit)

        override suspend fun listActive(limit: Int): List<StudyDashboardRow> {
            lastListActiveLimit = limit
            return rows.filterNot { locallySuspended.contains(it.kanji) }.take(limit)
        }

        override suspend fun get(kanji: String): StudyDashboardRow? =
            rows.firstOrNull { it.kanji == kanji }

        override suspend fun isLocallySuspended(kanji: String): Boolean =
            locallySuspended.contains(kanji.trim())

        override suspend fun setLocallySuspended(
            kanji: String,
            suspended: Boolean,
            nowMillis: Long,
        ): Boolean {
            val safeKanji = kanji.trim()
            if (safeKanji.isEmpty()) {
                return false
            }
            if (suspended) {
                locallySuspended.add(safeKanji)
            } else {
                locallySuspended.remove(safeKanji)
            }
            return true
        }
    }
}
