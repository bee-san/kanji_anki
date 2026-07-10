package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FsrsFitSummaryCodecTest {
    @Test
    fun fitSummaryJsonRoundTripsFiniteAndMissingLosses() {
        val summary = FsrsFitSummary(1840, 1472, 368, 0.44, 0.5, 0.42, 0.484, true, "adopted", 1234L)
        val decoded = FsrsFitSummaryCodec.decode(FsrsFitSummaryCodec.encode(summary))!!

        assertEquals(summary, decoded)
        assertEquals(0.032, decoded.relativeImprovement()!!, 1e-12)

        val missing = summary.copy(defaultValidationLoss = null, fittedValidationLoss = Double.NaN)
        val missingDecoded = FsrsFitSummaryCodec.decode(FsrsFitSummaryCodec.encode(missing))!!
        assertNull(missingDecoded.defaultValidationLoss)
        assertNull(missingDecoded.fittedValidationLoss)
        assertNull(missingDecoded.relativeImprovement())
        assertNull(FsrsFitSummaryCodec.decode("not-json"))
        assertNull(FsrsFitSummaryCodec.decode(""))
    }
}
