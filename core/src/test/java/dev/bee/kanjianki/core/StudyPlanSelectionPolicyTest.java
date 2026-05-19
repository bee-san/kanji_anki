package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class StudyPlanSelectionPolicyTest {
    @Test
    public void extraNewCardsOverrideAdaptivePlan() {
        RecordsSchedulerModels.AdaptiveLoadPlan adaptive = adaptivePlan();

        RecordsSchedulerModels.AdaptiveLoadPlan result = StudyPlanSelectionPolicy.select(
                Collections.singletonList("裂"),
                false,
                Arrays.asList(row("裂"), row("語")),
                Collections.singletonList(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
                Collections.emptySet(),
                100L,
                adaptive
        );

        assertEquals(Collections.singletonList("裂"), result.focusKanji);
        assertEquals(1, result.remaining);
        assertTrue(result.status.contains("extra new card"));
    }

    @Test
    public void continueAllOverridesAdaptivePlanWhenNoExtraCardsRequested() {
        RecordsSchedulerModels.AdaptiveLoadPlan result = StudyPlanSelectionPolicy.select(
                Collections.emptyList(),
                true,
                Arrays.asList(row("裂"), row("語")),
                Collections.singletonList(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
                Collections.singleton("語"),
                100L,
                adaptivePlan()
        );

        assertTrue(result.allKanjiMode);
        assertEquals(Arrays.asList("裂", "語"), result.focusKanji);
        assertEquals(1, result.remaining);
    }

    @Test
    public void adaptivePlanPassesThroughByDefault() {
        RecordsSchedulerModels.AdaptiveLoadPlan adaptive = adaptivePlan();

        RecordsSchedulerModels.AdaptiveLoadPlan result = StudyPlanSelectionPolicy.select(
                null,
                false,
                Collections.singletonList(row("裂")),
                Collections.singletonList(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
                Collections.emptySet(),
                100L,
                adaptive
        );

        assertSame(adaptive, result);
    }

    private static RecordsSchedulerModels.AdaptiveLoadPlan adaptivePlan() {
        return new RecordsSchedulerModels.AdaptiveLoadPlan(true, 40, 1, 1, Collections.singletonList("裂"), 0, false, "adaptive");
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                100,
                "meaning",
                "reading",
                "search",
                1,
                "reason",
                "reason text",
                1,
                0,
                0,
                Collections.emptyList()
        );
    }

    private static RecordsStudyModels.StudyItem item(String kanji, String state, long dueAtMillis, int totalReviews) {
        return new RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 0, null, 0L);
    }
}
