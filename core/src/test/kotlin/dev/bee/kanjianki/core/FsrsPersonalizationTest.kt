package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7Parameters
import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FsrsPersonalizationTest {
    @Test
    fun fullPrecisionCodecRoundTripsAndRejectsInvalidVectors() {
        val weights = Fsrs7Parameters.latestDefaultValues().also {
            it[9] = 1.3325000000000001
            it[26] = 0.0658
        }
        val encoded = FsrsPersonalization.encodeWeights(weights)

        assertArrayEquals(weights, FsrsPersonalization.decodeWeights(encoded), 0.0)
        assertEquals(Fsrs7Parameters.PARAMETER_COUNT, encoded.split(',').size)
        assertEquals("0.0658", encoded.split(',')[26])
        assertNull(FsrsPersonalization.decodeWeights("  "))
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.decodeWeights(weights.dropLast(1).joinToString(","))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.decodeWeights(weights.clone().also { it[0] = Double.NaN }.joinToString(","))
        }
        assertThrows(IllegalArgumentException::class.java) {
            // Out of bounds: w[25] (the long/short-term transition rate) has an
            // inclusive lower bound of 2.5.
            FsrsPersonalization.decodeWeights(weights.clone().also { it[25] = 0.0 }.joinToString(","))
        }
    }

    /**
     * A stored FSRS-6 vector is rejected rather than migrated.
     *
     * There is no correct migration: 21 values fitted for FSRS-6's single power law
     * do not describe FSRS-7's blended pair, so padding or reinterpreting them would
     * schedule against a parameter set no optimizer ever validated. Rejecting means
     * `LocalStoreStudySettings.schedulerFsrsWeights` logs once and falls open to the
     * FSRS-7 defaults, and the weekly fitter re-earns a vector from the review
     * history the device already has.
     *
     * This is the one user-visible cost of the FSRS-7 switch, so it is pinned rather
     * than left to the generic "wrong length" case.
     */
    @Test
    fun aStoredFsrs6VectorIsRejectedSoTheReaderFallsOpenToFsrs7Defaults() {
        val fsrs6 = FsrsParameters.latestDefaultValues()
        assertEquals(21, fsrs6.size)

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.decodeWeights(fsrs6.joinToString(","))
        }
        assertEquals(
            "FSRS-7 requires exactly 35 parameters, got 21",
            thrown.message,
        )
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.encodeWeights(fsrs6)
        }
    }
}
