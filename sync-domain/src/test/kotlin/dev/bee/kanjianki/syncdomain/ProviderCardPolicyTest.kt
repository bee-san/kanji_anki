package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCardPolicyTest {
    @Test
    fun fsrsValuesAreReadFromPreferredAndLegacyColumns() {
        val fsrs = ProviderCardPolicy.fsrsMemoryState(
            "12.5",
            null,
            null,
            "7.25",
            "0.86",
            null,
            null,
        )

        assertEquals(12.5, fsrs.stability!!, 0.0001)
        assertEquals(7.25, fsrs.difficulty!!, 0.0001)
        assertEquals(0.86, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun partialFsrsColumnsDoNotFallBackToSerializedData() {
        val difficultyOnly = ProviderCardPolicy.fsrsMemoryState(
            null,
            null,
            null,
            "5.5",
            null,
            null,
            "stability=9 retrievability=0.1",
        )
        val retrievabilityOnly = ProviderCardPolicy.fsrsMemoryState(
            null,
            null,
            null,
            null,
            null,
            "0.33",
            "stability=9 difficulty=8",
        )

        assertNull(difficultyOnly.stability)
        assertEquals(5.5, difficultyOnly.difficulty!!, 0.0001)
        assertNull(difficultyOnly.retrievability)
        assertNull(retrievabilityOnly.stability)
        assertNull(retrievabilityOnly.difficulty)
        assertEquals(0.33, retrievabilityOnly.retrievability!!, 0.0001)
    }

    @Test
    fun fsrsParsingIgnoresInvalidValuesAndUsesFiniteDataKeys() {
        val fsrs = ProviderCardPolicy.fsrsMemoryState(
            "NaN",
            null,
            "Infinity",
            null,
            null,
            null,
            "stability=bad difficulty=6.5 retrievability=Infinity s=3.0",
        )

        assertEquals(3.0, fsrs.stability!!, 0.0001)
        assertEquals(6.5, fsrs.difficulty!!, 0.0001)
        assertNull(fsrs.retrievability)
    }

    @Test
    fun blankAndNullFsrsDataProduceEmptyMemoryState() {
        val blank = ProviderCardPolicy.fsrsMemoryState(null, null, null, null, null, null, "   ")
        val missing = ProviderCardPolicy.fsrsMemoryState(null, null, null, null, null, null, null)

        assertNull(blank.stability)
        assertNull(blank.difficulty)
        assertNull(blank.retrievability)
        assertNull(missing.stability)
        assertNull(missing.difficulty)
        assertNull(missing.retrievability)
    }

    @Test
    fun legacyColumnsAreUsedBeforeDataFallback() {
        val fsrs = ProviderCardPolicy.fsrsMemoryState(
            null,
            "1.25e1",
            null,
            "4.5",
            null,
            "0.91",
            "s=3 d=8 r=0.1",
        )

        assertEquals(12.5, fsrs.stability!!, 0.0001)
        assertEquals(4.5, fsrs.difficulty!!, 0.0001)
        assertEquals(0.91, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun legacyColumnsAreUsedWhenFsrsColumnsAreNull() {
        val fsrs = ProviderCardPolicy.fsrsMemoryState(
            null,
            "2.0",
            null,
            "3.0",
            null,
            "0.55",
            "stability=9 difficulty=8 retrievability=0.1",
        )

        assertEquals(2.0, fsrs.stability!!, 0.0001)
        assertEquals(3.0, fsrs.difficulty!!, 0.0001)
        assertEquals(0.55, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun fsrsDataParserAcceptsQuotedAliasesAndLastFiniteValueWins() {
        val fsrs = ProviderCardPolicy.parseFsrsData(
            "'s':\"2.5\" \"difficulty\"=4.25 retrievability=bad r=0.76 s=7.5",
        )

        assertEquals(7.5, fsrs.stability!!, 0.0001)
        assertEquals(4.25, fsrs.difficulty!!, 0.0001)
        assertEquals(0.76, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun fsrsDataParserAcceptsFullKeyNames() {
        val fsrs = ProviderCardPolicy.parseFsrsData(
            "stability=2.2 difficulty=3.3 retrievability=0.44",
        )

        assertEquals(2.2, fsrs.stability!!, 0.0001)
        assertEquals(3.3, fsrs.difficulty!!, 0.0001)
        assertEquals(0.44, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun fsrsDataParserSkipsNonFiniteMatchedNumbersAndReadsLaterKeys() {
        val fsrs = ProviderCardPolicy.fsrsMemoryState(
            null,
            null,
            null,
            null,
            null,
            null,
            "s=" + repeat("9", 400) + " d=5 r=0.8",
        )

        assertNull(fsrs.stability)
        assertEquals(5.0, fsrs.difficulty!!, 0.0001)
        assertEquals(0.8, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun parseDoubleRejectsNullInvalidAndNonFiniteValues() {
        assertNull(ProviderCardPolicy.parseDouble(null))
        assertNull(ProviderCardPolicy.parseDouble("bad"))
        assertNull(ProviderCardPolicy.parseDouble(repeat("9", 400)))
        assertNull(ProviderCardPolicy.parseDouble("1e309"))
        assertEquals(5.0, ProviderCardPolicy.parseDouble("+.5e1")!!, 0.0001)
    }

    @Test
    fun cardProgressReportingIsThrottledForLargeSyncs() {
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(0, 500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(1, 500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(10, 500))
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(11, 500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(20, 500))
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(25, 500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(500, 500))
    }

    @Test
    fun cardProgressReportingUsesWiderStepsForVeryLargeSyncs() {
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(10, 1500))
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(20, 1500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(50, 1500))
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(75, 1500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(1500, 1500))
    }

    @Test
    fun cardProgressReportingCoversSmallTotalsAndBoundaryValues() {
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(-1, 500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(8, 100))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(25, 100))
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(49, 1500))
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(100, 1500))
    }

    @Test
    fun staticWrappersStayAvailableForJavaInterop() {
        val fsrsMemoryState = ProviderCardPolicy::class.java.getMethod(
            "fsrsMemoryState",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
        )
        val parseFsrsData = ProviderCardPolicy::class.java.getMethod(
            "parseFsrsData",
            String::class.java,
        )
        val parseDouble = ProviderCardPolicy::class.java.getMethod(
            "parseDouble",
            String::class.java,
        )
        val shouldReportCardProgress = ProviderCardPolicy::class.java.getMethod(
            "shouldReportCardProgress",
            Integer.TYPE,
            Integer.TYPE,
        )

        val fsrs = fsrsMemoryState.invoke(null, "1.0", null, null, "2.0", "0.5", null, null) as ProviderCardPolicy.FsrsMemoryState
        val parsed = parseFsrsData.invoke(null, "s=3 d=4 r=0.6") as ProviderCardPolicy.FsrsMemoryState

        assertEquals(1.0, fsrs.stability!!, 0.0001)
        assertEquals(2.0, fsrs.difficulty!!, 0.0001)
        assertEquals(0.5, fsrs.retrievability!!, 0.0001)
        assertEquals(3.0, parsed.stability!!, 0.0001)
        assertEquals(4.0, parsed.difficulty!!, 0.0001)
        assertEquals(0.6, parsed.retrievability!!, 0.0001)
        assertEquals(5.0, parseDouble.invoke(null, "+.5e1") as Double, 0.0001)
        assertEquals(true, shouldReportCardProgress.invoke(null, 10, 500))
    }

    private fun repeat(value: String, count: Int): String {
        return value.repeat(count)
    }
}
