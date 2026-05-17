package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyProgressCalculatorTest {
    private val calculator = StudyProgressCalculator()

    @Test
    fun progressCountsUniqueSeenAndCompletedTasks() {
        var state = StudyProgressState()

        val firstPending = calculator.includePendingTask(state, "task-a")
        assertTrue(firstPending.accepted)
        state = firstPending.state

        val duplicatePending = calculator.includePendingTask(state, "task-a")
        assertFalse(duplicatePending.accepted)
        state = duplicatePending.state

        val emptyPending = calculator.includePendingTask(state, "")
        assertFalse(emptyPending.accepted)
        assertEquals(1, emptyPending.state.targetCount)
        assertFalse(calculator.atHardCap(emptyPending.state, continueAllKanjiSession = false))

        state = calculator.markTaskCompleted(state, "task-a")
        state = calculator.markTaskCompleted(state, "task-a")
        assertEquals(1, state.completedCount)
        assertEquals(1, state.targetCount)
        assertTrue(calculator.atHardCap(state, continueAllKanjiSession = false))
        assertFalse(calculator.atHardCap(state, continueAllKanjiSession = true))

        state = calculator.registerTaskShown(state, "task-b")
        state = calculator.markTaskCompleted(state, "task-b")
        assertEquals(2, state.completedCount)
        assertEquals(2, state.targetCount)

        state = calculator.reset()
        assertEquals(0, state.completedCount)
        assertEquals(0, state.targetCount)
    }

    @Test
    fun targetInitializationUsesRemainingThenTargetAndClampsManualValues() {
        var state = StudyProgressState()

        state = calculator.initializeTarget(state, StudyProgressPlan(targetCount = 7, remainingCount = 3))
        assertEquals(3, state.targetCount)

        state = calculator.initializeTarget(state, StudyProgressPlan(targetCount = 10, remainingCount = 9))
        assertEquals(3, state.targetCount)

        state = calculator.reset()
        state = calculator.initializeTarget(state, StudyProgressPlan(targetCount = 7, remainingCount = 0))
        assertEquals(7, state.targetCount)

        state = calculator.setTargetCount(state, -12)
        assertEquals(0, state.targetCount)
        state = calculator.registerTaskShown(state, "visible")
        assertEquals(1, state.targetCount)
    }

    @Test
    fun taskKeysAreStableAndNullSafe() {
        assertEquals("", calculator.sessionTaskKey(null, "裂", "token-1"))
        assertEquals("", calculator.sessionTaskKey("kanji_meaning", null, "token-1"))
        assertEquals("", calculator.sessionTaskKey("kanji_meaning", "裂", null))
        assertEquals(
            "session:kanji_meaning:裂:token-1",
            calculator.sessionTaskKey("kanji_meaning", " 裂 ", "token-1"),
        )
        assertEquals("", calculator.similarRepairProgressKey(null))
        assertEquals("", calculator.similarRepairProgressKey(0L))
        assertEquals("repair:42", calculator.similarRepairProgressKey(42L))
        assertEquals("", calculator.similarRepairStudyTaskKey(42L, ""))
        assertEquals("repair:42:token-2", calculator.similarRepairStudyTaskKey(42L, " token-2 "))
    }

    @Test
    fun reviewOutcomesTrackMovedForwardAndMissedKanji() {
        var state = StudyProgressState()

        state = calculator.recordReviewOutcome(
            state,
            reviewOutcome(
                kanji = " 裂 ",
                rating = StudyRating.AGAIN,
            ),
        )
        assertEquals(0, state.movedForwardKanji.size)
        assertEquals(1, state.missedKanji.size)

        state = calculator.recordReviewOutcome(
            state,
            reviewOutcome(
                kanji = "裂",
                rating = StudyRating.GOOD,
            ),
        )
        assertEquals(1, state.movedForwardKanji.size)
        assertEquals(0, state.missedKanji.size)

        state = calculator.recordReviewOutcome(
            state,
            reviewOutcome(
                kanji = "浅",
                rating = StudyRating.AGAIN,
                writingLevelAfter = 1,
            ),
        )
        state = calculator.recordReviewOutcome(
            state,
            reviewOutcome(
                kanji = "語",
                rating = StudyRating.AGAIN,
                realPassStreakAfter = 2,
            ),
        )
        state = calculator.recordReviewOutcome(
            state,
            reviewOutcome(
                kanji = null,
                rating = StudyRating.AGAIN,
            ),
        )
        assertEquals(3, state.movedForwardKanji.size)
        assertEquals(0, state.missedKanji.size)
    }

    @Test
    fun repairOutcomesDoNotDemoteAlreadyMovedKanjiToMissed() {
        var state = StudyProgressState()

        state = calculator.recordRepairOutcome(state, "裂", passed = false)
        assertEquals(0, state.movedForwardKanji.size)
        assertEquals(1, state.missedKanji.size)

        state = calculator.recordRepairOutcome(state, "裂", passed = true)
        assertEquals(1, state.movedForwardKanji.size)
        assertEquals(0, state.missedKanji.size)

        state = calculator.recordRepairOutcome(state, "裂", passed = false)
        state = calculator.recordRepairOutcome(state, "   ", passed = false)
        assertEquals(1, state.movedForwardKanji.size)
        assertEquals(0, state.missedKanji.size)
    }

    @Test
    fun snapshotMatchesStudyTopBarDisplayRules() {
        var state = StudyProgressState(targetCount = 2)
        state = calculator.markTaskCompleted(state, "one")
        state = calculator.markTaskCompleted(state, "two")

        val done = calculator.snapshot(state)
        assertEquals(2, done.completedCount)
        assertEquals(2, done.visibleCompletedCount)
        assertEquals(2, done.visibleTargetCount)
        assertEquals(0, done.remainingCount)
        assertEquals(1f, done.fraction, 0.0001f)
        assertTrue(done.isDone)
        assertTrue(done.atHardCap)

        val continueAllActive = calculator.snapshot(
            state = state,
            activeTask = true,
            continueAllKanjiSession = true,
        )
        assertEquals(2, continueAllActive.visibleCompletedCount)
        assertEquals(3, continueAllActive.visibleTargetCount)
        assertEquals(2f / 3f, continueAllActive.fraction, 0.0001f)
        assertFalse(continueAllActive.atHardCap)

        val emptyActive = calculator.snapshot(
            state = StudyProgressState(),
            activeTask = true,
        )
        assertEquals(0, emptyActive.visibleCompletedCount)
        assertEquals(1, emptyActive.visibleTargetCount)
        assertEquals(0f, emptyActive.fraction, 0.0001f)
        assertFalse(emptyActive.isDone)
    }

    private fun reviewOutcome(
        kanji: String?,
        rating: StudyRating,
        writingLevelAfter: Int = 0,
        realPassStreakAfter: Int = 0,
    ): StudyReviewProgressOutcome = StudyReviewProgressOutcome(
        kanji = kanji,
        rating = rating,
        writingLevelBefore = 0,
        writingLevelAfter = writingLevelAfter,
        realPassStreakBefore = 0,
        realPassStreakAfter = realPassStreakAfter,
    )
}
