package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class StudySessionFocusPolicyTest {
    @Test
    public void focusedModeLimitsSchedulerToFocusKanjiCopy() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(false, "裂", "語", "裂");

        Set<String> allowed = StudySessionFocusPolicy.allowedKanji(plan, false);

        assertEquals(2, allowed.size());
        assertTrue(allowed.contains("裂"));
        assertTrue(allowed.contains("語"));
        assertNotSame(plan.focusKanji, allowed);
    }

    @Test
    public void continueAllKanjiUsesUnrestrictedScheduler() {
        assertNull(StudySessionFocusPolicy.allowedKanji(plan(false, "裂"), true));
    }

    @Test
    public void allKanjiPlanUsesUnrestrictedScheduler() {
        assertNull(StudySessionFocusPolicy.allowedKanji(plan(true, "裂"), false));
    }

    @Test
    public void emptyFocusedPlanPreservesEmptyAllowedSet() {
        assertEquals(Collections.emptySet(), StudySessionFocusPolicy.allowedKanji(plan(false), false));
    }

    @Test
    public void nullPlanIsRejectedLikePreviousDirectPlanAccess() {
        assertThrows(NullPointerException.class, () -> StudySessionFocusPolicy.allowedKanji(null, false));
    }

    private static RecordsSchedulerModels.AdaptiveLoadPlan plan(boolean allKanjiMode, String... focusKanji) {
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                25,
                focusKanji.length,
                focusKanji.length,
                Arrays.asList(focusKanji),
                focusKanji.length,
                allKanjiMode,
                "status"
        );
    }
}
