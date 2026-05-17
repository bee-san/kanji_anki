package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank
import dev.bee.kanjianki.fsrs.FsrsMemory
import dev.bee.kanjianki.fsrs.FsrsReviewRating
import dev.bee.kanjianki.fsrs.FsrsReviewRequest
import dev.bee.kanjianki.fsrs.FsrsReviewSchedule
import dev.bee.kanjianki.fsrs.KaniFsrsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReviewTransitionEngineTest {
    private val fsrsEngine = RecordingFsrsEngine()
    private val transitionEngine = StudyReviewTransitionEngine(
        fsrsScheduler = StudyFsrsScheduler(fsrsEngine),
    )

    @Test
    fun duplicateTokenReturnsUnchangedItemWithoutConsumingAgain() {
        val original = item(activeToken = "token")

        val result = transitionEngine.apply(
            StudyReviewTransitionInput(
                item = original,
                request = request(token = "token"),
                nowMillis = NOW,
                consumedTokens = setOf("token"),
            ),
        )

        assertTrue(result.duplicate)
        assertSame(original, result.item)
        assertNull(result.appliedRating)
        assertEquals(ReviewTokenRejectionReason.ALREADY_CONSUMED, result.rejectionReason)
        assertEquals(setOf("token"), result.consumedTokens)
    }

    @Test
    fun newLearningGoodRepeatsNextLearningStepWithoutFsrs() {
        val original = item(
            state = StudyItemState.NEW,
            phase = StudyPhase.NEW_LEARNING,
            totalReviews = 0,
            memory = TaskMemory.initial(),
            activeToken = "learn",
        )

        val result = transitionEngine.apply(
            StudyReviewTransitionInput(
                item = original,
                request = request(token = "learn", rating = StudyRating.GOOD),
                nowMillis = NOW,
            ),
        )

        val updated = result.item
        val memory = updated.memories.kanjiMeaningMemory
        assertFalse(result.duplicate)
        assertEquals(StudyRating.GOOD, result.appliedRating)
        assertEquals(setOf("learn"), result.consumedTokens)
        assertEquals(StudyItemState.LEARNING, updated.state)
        assertEquals(StudyPhase.NEW_LEARNING, updated.phase)
        assertEquals(1, updated.learningStep)
        assertEquals(NOW + 10 * MINUTE, updated.dueAtMillis)
        assertEquals(1, updated.totalReviews)
        assertNull(updated.activeToken)
        assertNull(fsrsEngine.initialRating)
        assertNull(fsrsEngine.reviewRequest)
        assertEquals("learning", memory.state)
        assertEquals(1, memory.totalReviews)
        assertEquals(1, memory.learningStep)
        assertEquals("good", memory.lastRating)
    }

    @Test
    fun newLearningEasyGraduatesWithFreshFsrsState() {
        fsrsEngine.initialMemory = FsrsMemory(stability = 1.234, difficulty = 5.678)
        fsrsEngine.nextIntervalDays = 3
        val original = item(
            state = StudyItemState.NEW,
            phase = StudyPhase.NEW_LEARNING,
            totalReviews = 0,
            memory = TaskMemory.initial(),
            activeToken = "easy",
        )

        val result = transitionEngine.apply(
            StudyReviewTransitionInput(
                item = original,
                request = request(token = "easy", rating = StudyRating.EASY),
                nowMillis = NOW,
                targetRetention = 0.88,
            ),
        )

        val updated = result.item
        assertEquals(FsrsReviewRating.EASY, fsrsEngine.initialRating)
        assertEquals(0.88, fsrsEngine.nextIntervalRetention, 0.0)
        assertEquals(StudyItemState.REVIEW, updated.state)
        assertEquals(StudyPhase.REVIEW, updated.phase)
        assertEquals(NOW + 3 * DAY, updated.dueAtMillis)
        assertEquals(3, updated.matureIntervalDays)
        assertEquals(1.23, updated.stability, 0.0)
        assertEquals(5.68, updated.difficulty, 0.0)
        assertEquals(3, updated.memories.kanjiMeaningMemory.matureIntervalDays)
    }

    @Test
    fun reviewGoodUsesElapsedTaskMemoryAndPromotesByFsrsInterval() {
        fsrsEngine.reviewSchedule = FsrsReviewSchedule(
            nextMemory = FsrsMemory(stability = 18.014, difficulty = 5.987),
            retrievability = 0.9,
            nextIntervalDays = 22,
        )
        val original = item(
            dueAtMillis = NOW,
            realPassStreak = 1,
            memory = memory(
                dueAtMillis = NOW,
                totalReviews = 4,
                matureIntervalDays = 7,
            ),
            activeToken = "pass",
        )

        val result = transitionEngine.apply(
            StudyReviewTransitionInput(
                item = original,
                request = request(token = "pass", rating = StudyRating.GOOD),
                nowMillis = NOW,
            ),
        )

        val updated = result.item
        val expectedMemory = updated.memories.kanjiMeaningMemory
        assertEquals(
            FsrsReviewRequest(
                previousMemory = FsrsMemory(stability = 5.0, difficulty = 6.0),
                rating = FsrsReviewRating.GOOD,
                elapsedDays = 7,
                desiredRetention = 0.90,
            ),
            fsrsEngine.reviewRequest,
        )
        assertEquals(StudyRung.FONT_MEANING, updated.rung)
        assertEquals(StudyPhase.REVIEW, updated.phase)
        assertEquals(NOW + 22 * DAY, updated.dueAtMillis)
        assertEquals(22, updated.matureIntervalDays)
        assertEquals(18.01, updated.stability, 0.0)
        assertEquals(5.99, updated.difficulty, 0.0)
        assertEquals(0, updated.realPassStreak)
        assertEquals(0, updated.realAgainStreak)
        assertEquals(NOW, updated.lastRealReviewDueAtMillis)
        assertEquals(expectedMemory, updated.memories.fontMeaningMemory)
    }

    @Test
    fun reviewAgainLapsesEntersRelearningAndCanDemote() {
        fsrsEngine.reviewSchedule = FsrsReviewSchedule(
            nextMemory = FsrsMemory(stability = 2.0, difficulty = 7.0),
            retrievability = 0.5,
            nextIntervalDays = 5,
        )
        val original = item(
            dueAtMillis = NOW,
            lapses = 1,
            realAgainStreak = 2,
            memory = memory(
                dueAtMillis = NOW,
                totalReviews = 4,
                lapses = 1,
                matureIntervalDays = 7,
            ),
            activeToken = "again",
        )

        val result = transitionEngine.apply(
            StudyReviewTransitionInput(
                item = original,
                request = request(token = "again", rating = StudyRating.AGAIN),
                nowMillis = NOW,
            ),
        )

        val updated = result.item
        val memory = updated.memories.kanjiMeaningMemory
        assertEquals(StudyRung.TYPE_MEANING, updated.rung)
        assertEquals(StudyItemState.LEARNING, updated.state)
        assertEquals(StudyPhase.RELEARNING, updated.phase)
        assertEquals(NOW + 10 * MINUTE, updated.dueAtMillis)
        assertEquals(0, updated.matureIntervalDays)
        assertEquals(2, updated.lapses)
        assertEquals(0, updated.realAgainStreak)
        assertEquals(0, updated.realPassStreak)
        assertEquals(NOW, updated.lastRealReviewDueAtMillis)
        assertEquals("learning", memory.state)
        assertEquals(5, memory.totalReviews)
        assertEquals(2, memory.lapses)
        assertEquals(0, memory.matureIntervalDays)
        assertEquals(memory, updated.memories.typingMeaningMemory)
    }

    @Test
    fun relearningGraduationDoesNotAdvanceRealReviewStreaks() {
        fsrsEngine.nextDifficultyResult = 5.5
        fsrsEngine.nextIntervalDays = 2
        val original = item(
            state = StudyItemState.LEARNING,
            phase = StudyPhase.RELEARNING,
            realPassStreak = 2,
            lastRealReviewDueAtMillis = 123L,
            memory = memory(
                state = "learning",
                dueAtMillis = NOW,
                totalReviews = 4,
                learningStep = 0,
                matureIntervalDays = 0,
            ),
            activeToken = "graduate",
        )

        val result = transitionEngine.apply(
            StudyReviewTransitionInput(
                item = original,
                request = request(token = "graduate", rating = StudyRating.GOOD),
                nowMillis = NOW,
            ),
        )

        val updated = result.item
        assertEquals(NextDifficultyInput(6.0, FsrsReviewRating.GOOD), fsrsEngine.nextDifficultyInput)
        assertEquals(StudyPhase.REVIEW, updated.phase)
        assertEquals(StudyItemState.REVIEW, updated.state)
        assertEquals(NOW + 2 * DAY, updated.dueAtMillis)
        assertEquals(2, updated.realPassStreak)
        assertEquals(123L, updated.lastRealReviewDueAtMillis)
    }

    private fun request(
        token: String,
        rating: StudyRating = StudyRating.GOOD,
    ): StudyReviewRequest = StudyReviewRequest(
        kanji = "裂",
        token = token,
        rating = rating,
    )

    private fun item(
        state: StudyItemState = StudyItemState.REVIEW,
        phase: StudyPhase = StudyPhase.REVIEW,
        dueAtMillis: Long = NOW,
        totalReviews: Int = 4,
        lapses: Int = 0,
        learningStep: Int = 0,
        rung: StudyRung = StudyRung.KANJI_MEANING,
        realPassStreak: Int = 0,
        realAgainStreak: Int = 0,
        lastRealReviewDueAtMillis: Long = 0L,
        memory: TaskMemory = memory(
            dueAtMillis = dueAtMillis,
            totalReviews = totalReviews,
            lapses = lapses,
            learningStep = learningStep,
        ),
        activeToken: String? = null,
    ): StudyQueueItem = StudyQueueItem(
        kanji = "裂",
        state = state,
        dueAtMillis = dueAtMillis,
        stability = memory.stability,
        difficulty = memory.difficulty,
        totalReviews = totalReviews,
        lapses = lapses,
        learningStep = learningStep,
        writingLevel = 2,
        matureIntervalDays = memory.matureIntervalDays,
        rung = rung,
        phase = phase,
        realPassStreak = realPassStreak,
        realAgainStreak = realAgainStreak,
        lastRealReviewDueAtMillis = lastRealReviewDueAtMillis,
        activeToken = activeToken,
        memories = TaskMemoryBank(kanjiMeaningMemory = memory),
    )

    private fun memory(
        state: String = "review",
        dueAtMillis: Long = NOW,
        stability: Double = 5.0,
        difficulty: Double = 6.0,
        totalReviews: Int = 4,
        lapses: Int = 0,
        learningStep: Int = 0,
        matureIntervalDays: Int = 7,
    ): TaskMemory = TaskMemory.from(
        state = state,
        dueAtMillis = dueAtMillis,
        stability = stability,
        difficulty = difficulty,
        totalReviews = totalReviews,
        lapses = lapses,
        learningStep = learningStep,
        lastRating = "good",
        matureIntervalDays = matureIntervalDays,
    )

    private class RecordingFsrsEngine : KaniFsrsEngine {
        var initialMemory: FsrsMemory = FsrsMemory(stability = 1.0, difficulty = 5.0)
        var initialRating: FsrsReviewRating? = null
        var nextDifficultyInput: NextDifficultyInput? = null
        var nextDifficultyResult: Double = 5.0
        var nextIntervalRetention: Double = 0.0
        var nextIntervalDays: Int = 1
        var reviewRequest: FsrsReviewRequest? = null
        var reviewSchedule: FsrsReviewSchedule = FsrsReviewSchedule(
            nextMemory = FsrsMemory(stability = 1.0, difficulty = 5.0),
            retrievability = 1.0,
            nextIntervalDays = 1,
        )

        override fun initialState(firstRating: FsrsReviewRating): FsrsMemory {
            initialRating = firstRating
            return initialMemory
        }

        override fun review(request: FsrsReviewRequest): FsrsReviewSchedule {
            reviewRequest = request
            return reviewSchedule
        }

        override fun nextDifficulty(
            currentDifficulty: Double,
            rating: FsrsReviewRating,
        ): Double {
            nextDifficultyInput = NextDifficultyInput(currentDifficulty, rating)
            return nextDifficultyResult
        }

        override fun nextIntervalDays(
            stability: Double,
            desiredRetention: Double,
            maximumIntervalDays: Int,
        ): Int {
            nextIntervalRetention = desiredRetention
            return nextIntervalDays
        }
    }

    private data class NextDifficultyInput(
        val currentDifficulty: Double,
        val rating: FsrsReviewRating,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
        const val NOW = 30L * DAY
    }
}
