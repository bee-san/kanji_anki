package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7AlgorithmInfo
import dev.bee.fsrs.Fsrs7Engine
import dev.bee.fsrs.Fsrs7Parameters
import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the identity of the vendored FSRS engine, and which one Kani schedules with.
 *
 * `bee-fsrs/` is a vendored checkout of a release from
 * [`bee-san/bee-fsrs`](https://github.com/bee-san/bee-fsrs), and a vendored copy drifts
 * silently: someone edits it in place instead of upstreaming, and nothing notices. These
 * assertions are what notices.
 *
 * This is not testing FSRS. The engine's own fixtures do that, with 38 FSRS-6 vectors and
 * 384 FSRS-7 ones, and they run in `:bee-fsrs`. This is testing that Kani is scheduling
 * with the mathematics it claims, which matters because persisted `study_items` memory
 * state is only interpretable if the engine that produced it is known.
 *
 * It lives in `:core` rather than in `bee-fsrs/src/test` deliberately. The vendored tree
 * is byte-identical to upstream, and a Kani-specific test placed inside it would be the
 * one file that could never match — which would defeat the check it is trying to perform.
 *
 * @see bee-fsrs/PROVENANCE.md
 */
class FsrsVendoringTest {

    @Test
    fun kaniSchedulesWithTheThirtyFiveParameterFsrs7Engine() {
        // Asserted by parameter count rather than in prose, because the count is the
        // thing that distinguishes the two algorithms. FSRS-6 is still vendored and
        // still tested by the engine's own fixtures, but nothing in Kani reaches for
        // it — see theFsrs6EngineIsPresentButUnreachedByKani below.
        assertEquals(35, Fsrs7Parameters.PARAMETER_COUNT)
        assertEquals(35, Fsrs7AlgorithmInfo.PARAMETER_COUNT)
        assertEquals("FSRS-7 35-parameter snapshot", Fsrs7AlgorithmInfo.ALGORITHM_LABEL)

        // The fitter's hardcoded bounds arrays are sized to this count, and
        // `scheduler_fsrs_weights` persists exactly this many values. Probing the last
        // valid index proves the bounds arrays agree with the engine; a 21-parameter
        // bounds table underneath would throw here rather than quietly fit a
        // truncated vector.
        val lastIndex = Fsrs7Parameters.PARAMETER_COUNT - 1
        assertTrue(
            "fitter bounds must cover parameter $lastIndex",
            FsrsWeightFitter.lowerBound(lastIndex) <= FsrsWeightFitter.upperBound(lastIndex),
        )

        // And `scheduler_fsrs_weights` round-trips exactly this many values.
        val encoded = FsrsPersonalization.encodeWeights(Fsrs7Parameters.latestDefaultValues())
        assertEquals(Fsrs7Parameters.PARAMETER_COUNT, encoded.split(',').size)
    }

    @Test
    fun theFsrs7DefaultParametersAreExactlyTheExpectedThirtyFiveValues() {
        // The most consequential possible regression: changing a default silently
        // reschedules every existing user's whole queue, including the fixed-0.90
        // promotionIntervalDays signal the ladder keys off. Pinned by value, and
        // grouped as upstream groups them so a 35-long literal stays reviewable.
        val expected = doubleArrayOf(
            // Initial stability, w[0..3]
            0.041, 2.4175, 4.1283, 11.9709,
            // Difficulty, w[4..6]
            5.6385, 0.4468, 3.262,
            // Stability, long-term, w[7..15]
            2.3054, 0.1688, 1.3325, 0.3524, 0.0049, 0.7503, 0.0896, 0.6625, 1.3,
            // Stability, short-term, w[16..24]
            0.882, 0.3072, 3.5875, 0.303, 0.0107, 0.2279, 2.6413, 0.5594, 1.3,
            // Long-short term transition, w[25..26]
            2.5, 1.0,
            // Forgetting curve, w[27..34]
            0.0723, 0.1634, 0.5, 0.9555, 0.2245, 0.6232, 0.1362, 0.3862,
        )
        val actual = Fsrs7Parameters.latestDefaultValues()
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, value ->
            assertEquals("FSRS-7 parameter $index", value, actual[index], 0.0)
        }
    }

    @Test
    fun theFsrs7UpstreamSourceIsPinnedExactly() {
        // Recorded so a schedule computed years ago stays explainable. Pinned to a
        // commit and a blob, not a release: FSRS-7 has no release. It is research code
        // in a repository whose main moves, so "the FSRS-7 in srs-benchmark" does not
        // identify anything without a hash.
        assertEquals(
            "open-spaced-repetition/srs-benchmark",
            Fsrs7AlgorithmInfo.UPSTREAM_REPOSITORY,
        )
        assertEquals(
            "70cc4387f573ff20b13ac9c106333a335c8a4cb8",
            Fsrs7AlgorithmInfo.UPSTREAM_COMMIT,
        )
        assertEquals(
            "33893c3fed0f7dbe28c2b55874a50d9b3fa77df5",
            Fsrs7AlgorithmInfo.UPSTREAM_MODEL_BLOB,
        )
        assertEquals("models/fsrs_v7.py", Fsrs7AlgorithmInfo.UPSTREAM_MODEL_PATH)
    }

    @Test
    fun theFsrs6EngineIsPresentButUnreachedByKani() {
        // FSRS-6 stays vendored: it is upstream's, it is covered by its own 38
        // reference vectors, and deleting it from a byte-identical checkout is not an
        // option. Pinned so a future re-vendor that dropped it fails here rather than
        // in a confusing missing-class error.
        assertEquals(21, FsrsParameters.PARAMETER_COUNT)
        assertEquals(21, FsrsAlgorithmInfo.PARAMETER_COUNT)
        assertEquals("FSRS-6.x 21-parameter snapshot", FsrsAlgorithmInfo.ALGORITHM_LABEL)
        assertEquals("open-spaced-repetition/py-fsrs", FsrsAlgorithmInfo.UPSTREAM_REPOSITORY)
        assertEquals("v6.3.1", FsrsAlgorithmInfo.UPSTREAM_RELEASE)
        assertEquals(
            "3abe686e9c058d3f3c00bbeb92e68b71211b2b31",
            FsrsAlgorithmInfo.UPSTREAM_COMMIT,
        )

        // But Kani's storage no longer accepts an FSRS-6-shaped vector. This is the
        // check that the switch went all the way through rather than leaving one path
        // on the old parameter count: if `scheduler_fsrs_weights` still took 21 values,
        // this would pass silently and the fitter and the scheduler would disagree.
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.encodeWeights(FsrsParameters.latestDefaultValues())
        }
    }

    @Test
    fun theTwoEnginesAreDistinguishableByNumberNotJustByLabel() {
        // "Kani uses FSRS-7" is a claim about which engine the adapter reaches for, and
        // it is only checkable if the two are actually different mathematics rather
        // than a relabel — which is exactly what the upstream repository's history had
        // to establish.
        assertNotEquals(FsrsParameters.PARAMETER_COUNT, Fsrs7Parameters.PARAMETER_COUNT)
        assertNotEquals(FsrsAlgorithmInfo.ALGORITHM_LABEL, Fsrs7AlgorithmInfo.ALGORITHM_LABEL)

        // FSRS-7 schedules fractionally; FSRS-6's engine returns Int days and clamps to
        // at least 1. The clearest evidence that these are different algorithms and not
        // one with two names.
        val fractional = Fsrs7Engine.latestDefault()
            .nextIntervalDays(0.5, 0.9, 36_500.0)
        assertTrue("FSRS-7 must schedule sub-day intervals: $fractional", fractional < 1.0)
        assertTrue("interval must be positive: $fractional", fractional > 0.0)
    }

    /**
     * Kani's own one-day floor lives above the engine, not inside it.
     *
     * FSRS-6 clamped `nextIntervalDays` to `[1, max]`; FSRS-7 deliberately does not,
     * because an engine should answer "when does retrievability reach the target"
     * without assuming what the caller does with sub-day answers. Kani's answer is that
     * its `review` phase is a day-granularity long-term queue, so `LatestFsrsAdapter`
     * floors the interval it emits.
     *
     * Pinned here because the two facts are only safe together: the engine returning
     * sub-day values is correct, and the adapter flooring them is correct, but an
     * adapter that stopped flooring would let a lapsed card come due seconds later and
     * demote a rung within one session.
     */
    @Test
    fun theEngineReturnsSubDayIntervalsAndTheAdapterFloorsThem() {
        val engineInterval = Fsrs7Engine.latestDefault().nextIntervalDays(0.01, 0.9, 36_500.0)
        assertTrue("engine must not floor: $engineInterval", engineInterval < 1.0)

        val adapterResult = LatestFsrsAdapter().review(0.01, 9.5, StudyRatings.AGAIN, 30.0, 0.9)
        assertEquals(KaniFsrsReviewResult.DAY_MILLIS, adapterResult.intervalMillis)
    }

    /**
     * A `study_items` row written by the FSRS-6 scheduler still schedules.
     *
     * This is the switch's highest-risk claim, so it is tested at the scheduler rather
     * than argued in a document. `FsrsMemoryState` is shared by both engines — the same
     * `(stability, difficulty)` pair, stability in days, difficulty on the same 1..10
     * scale — so no schema migration and no state reset was needed. The stability and
     * difficulty below are literally the values the pre-switch `BridgeSchedulerTest`
     * asserted for a mature card, which is what makes this a real upgrade case rather
     * than a synthetic one.
     *
     * Intervals do move, and the assertion says so rather than pinning a number: the
     * point is that the row survives and advances, not that it advances to any
     * particular day.
     */
    @Test
    fun aRowWrittenByTheFsrs6SchedulerStillSchedulesUnderFsrs7() {
        val fsrs6Written = RecordsStudyModels.StudyItem(
            "裂", "review", 0L, 18.005, 5.99, 12,
            0, 0, 0, 0, 0, 0L, false, "tok", 18,
        ).withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW)

        val result = BridgeScheduler().applyReview(
            fsrs6Written,
            RecordsSchedulerModels.ReviewRequest("裂", "tok", "good", false, false, false, 0),
            java.util.HashSet(),
            40L * BridgeScheduler.DAY,
        )

        assertTrue(
            "a pass must grow stability, not reset it: ${result.item.stability}",
            result.item.stability > 18.005,
        )
        assertTrue(
            "difficulty must stay on the shared 1..10 scale: ${result.item.difficulty}",
            result.item.difficulty in 1.0..10.0,
        )
        assertTrue(
            "the card must be scheduled forward: ${result.item.dueAtMillis}",
            result.item.dueAtMillis > 40L * BridgeScheduler.DAY,
        )
    }
}
