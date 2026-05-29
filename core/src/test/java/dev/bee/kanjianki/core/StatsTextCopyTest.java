package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StatsTextCopyTest {
    @Test
    public void verdictFlagsPreserveStatsPanelBranches() {
        assertFalse(StatsTextCopy.verdictWorking(0, 0));
        assertTrue(StatsTextCopy.verdictWorking(1, 0));
        assertTrue(StatsTextCopy.verdictWorking(0, 1));
        assertFalse(StatsTextCopy.verdictHasLadder(0));
        assertTrue(StatsTextCopy.verdictHasLadder(1));
    }

    @Test
    public void verdictTitlePreservesWorkingAndWaitingCopy() {
        assertEquals("Kani is working for you", StatsTextCopy.verdictTitle(true));
        assertEquals("Waiting for Kani evidence", StatsTextCopy.verdictTitle(false));
    }

    @Test
    public void verdictBodyKeepsEmptyAndLadderOnlyCopyBrief() {
        assertEquals(
                "Study in Kani, then sync AnkiDroid to unlock trends.",
                StatsTextCopy.verdictBody(false, false, false, 0, 0, 0, 0, 0)
        );
        assertEquals(
                "Kani is tracking 2 active kanji. Trends appear after reviews and sync.",
                StatsTextCopy.verdictBody(true, false, true, 0, 0, 0, 0, 2)
        );
        assertEquals(
                "Do Kani reviews, then sync AnkiDroid to compare before and after.",
                StatsTextCopy.verdictBody(true, false, false, 0, 0, 0, 0, 0)
        );
    }

    @Test
    public void verdictBodyPreservesWorkingSignalsAndRiskCopy() {
        assertEquals(
                "1 weak kanji is burning down. 2 mature Anki cards have been gained. 3 review-phase items crossed the FSRS climb threshold. Watch 1 review-phase item with a miss streak.",
                StatsTextCopy.verdictBody(true, true, true, 1, 2, 3, 1, 4)
        );
    }

    @Test
    public void ladderHealthBodyDemotesThresholdDetailsAndKeepsEmptyCopyBrief() {
        assertEquals(
                "No active ladder items yet. Sync or study weak kanji to fill the ladder.",
                StatsTextCopy.ladderHealthBody(0, 0, 0, 0, 21, 3)
        );
        assertEquals(
                "2 ready to climb · 1 at risk · 1 ready to fall. Rules: climb after more than 21 days; fall after 3 misses.",
                StatsTextCopy.ladderHealthBody(5, 2, 1, 1, 21, 3)
        );
    }

    @Test
    public void ladderDistributionRowsPreserveRungLabels() {
        assertEquals("Write kanji: 2", StatsTextCopy.ladderDistributionRow(RecordsBase.LadderRung.WRITE_KANJI, 2));
        assertEquals("Type meaning: 0", StatsTextCopy.ladderDistributionRow(RecordsBase.LadderRung.TYPE_MEANING, 0));
        assertEquals("Similar kanji: 1", StatsTextCopy.ladderDistributionRow(RecordsBase.LadderRung.SIMILAR_KANJI, 1));
        assertEquals("Meaning kanji", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.MEANING_KANJI));
        assertEquals("Kanji meaning", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals("Font meaning", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.FONT_MEANING));
        assertEquals("Word reading", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.WORD_READING));
    }

    @Test
    public void weaknessAndSupportFormattingPreservesStatsRows() {
        assertEquals(
                "Weakness trends appear after Kani reviews and a successful AnkiDroid sync.",
                StatsTextCopy.weaknessImprovementBody(0, 0.0, 0.0)
        );
        assertEquals(
                "Average weakness: 0.80 -> 0.25.",
                StatsTextCopy.weaknessImprovementBody(2, 0.8, 0.25)
        );
        assertEquals("裂  0.80 -> 0.25", StatsTextCopy.weaknessImprovementExample("裂", 0.8, 0.25));
        assertEquals("裂  1 -> 3 mature cards", StatsTextCopy.supportGainExample("裂", 1, 3));
    }

    @Test
    public void impactAndTimeFormattingPreservesStatsHelpers() {
        assertEquals(
                "Review in Kani, then sync AnkiDroid to compare before and after.",
                StatsTextCopy.notHelpingBody(true, false)
        );
        assertEquals(
                "No kanji need attention right now.",
                StatsTextCopy.notHelpingBody(false, false)
        );
        assertEquals(
                "Shown only after enough Kani reviews and synced Anki evidence.",
                StatsTextCopy.notHelpingBody(false, true)
        );
        assertEquals("裂  3 Kani reviews · 2 same-card checks · retention +12% · difficulty -0.4", StatsTextCopy.notHelpingRowText("裂", 3, 2, 0.12, -0.4));
        assertEquals("0 sec", StatsTextCopy.formatStudyTime(-1));
        assertEquals("59 sec", StatsTextCopy.formatStudyTime(59_000));
        assertEquals("1 min", StatsTextCopy.formatStudyTime(60_000));
        assertEquals("1 min 5 sec", StatsTextCopy.formatStudyTime(65_000));
        assertEquals("1 hr", StatsTextCopy.formatStudyTime(3_600_000));
        assertEquals("1 hr 2 min", StatsTextCopy.formatStudyTime(3_720_000));
    }
}
