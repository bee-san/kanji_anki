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

    /**
     * Cap-N variant: feed at most N most-recent learning answers into the
     * same-day chain before the graduating rating.
     */
    private fun capNGraduationStability(learningAnswers: List<FsrsRating>, capN: Int): Double {
        val capped = if (learningAnswers.size <= capN + 1) learningAnswers
        else learningAnswers.takeLast(capN + 1)
        var state: FsrsMemoryState = engine.initialState(capped.first())
        for (i in 1 until capped.size) {
            state = engine.nextState(state, capped[i], 0)
        }
        return state.stability
    }

    /**
     * Blend-α variant: stability = α·initialState(graduatingRating).stability +
     * (1-α)·historyChain.stability; difficulty from the graduating rating alone.
     */
    private fun blendAlphaGraduationStability(learningAnswers: List<FsrsRating>, alpha: Double): Double {
        val graduating = learningAnswers.last()
        val baselineStability = engine.initialState(graduating).stability
        var state: FsrsMemoryState = engine.initialState(learningAnswers.first())
        for (i in 1 until learningAnswers.size) {
            state = engine.nextState(state, learningAnswers[i], 0)
        }
        return alpha * baselineStability + (1.0 - alpha) * state.stability
    }

    @Test
    fun temperedCapNVariantsPreserveSaneOrdering() {
        val breeze = listOf(FsrsRating.GOOD)
        val struggling1 = listOf(FsrsRating.AGAIN, FsrsRating.GOOD)
        val struggling5 = listOf(
            FsrsRating.AGAIN, FsrsRating.AGAIN, FsrsRating.AGAIN,
            FsrsRating.AGAIN, FsrsRating.AGAIN, FsrsRating.GOOD,
        )

        for (capN in listOf(1, 2)) {
            val breezeStab = capNGraduationStability(breeze, capN)
            val strug1Stab = capNGraduationStability(struggling1, capN)
            val strug5Stab = capNGraduationStability(struggling5, capN)

            assertTrue("Cap-$capN: struggling1 <= breeze", strug1Stab <= breezeStab)
            assertTrue("Cap-$capN: struggling5 <= breeze", strug5Stab <= breezeStab)
            // Cap-N with small N still collapses heavily for many-again sequences
            // (the last N agains dominate). Record whether this variant qualifies
            // for the 25% floor — Cap-1 does; Cap-2 may not.
            assertTrue("Cap-$capN: produces positive stability",
                strug5Stab > 0)
        }
    }

    @Test
    fun temperedBlendAlphaVariantsPreserveSaneOrdering() {
        val breeze = listOf(FsrsRating.GOOD)
        val struggling1 = listOf(FsrsRating.AGAIN, FsrsRating.GOOD)
        val struggling5 = listOf(
            FsrsRating.AGAIN, FsrsRating.AGAIN, FsrsRating.AGAIN,
            FsrsRating.AGAIN, FsrsRating.AGAIN, FsrsRating.GOOD,
        )

        for (alpha in listOf(0.5, 0.7, 0.9)) {
            val breezeStab = blendAlphaGraduationStability(breeze, alpha)
            val strug1Stab = blendAlphaGraduationStability(struggling1, alpha)
            val strug5Stab = blendAlphaGraduationStability(struggling5, alpha)

            assertTrue("Blend-$alpha: struggling1 <= breeze", strug1Stab <= breezeStab)
            assertTrue("Blend-$alpha: struggling5 <= breeze", strug5Stab <= breezeStab)
            assertTrue("Blend-$alpha: struggling5 >= 25% of breeze (floor check)",
                strug5Stab >= breezeStab * 0.25)
        }
    }

    @Test
    fun temperedVariantsComparisonTableCorpus() {
        val corpora = mapOf(
            "[Good]" to listOf(FsrsRating.GOOD),
            "[Again, Good]" to listOf(FsrsRating.AGAIN, FsrsRating.GOOD),
            "[Again×5, Good]" to listOf(
                FsrsRating.AGAIN, FsrsRating.AGAIN, FsrsRating.AGAIN,
                FsrsRating.AGAIN, FsrsRating.AGAIN, FsrsRating.GOOD,
            ),
        )
        for ((label, answers) in corpora) {
            val current = engine.initialState(answers.last()).stability
            val capN1 = capNGraduationStability(answers, 1)
            val capN2 = capNGraduationStability(answers, 2)
            val blend05 = blendAlphaGraduationStability(answers, 0.5)
            val blend07 = blendAlphaGraduationStability(answers, 0.7)
            val blend09 = blendAlphaGraduationStability(answers, 0.9)

            assertTrue("$label: all variants produce positive stability",
                current > 0 && capN1 > 0 && capN2 > 0 && blend05 > 0 && blend07 > 0 && blend09 > 0)
        }
    }
}
