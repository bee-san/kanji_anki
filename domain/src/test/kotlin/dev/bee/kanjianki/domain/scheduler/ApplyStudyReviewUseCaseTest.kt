package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import dev.bee.kanjianki.fsrs.FsrsMemory
import dev.bee.kanjianki.fsrs.FsrsReviewRating
import dev.bee.kanjianki.fsrs.FsrsReviewRequest
import dev.bee.kanjianki.fsrs.FsrsReviewSchedule
import dev.bee.kanjianki.fsrs.KaniFsrsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyStudyReviewUseCaseTest {
    private val fsrsEngine = RecordingFsrsEngine()
    private val repository = FakeStudyQueueRepository()
    private val useCase = ApplyStudyReviewUseCase(
        studyQueueRepository = repository,
        transitionEngine = StudyReviewTransitionEngine(
            fsrsScheduler = StudyFsrsScheduler(fsrsEngine),
        ),
    )

    @Test
    fun duplicateReviewsAreNotPersisted() = runBlocking {
        val item = item(activeToken = "dupe")

        val result = useCase.apply(
            StudyReviewTransitionInput(
                item = item,
                request = request(token = "dupe"),
                nowMillis = NOW,
                consumedTokens = setOf("dupe"),
            ),
        )

        assertTrue(result.transition.duplicate)
        assertFalse(result.persisted)
        assertEquals(emptyList<StudyQueueItem>(), repository.updatedItems)
    }

    @Test
    fun acceptedReviewsPersistUpdatedItem() = runBlocking {
        fsrsEngine.initialMemory = FsrsMemory(stability = 1.25, difficulty = 5.75)
        fsrsEngine.nextIntervalDays = 4
        val item = item(
            state = StudyItemState.NEW,
            phase = StudyPhase.NEW_LEARNING,
            totalReviews = 0,
            activeToken = "easy",
        )

        val result = useCase.apply(
            StudyReviewTransitionInput(
                item = item,
                request = request(token = "easy", rating = StudyRating.EASY),
                nowMillis = NOW,
            ),
        )

        assertFalse(result.transition.duplicate)
        assertTrue(result.persisted)
        assertEquals(1, repository.updatedItems.size)
        assertEquals(StudyPhase.REVIEW, repository.updatedItems.single().phase)
        assertEquals(NOW + 4 * DAY, repository.updatedItems.single().dueAtMillis)
        assertNull(repository.updatedItems.single().activeToken)
    }

    @Test
    fun missingRowsAreReportedWithoutChangingTransitionResult() = runBlocking {
        repository.updateResult = false
        val item = item(activeToken = "ok")

        val result = useCase.apply(
            StudyReviewTransitionInput(
                item = item,
                request = request(token = "ok", rating = StudyRating.GOOD),
                nowMillis = NOW,
            ),
        )

        assertFalse(result.transition.duplicate)
        assertFalse(result.persisted)
        assertEquals(1, repository.updatedItems.size)
        assertEquals("Review applied.", result.transition.message)
    }

    private class FakeStudyQueueRepository : StudyQueueRepository {
        var updateResult = true
        val updatedItems = mutableListOf<StudyQueueItem>()

        override suspend fun listActive(): List<StudyQueueItem> = emptyList()

        override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> = emptyList()

        override suspend fun updateReviewedItem(item: StudyQueueItem): Boolean {
            updatedItems += item
            return updateResult
        }

        override suspend fun dueCount(
            state: StudyItemState,
            nowMillis: Long,
        ): Int = 0
    }

    private class RecordingFsrsEngine : KaniFsrsEngine {
        var initialMemory: FsrsMemory = FsrsMemory(stability = 1.0, difficulty = 5.0)
        var nextIntervalDays: Int = 1

        override fun initialState(firstRating: FsrsReviewRating): FsrsMemory = initialMemory

        override fun review(request: FsrsReviewRequest): FsrsReviewSchedule = FsrsReviewSchedule(
            nextMemory = FsrsMemory(stability = 2.0, difficulty = 6.0),
            retrievability = 0.9,
            nextIntervalDays = nextIntervalDays,
        )

        override fun nextDifficulty(
            currentDifficulty: Double,
            rating: FsrsReviewRating,
        ): Double = currentDifficulty

        override fun nextIntervalDays(
            stability: Double,
            desiredRetention: Double,
            maximumIntervalDays: Int,
        ): Int = nextIntervalDays
    }

    private fun request(
        token: String,
        rating: StudyRating = StudyRating.GOOD,
    ): StudyReviewRequest = StudyReviewRequest(
        kanji = "裂",
        rating = rating,
        token = token,
    )

    private fun item(
        state: StudyItemState = StudyItemState.REVIEW,
        phase: StudyPhase = StudyPhase.REVIEW,
        totalReviews: Int = 1,
        activeToken: String? = null,
    ): StudyQueueItem = StudyQueueItem(
        kanji = "裂",
        state = state,
        dueAtMillis = NOW,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = totalReviews,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        phase = phase,
        activeToken = activeToken,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 10L * DAY
    }
}
