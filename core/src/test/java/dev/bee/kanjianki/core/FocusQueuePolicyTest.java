package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class FocusQueuePolicyTest {
    @Test
    public void queuedEntriesFollowFocusOrderBeforeDuePriority() {
        long now = 5_000L;
        List<FocusQueuePolicy.QueueEntry> entries = FocusQueuePolicy.queuedEntries(
                Arrays.asList(row("先", 10), row("後", 90)),
                Arrays.asList(review("先", now - 1_000L), review("後", now + 1_000L)),
                now,
                0L,
                plan("後", "先"),
                RecordsBase.StudyLadderSettings.defaults());

        assertEquals(Arrays.asList("後", "先"), kanji(entries));
    }

    @Test
    public void queuedEntriesSortByDueStateDueTimeWeaknessAndKanji() {
        long now = 5_000L;
        List<FocusQueuePolicy.QueueEntry> entries = FocusQueuePolicy.queuedEntries(
                Arrays.asList(row("新", 20), row("学", 20), row("弱", 80), row("低", 10), row("後", 20)),
                Arrays.asList(
                        item("後", StudyLadderRules.STATE_REVIEW, now + 1_000L, 1),
                        item("新", StudyLadderRules.STATE_NEW, now - 1_000L, 0),
                        item("低", StudyLadderRules.STATE_REVIEW, now - 1_000L, 1),
                        item("弱", StudyLadderRules.STATE_REVIEW, now - 1_000L, 1),
                        item("学", StudyLadderRules.STATE_LEARNING, now - 1_000L, 1)
                ),
                now,
                0L,
                null,
                RecordsBase.StudyLadderSettings.defaults());

        assertEquals(Arrays.asList("学", "弱", "低", "新", "後"), kanji(entries));
    }

    @Test
    public void queuedEntriesKeepFutureQueueItemsButDropMissingRows() {
        long now = 5_000L;
        List<FocusQueuePolicy.QueueEntry> entries = FocusQueuePolicy.queuedEntries(
                Collections.singletonList(row("待", 10)),
                Arrays.asList(review("待", now + 60_000L), review("無", now - 1L)),
                now,
                0L,
                null,
                RecordsBase.StudyLadderSettings.defaults());

        assertEquals(Collections.singletonList("待"), kanji(entries));
    }

    @Test
    public void queuedEntriesTreatMissingInputsAsEmpty() {
        List<FocusQueuePolicy.QueueEntry> entries = FocusQueuePolicy.queuedEntries(
                null,
                null,
                1L,
                0L,
                null,
                RecordsBase.StudyLadderSettings.defaults());

        assertEquals(Collections.emptyList(), entries);
    }

    @Test
    public void stateRankOrdersLearningReviewNewThenUnknownStates() {
        assertEquals(0, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_LEARNING));
        assertEquals(1, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_REVIEW));
        assertEquals(2, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_NEW));
        assertEquals(3, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_RETIRED));
        assertEquals(3, FocusQueuePolicy.stateRank(""));
    }

    private static List<String> kanji(List<FocusQueuePolicy.QueueEntry> entries) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (FocusQueuePolicy.QueueEntry entry : entries) {
            out.add(entry.row.kanji);
        }
        return out;
    }

    private static RecordsSchedulerModels.AdaptiveLoadPlan plan(String... focusKanji) {
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                false,
                100,
                focusKanji.length,
                focusKanji.length,
                Arrays.asList(focusKanji),
                focusKanji.length,
                false,
                "test"
        );
    }

    private static RecordsImportModels.DashboardRow row(String kanji, int weaknessScore) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                weaknessScore,
                "reason",
                "reason text",
                1,
                1,
                0,
                Collections.emptyList()
        );
    }

    private static RecordsStudyModels.StudyItem review(String kanji, long dueAtMillis) {
        return item(kanji, StudyLadderRules.STATE_REVIEW, dueAtMillis, 1);
    }

    private static RecordsStudyModels.StudyItem item(String kanji, String state, long dueAtMillis, int totalReviews) {
        return new RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L);
    }
}
