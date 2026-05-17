package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReviewMemoryPolicyTest {
    private val policy = ReviewMemoryPolicy()

    @Test
    fun activeTaskMemoryUsesPersistedTaskMemoryWhenItHasReviews() {
        val reviewedMemory = memory(totalReviews = 2, stability = 9.0)
        val item = item(
            totalReviews = 7,
            memories = TaskMemoryBank(fontMeaningMemory = reviewedMemory),
        )

        assertSame(reviewedMemory, policy.activeTaskMemory(item, StudyRung.FONT_MEANING))
    }

    @Test
    fun activeTaskMemoryFallsBackToStudyFieldsForLegacyReviewedItems() {
        val item = item(
            state = StudyItemState.REVIEW,
            dueAtMillis = 3_000L,
            stability = 4.5,
            difficulty = 6.5,
            totalReviews = 5,
            lapses = 1,
            learningStep = 2,
            matureIntervalDays = 21,
        )

        val memory = policy.activeTaskMemory(item, StudyRung.KANJI_MEANING)

        assertEquals("review", memory.state)
        assertEquals(3_000L, memory.dueAtMillis)
        assertEquals(4.5, memory.stability, 0.0)
        assertEquals(6.5, memory.difficulty, 0.0)
        assertEquals(5, memory.totalReviews)
        assertEquals(1, memory.lapses)
        assertEquals(2, memory.learningStep)
        assertEquals(21, memory.matureIntervalDays)
    }

    @Test
    fun newItemsKeepInitialTaskMemoryInsteadOfStudyFieldFallback() {
        val item = item(totalReviews = 0)

        val memory = policy.activeTaskMemory(item, StudyRung.KANJI_MEANING)

        assertEquals(TaskMemory.initial(), memory)
    }

    @Test
    fun elapsedReviewDaysUsesPreviousScheduledInterval() {
        val day = 86_400_000L
        val memory = memory(
            dueAtMillis = 10 * day,
            matureIntervalDays = 7,
        )

        assertEquals(8, policy.elapsedReviewDays(memory, nowMillis = 11 * day))
        assertEquals(0, policy.elapsedReviewDays(memory, nowMillis = 2 * day))
        assertEquals(0, policy.elapsedReviewDays(memory.copy(dueAtMillis = 0L), nowMillis = 0L))
    }

    private fun item(
        state: StudyItemState = StudyItemState.REVIEW,
        dueAtMillis: Long = 1_000L,
        stability: Double = 1.0,
        difficulty: Double = 5.0,
        totalReviews: Int = 1,
        lapses: Int = 0,
        learningStep: Int = 0,
        matureIntervalDays: Int = 0,
        memories: TaskMemoryBank = TaskMemoryBank(),
    ): StudyQueueItem = StudyQueueItem(
        kanji = "裂",
        state = state,
        dueAtMillis = dueAtMillis,
        stability = stability,
        difficulty = difficulty,
        totalReviews = totalReviews,
        lapses = lapses,
        learningStep = learningStep,
        writingLevel = 0,
        matureIntervalDays = matureIntervalDays,
        phase = StudyPhase.REVIEW,
        memories = memories,
    )

    private fun memory(
        dueAtMillis: Long = 1_000L,
        stability: Double = 1.0,
        totalReviews: Int = 0,
        matureIntervalDays: Int = 0,
    ): TaskMemory = TaskMemory.from(
        state = "review",
        dueAtMillis = dueAtMillis,
        stability = stability,
        difficulty = 5.0,
        totalReviews = totalReviews,
        lapses = 0,
        learningStep = 0,
        lastRating = "good",
        matureIntervalDays = matureIntervalDays,
    )
}
