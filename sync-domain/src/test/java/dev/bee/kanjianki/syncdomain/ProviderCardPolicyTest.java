package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ProviderCardPolicyTest {
    @Test
    public void fsrsValuesAreReadFromPreferredAndLegacyColumns() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.fsrsMemoryState(
                "12.5",
                null,
                null,
                "7.25",
                "0.86",
                null,
                null
        );

        assertEquals(12.5, fsrs.stability(), 0.0001);
        assertEquals(7.25, fsrs.difficulty(), 0.0001);
        assertEquals(0.86, fsrs.retrievability(), 0.0001);
    }

    @Test
    public void partialFsrsColumnsDoNotFallBackToSerializedData() {
        ProviderCardPolicy.FsrsMemoryState difficultyOnly = ProviderCardPolicy.fsrsMemoryState(
                null,
                null,
                null,
                "5.5",
                null,
                null,
                "stability=9 retrievability=0.1"
        );
        ProviderCardPolicy.FsrsMemoryState retrievabilityOnly = ProviderCardPolicy.fsrsMemoryState(
                null,
                null,
                null,
                null,
                null,
                "0.33",
                "stability=9 difficulty=8"
        );

        assertNull(difficultyOnly.stability());
        assertEquals(5.5, difficultyOnly.difficulty(), 0.0001);
        assertNull(difficultyOnly.retrievability());
        assertNull(retrievabilityOnly.stability());
        assertNull(retrievabilityOnly.difficulty());
        assertEquals(0.33, retrievabilityOnly.retrievability(), 0.0001);
    }

    @Test
    public void fsrsParsingIgnoresInvalidValuesAndUsesFiniteDataKeys() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.fsrsMemoryState(
                "NaN",
                null,
                "Infinity",
                null,
                null,
                null,
                "stability=bad difficulty=6.5 retrievability=Infinity s=3.0"
        );

        assertEquals(3.0, fsrs.stability(), 0.0001);
        assertEquals(6.5, fsrs.difficulty(), 0.0001);
        assertNull(fsrs.retrievability());
    }

    @Test
    public void blankAndNullFsrsDataProduceEmptyMemoryState() {
        ProviderCardPolicy.FsrsMemoryState blank = ProviderCardPolicy.fsrsMemoryState(null, null, null, null, null, null, "   ");
        ProviderCardPolicy.FsrsMemoryState missing = ProviderCardPolicy.fsrsMemoryState(null, null, null, null, null, null, null);

        assertNull(blank.stability());
        assertNull(blank.difficulty());
        assertNull(blank.retrievability());
        assertNull(missing.stability());
        assertNull(missing.difficulty());
        assertNull(missing.retrievability());
    }

    @Test
    public void legacyColumnsAreUsedBeforeDataFallback() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.fsrsMemoryState(
                null,
                "1.25e1",
                null,
                "4.5",
                null,
                "0.91",
                "s=3 d=8 r=0.1"
        );

        assertEquals(12.5, fsrs.stability(), 0.0001);
        assertEquals(4.5, fsrs.difficulty(), 0.0001);
        assertEquals(0.91, fsrs.retrievability(), 0.0001);
    }

    @Test
    public void legacyColumnsAreUsedWhenFsrsColumnsAreNull() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.fsrsMemoryState(
                null,
                "2.0",
                null,
                "3.0",
                null,
                "0.55",
                "stability=9 difficulty=8 retrievability=0.1"
        );

        assertEquals(2.0, fsrs.stability(), 0.0001);
        assertEquals(3.0, fsrs.difficulty(), 0.0001);
        assertEquals(0.55, fsrs.retrievability(), 0.0001);
    }

    @Test
    public void fsrsDataParserAcceptsQuotedAliasesAndLastFiniteValueWins() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.parseFsrsData(
                "'s':\"2.5\" \"difficulty\"=4.25 retrievability=bad r=0.76 s=7.5"
        );

        assertEquals(7.5, fsrs.stability(), 0.0001);
        assertEquals(4.25, fsrs.difficulty(), 0.0001);
        assertEquals(0.76, fsrs.retrievability(), 0.0001);
    }

    @Test
    public void fsrsDataParserAcceptsFullKeyNames() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.parseFsrsData(
                "stability=2.2 difficulty=3.3 retrievability=0.44"
        );

        assertEquals(2.2, fsrs.stability(), 0.0001);
        assertEquals(3.3, fsrs.difficulty(), 0.0001);
        assertEquals(0.44, fsrs.retrievability(), 0.0001);
    }

    @Test
    public void fsrsDataParserSkipsNonFiniteMatchedNumbersAndReadsLaterKeys() {
        ProviderCardPolicy.FsrsMemoryState fsrs = ProviderCardPolicy.fsrsMemoryState(
                null,
                null,
                null,
                null,
                null,
                null,
                "s=" + repeat("9", 400) + " d=5 r=0.8"
        );

        assertNull(fsrs.stability());
        assertEquals(5.0, fsrs.difficulty(), 0.0001);
        assertEquals(0.8, fsrs.retrievability(), 0.0001);
    }

    @Test
    public void parseDoubleRejectsNullInvalidAndNonFiniteValues() {
        assertNull(ProviderCardPolicy.parseDouble(null));
        assertNull(ProviderCardPolicy.parseDouble("bad"));
        assertNull(ProviderCardPolicy.parseDouble(repeat("9", 400)));
        assertNull(ProviderCardPolicy.parseDouble("1e309"));
        assertEquals(5.0, ProviderCardPolicy.parseDouble("+.5e1"), 0.0001);
    }

    @Test
    public void cardProgressReportingIsThrottledForLargeSyncs() {
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(0, 500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(1, 500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(10, 500));
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(11, 500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(20, 500));
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(25, 500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(500, 500));
    }

    @Test
    public void cardProgressReportingUsesWiderStepsForVeryLargeSyncs() {
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(10, 1500));
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(20, 1500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(50, 1500));
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(75, 1500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(1500, 1500));
    }

    @Test
    public void cardProgressReportingCoversSmallTotalsAndBoundaryValues() {
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(-1, 500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(8, 100));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(25, 100));
        assertFalse(ProviderCardPolicy.shouldReportCardProgress(49, 1500));
        assertTrue(ProviderCardPolicy.shouldReportCardProgress(100, 1500));
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
