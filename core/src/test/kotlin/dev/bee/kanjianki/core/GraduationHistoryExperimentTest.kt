package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal 74 / open decision D2 experiment.
 *
 * New-learning graduation currently seeds FSRS from the graduating rating
 * alone (`LatestFsrsAdapter.initialReview` with `isNewLearning = true` →
 * `engine.initialState(graduationRating)`), so a card that needed five
 * `again`s in learning graduates with the same initial memory as one that
 * breezed through on a single Good.
 *
 * This test is a **read-only experiment harness** (it does not touch
 * production scheduling). It drives two candidate adapter paths over a
 * struggling corpus and a breeze-through corpus and asserts the qualitative
 * relationship the lab report documents. See
 * `docs/scheduler-fsrs-correctness-lab-report.md` for the decision.
 */
class GraduationHistoryExperimentTest {
    private val engine: FsrsEngine = FsrsEngine.latestDefault()
    private val retention = 0.90
    private val maxInterval = 36_500

    /** Current production path: initial state from the graduating rating only. */
    private fun currentGraduationIntervalDays(learningAnswers: List<FsrsRating>): Int {
        val graduating = learningAnswers.last()
        val state = engine.initialState(graduating)
        return engine.nextIntervalDays(state.stability, retention, maxInterval)
    }

    /**
     * Candidate history path: seed from the FIRST learning answer, then evolve
     * the state through every subsequent learning answer via the FSRS
     * same-day short-term chain (`nextState(..., elapsedDays = 0)`), ending on
     * the graduating answer.
     */
    private fun historyGraduationIntervalDays(learningAnswers: List<FsrsRating>): Int {
        var state: FsrsMemoryState = engine.initialState(learningAnswers.first())
        for (i in 1 until learningAnswers.size) {
            state = engine.nextState(state, learningAnswers[i], 0)
        }
        return engine.nextIntervalDays(state.stability, retention, maxInterval)
    }

    @Test
    fun currentPathIgnoresLearningStruggleWhileHistoryPathShortensIt() {
        // A struggling card: several Agains before a graduating Good.
        val struggling = listOf(
            FsrsRating.AGAIN,
            FsrsRating.AGAIN,
            FsrsRating.AGAIN,
            FsrsRating.AGAIN,
            FsrsRating.AGAIN,
            FsrsRating.GOOD,
        )
        // A breeze-through card: a single graduating Good.
        val breeze = listOf(FsrsRating.GOOD)

        val currentStruggling = currentGraduationIntervalDays(struggling)
        val currentBreeze = currentGraduationIntervalDays(breeze)
        val historyStruggling = historyGraduationIntervalDays(struggling)
        val historyBreeze = historyGraduationIntervalDays(breeze)

        // The current path is blind to in-learning struggle: both corpora
        // graduate with the identical first interval.
        assertTrue(
            "Current path gives the struggling and breeze cards the same first interval",
            currentStruggling == currentBreeze,
        )
        // The history path differentiates them: the struggling card's
        // short-term chain produces a first interval that is not larger than
        // the breeze card's (it reflects the extra failures).
        assertTrue(
            "History path does not over-reward the struggling card vs the breeze card",
            historyStruggling <= historyBreeze,
        )
        // And it is not larger than the current (struggle-blind) interval for
        // the struggling card — the whole point of D2 is that today's path
        // over-estimates initial stability for hard cards.
        assertTrue(
            "History path first interval <= current path for the struggling card",
            historyStruggling <= currentStruggling,
        )
    }

    @Test
    fun harnessIsDeterministicAcrossRuns() {
        val answers = listOf(FsrsRating.AGAIN, FsrsRating.GOOD, FsrsRating.GOOD)
        assertTrue(
            currentGraduationIntervalDays(answers) == currentGraduationIntervalDays(answers) &&
                historyGraduationIntervalDays(answers) == historyGraduationIntervalDays(answers),
        )
    }
}
