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
            "s=" + repeatText("9", 400) + " d=5 r=0.8",
        )

        assertNull(fsrs.stability)
        assertEquals(5.0, fsrs.difficulty!!, 0.0001)
        assertEquals(0.8, fsrs.retrievability!!, 0.0001)
    }

    @Test
    fun parseDoubleRejectsNullInvalidAndNonFiniteValues() {
        assertNull(ProviderCardPolicy.parseDouble(null))
        assertNull(ProviderCardPolicy.parseDouble("bad"))
        assertNull(ProviderCardPolicy.parseDouble(repeatText("9", 400)))
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

    /**
     * Kani's suspension rule is deliberately wider than Anki's own `queue == -1`:
     * a buried card is not active study material either, and counting one as
     * mature support would credit repair for a kanji the learner is not shown.
     */
    @Test
    fun anyNegativeQueueCountsAsSuspended() {
        assertFalse(ProviderCardPolicy.isSuspendedQueue(0L))
        assertFalse(ProviderCardPolicy.isSuspendedQueue(1L))
        assertFalse(ProviderCardPolicy.isSuspendedQueue(2L))
        assertTrue(ProviderCardPolicy.isSuspendedQueue(-1L))
        assertTrue(ProviderCardPolicy.isSuspendedQueue(-2L))
        assertTrue(ProviderCardPolicy.isSuspendedQueue(-3L))
    }

    /** One card per note: the front template only, or a note double-counts. */
    @Test
    fun onlyTheFrontTemplateOrdinalIsAccepted() {
        assertTrue(ProviderCardPolicy.isAcceptedTemplateOrd(0L))
        assertFalse(ProviderCardPolicy.isAcceptedTemplateOrd(1L))
        assertFalse(ProviderCardPolicy.isAcceptedTemplateOrd(2L))
        assertFalse(ProviderCardPolicy.isAcceptedTemplateOrd(-1L))
    }

    /**
     * Anki encodes a sub-day interval as a negative `ivl` meaning seconds. Passed
     * through it would be read as a negative *day* count, and maturity compares
     * days against a positive threshold — so a card answered minutes ago would
     * sort as further from mature than one at a genuine long interval.
     */
    @Test
    fun subDayIntervalsFloorToZeroDays() {
        assertEquals(30, ProviderCardPolicy.intervalDays(30L))
        assertEquals(1, ProviderCardPolicy.intervalDays(1L))
        assertEquals(0, ProviderCardPolicy.intervalDays(0L))
        assertEquals(0, ProviderCardPolicy.intervalDays(-600L))
        assertEquals(0, ProviderCardPolicy.intervalDays(Long.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, ProviderCardPolicy.intervalDays(Int.MAX_VALUE.toLong()))
        assertEquals(Int.MAX_VALUE, ProviderCardPolicy.intervalDays(Long.MAX_VALUE))
    }

    /** A wrapped counter would read as a negative number of lapses. */
    @Test
    fun countersFloorAtZeroAndSaturateAtTheTop() {
        assertEquals(7, ProviderCardPolicy.counter(7L))
        assertEquals(0, ProviderCardPolicy.counter(0L))
        assertEquals(0, ProviderCardPolicy.counter(-5L))
        assertEquals(0, ProviderCardPolicy.counter(Long.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, ProviderCardPolicy.counter(Long.MAX_VALUE))
    }

    /** `queue` and `due` keep their sign, because the sign carries meaning. */
    @Test
    fun signedValuesKeepTheirSignAndSaturateAtBothBounds() {
        assertEquals(500, ProviderCardPolicy.signed(500L))
        assertEquals(0, ProviderCardPolicy.signed(0L))
        assertEquals(-2, ProviderCardPolicy.signed(-2L))
        assertEquals(Int.MIN_VALUE, ProviderCardPolicy.signed(Long.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, ProviderCardPolicy.signed(Long.MAX_VALUE))
    }

    private fun repeatText(value: String, count: Int): String = buildString(value.length * count) {
        repeat(count) {
            append(value)
        }
    }
}
