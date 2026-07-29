package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7AlgorithmInfo
import dev.bee.fsrs.Fsrs7Engine
import dev.bee.fsrs.Fsrs7Parameters
import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the identity of the vendored FSRS engine.
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
    fun kaniStillSchedulesWithTheTwentyOneParameterFsrs6Engine() {
        // Kani's scheduler reaches for FsrsEngine (FSRS-6), not Fsrs7Engine, and
        // vendoring did not change that. README.md advertises "FSRS 7", which is an
        // intention rather than the current scheduler; PROVENANCE.md says so in full and
        // lists what adoption would require.
        //
        // Asserted by parameter count rather than in prose, because the count is the
        // thing that distinguishes the two algorithms. If someone points
        // LatestFsrsAdapter at Fsrs7Engine, the stored-state and weight-storage
        // consequences described in PROVENANCE.md come due, and this test is where that
        // decision gets made explicitly instead of by accident.
        assertEquals(21, FsrsParameters.PARAMETER_COUNT)
        assertEquals(21, FsrsAlgorithmInfo.PARAMETER_COUNT)
        assertEquals("FSRS-6.x 21-parameter snapshot", FsrsAlgorithmInfo.ALGORITHM_LABEL)

        // The fitter's hardcoded bounds arrays are sized to this count, and
        // `scheduler_fsrs_weights` persists exactly this many values. Probing the last
        // valid index proves the bounds arrays agree with the engine; a 35-parameter
        // engine underneath would leave indices 21..34 unbounded.
        val lastIndex = FsrsParameters.PARAMETER_COUNT - 1
        assertTrue(
            "fitter bounds must cover parameter $lastIndex",
            FsrsWeightFitter.lowerBound(lastIndex) <= FsrsWeightFitter.upperBound(lastIndex),
        )

        // And `scheduler_fsrs_weights` round-trips exactly this many values.
        val encoded = FsrsPersonalization.encodeWeights(FsrsParameters.latestDefaultValues())
        assertEquals(FsrsParameters.PARAMETER_COUNT, encoded.split(',').size)
    }

    @Test
    fun theFsrs6DefaultParametersAreExactlyTheExpectedTwentyOneValues() {
        // The most consequential possible regression: changing a default silently
        // reschedules every existing user's whole queue, including the fixed-0.90
        // promotionIntervalDays signal the ladder keys off. Pinned by value.
        val expected = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133,
            0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483,
            0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
            0.1542,
        )
        val actual = FsrsParameters.latestDefaultValues()
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, value ->
            assertEquals("FSRS-6 parameter $index", value, actual[index], 0.0)
        }
    }

    @Test
    fun theFsrs6UpstreamSourceIsPinnedExactly() {
        // Recorded so a schedule computed years ago stays explainable.
        assertEquals("open-spaced-repetition/py-fsrs", FsrsAlgorithmInfo.UPSTREAM_REPOSITORY)
        assertEquals("v6.3.1", FsrsAlgorithmInfo.UPSTREAM_RELEASE)
        assertEquals(
            "3abe686e9c058d3f3c00bbeb92e68b71211b2b31",
            FsrsAlgorithmInfo.UPSTREAM_COMMIT,
        )
    }

    @Test
    fun theVendoredCopyAlsoCarriesTheFsrs7Engine() {
        // Vendoring 0.2.0 is what brings FSRS-7 into the repository. It is unused by
        // Kani's scheduler today, so this asserts only that it arrived intact and is
        // reachable from :core — if a future re-vendor silently dropped back to 0.1.0,
        // an "adopt FSRS-7" change would otherwise fail with a confusing missing-class
        // error rather than here.
        assertEquals(35, Fsrs7Parameters.PARAMETER_COUNT)
        assertEquals(35, Fsrs7AlgorithmInfo.PARAMETER_COUNT)
        assertEquals("FSRS-7 35-parameter snapshot", Fsrs7AlgorithmInfo.ALGORITHM_LABEL)

        // Pinned to a commit and a blob, not a release: FSRS-7 has no release. It is
        // research code in a repository whose main moves, so "the FSRS-7 in
        // srs-benchmark" does not identify anything without a hash.
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
    fun theTwoEnginesAreDistinguishableByNumberNotJustByLabel() {
        // Both are vendored, so "Kani uses FSRS-6" is a claim about which engine the
        // adapter reaches for. That claim is only checkable if the two are actually
        // different mathematics rather than a relabel — which is exactly what the
        // upstream repository's history had to establish.
        assertNotEquals(FsrsParameters.PARAMETER_COUNT, Fsrs7Parameters.PARAMETER_COUNT)
        assertNotEquals(FsrsAlgorithmInfo.ALGORITHM_LABEL, Fsrs7AlgorithmInfo.ALGORITHM_LABEL)

        // FSRS-7 schedules fractionally; FSRS-6's engine returns Int days. The clearest
        // evidence that these are different algorithms and not one with two names.
        val fractional = Fsrs7Engine.latestDefault()
            .nextIntervalDays(0.5, 0.9, 36_500.0)
        assertTrue("FSRS-7 must schedule sub-day intervals: $fractional", fractional < 1.0)
        assertTrue("interval must be positive: $fractional", fractional > 0.0)
    }
}
