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
    public void verdictTitlePreservesWorkingAndNotWorkingCopy() {
        assertEquals("Kani is working for you", StatsTextCopy.verdictTitle(true));
        assertEquals("Kani is not currently working for you", StatsTextCopy.verdictTitle(false));
    }

    @Test
    public void verdictBodyPreservesEmptyAndLadderOnlyCopy() {
        assertEquals(
                "No Kani evidence is available yet. Study weak kanji, then sync AnkiDroid so this page can compare before and after.",
                StatsTextCopy.verdictBody(false, false, false, 0, 0, 0, 0, 0)
        );
        assertEquals(
                "Kani is tracking 2 active kanji, but no weakness burn-down or mature Anki support conversion has landed yet. Study due reviews, then sync AnkiDroid.",
                StatsTextCopy.verdictBody(true, false, true, 0, 0, 0, 0, 2)
        );
        assertEquals(
                "No before-and-after evidence yet. Do Kani reviews, then sync AnkiDroid so this page can compare weak kanji and mature support.",
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
}
