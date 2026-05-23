package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FocusedStudyPlanPolicyTest {
    @Test
    public void studyMoreNewCardsPlanKeepsRequestedRowOrderAndCountsDueItems() {
        long now = 2_000L;

        RecordsSchedulerModels.AdaptiveLoadPlan plan = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(
                Arrays.asList("新", "無", "裂"),
                Arrays.asList(row("裂"), row("新")),
                Arrays.asList(review("新", now - 1L, 1), review("裂", now + 1_000L, 1)),
                now);

        assertEquals(Arrays.asList("新", "裂"), plan.focusKanji);
        assertEquals(2, plan.target);
        assertEquals(1, plan.remaining);
        assertEquals(0, plan.newAdmissionLimit);
        assertFalse(plan.allKanjiMode);
        assertEquals("Custom study: 2 extra new cards.", plan.status);
    }

    @Test
    public void studyMoreNewCardsPlanFormatsSingularAndEmptyStatuses() {
        long now = 2_000L;

        RecordsSchedulerModels.AdaptiveLoadPlan one = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(
                Collections.singletonList("新"),
                Collections.singletonList(row("新")),
                Collections.singletonList(review("新", now, 1)),
                now);
        RecordsSchedulerModels.AdaptiveLoadPlan empty = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(
                null,
                Collections.singletonList(row("新")),
                null,
                now);

        assertEquals("Custom study: 1 extra new card.", one.status);
        assertEquals("Custom study: 0 extra new cards.", empty.status);
        assertEquals(0, empty.remaining);
    }

    @Test
    public void allCurrentProblemKanjiPlanCountsUnstudiedAndDueStudiedItems() {
        long now = 10_000L;

        RecordsSchedulerModels.AdaptiveLoadPlan plan = FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(
                Arrays.asList(row("未"), row("済"), row("待")),
                Arrays.asList(review("未", now + 1_000L, 1), review("済", now - 1L, 1), learning("待", now + 1_000L)),
                new HashSet<>(Arrays.asList("済", "待")),
                now);

        assertEquals(Arrays.asList("未", "済", "待"), plan.focusKanji);
        assertEquals(3, plan.target);
        assertEquals(2, plan.remaining);
        assertEquals(3, plan.newAdmissionLimit);
        assertTrue(plan.allKanjiMode);
        assertEquals("All current problem kanji are available today.", plan.status);
    }

    @Test
    public void allCurrentProblemKanjiPlanTreatsMissingInputsAsEmpty() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(
                null,
                null,
                null,
                1L);

        assertEquals(Collections.emptyList(), plan.focusKanji);
        assertEquals(0, plan.remaining);
        assertTrue(plan.allKanjiMode);
    }

    @Test
    public void itemDueForFocusPreservesStudyModeDueRules() {
        long now = 5_000L;

        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(null, now));
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(review("退", now - 1L, 3).copyBuilder().state(StudyLadderRules.STATE_RETIRED).build(), now));
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(learning("学", now + 1L), now));
        assertTrue(FocusedStudyPlanPolicy.itemDueForFocus(learning("学", now), now));
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(review("新", now - 1L, 0), now));
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(review("復", now + 1L, 1), now));
        assertTrue(FocusedStudyPlanPolicy.itemDueForFocus(review("復", now, 1), now));
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                40,
                "reason",
                "reason text",
                1,
                1,
                0,
                Collections.emptyList()
        );
    }

    private static RecordsStudyModels.StudyItem review(String kanji, long dueAtMillis, int totalReviews) {
        return new RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_REVIEW, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L);
    }

    private static RecordsStudyModels.StudyItem learning(String kanji, long dueAtMillis) {
        return new RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_LEARNING, dueAtMillis, 1.0, 5.0, 1, 0, 0, 1, null, 0L);
    }
}
