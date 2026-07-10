package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FsrsPersonalizationTest {
    @Test
    fun fullPrecisionCodecRoundTripsAndRejectsInvalidVectors() {
        val weights = FsrsParameters.latestDefaultValues().also {
            it[0] = 0.12345678901234567
            it[19] = 0.0658
        }
        val encoded = FsrsPersonalization.encodeWeights(weights)

        assertArrayEquals(weights, FsrsPersonalization.decodeWeights(encoded), 0.0)
        assertEquals("0.0658", encoded.split(',')[19])
        assertNull(FsrsPersonalization.decodeWeights("  "))
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.decodeWeights(weights.dropLast(1).joinToString(","))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.decodeWeights(weights.clone().also { it[0] = Double.NaN }.joinToString(","))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FsrsPersonalization.decodeWeights(weights.clone().also { it[20] = 0.0 }.joinToString(","))
        }
    }
}
