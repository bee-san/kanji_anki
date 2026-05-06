package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BridgeSchedulerTest {
    @Test
    public void seedsQueueWithDailyNewAndActiveCaps() {
        Records.Settings settings = new Records.Settings("Kiku", "Mining", "Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", 21, 2, 3000, 2, 1);
        List<Records.DashboardRow> rows = Arrays.asList(row("裂", 20), row("謎", 19), row("示", 18));

        List<Records.StudyItem> items = new BridgeScheduler().seedQueue(rows, Collections.emptyList(), settings, 1000L, 0L);

        assertEquals(1, items.size());
        assertEquals("裂", items.get(0).kanji);
    }

    @Test
    public void writingFailureCapsGoodRatingToAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0);
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", true, false, false, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("again", result.appliedRating);
        assertEquals("learning", result.item.state);
        assertEquals(1, result.item.lapses);
        assertEquals(1, result.item.writingLevel);
    }

    @Test
    public void manualOverrideAllowsWritingRating() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0);
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", true, false, true, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("good", result.appliedRating);
        assertFalse(result.duplicate);
        assertEquals(2, result.item.writingLevel);
    }

    @Test
    public void writingHelpOnlyChangesAfterWritingReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0);
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "easy", false, false, false, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("easy", result.appliedRating);
        assertEquals(2, result.item.writingLevel);
    }

    @Test
    public void cleanWritingAdvancesHintAssistedAndMessyWritingHold() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 1, "clean", 0);

        Records.ReviewResult clean = scheduler.applyReview(
                item,
                new Records.ReviewRequest("裂", "clean", "hard", true, true, true, false, 0),
                new HashSet<>(),
                1000L
        );
        Records.ReviewResult hinted = scheduler.applyReview(
                new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 1, "hinted", 0),
                new Records.ReviewRequest("裂", "hinted", "good", true, true, true, false, 1),
                new HashSet<>(),
                1000L
        );
        Records.ReviewResult messy = scheduler.applyReview(
                new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 1, "messy", 0),
                new Records.ReviewRequest("裂", "messy", "hard", true, true, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(2, clean.item.writingLevel);
        assertEquals(1, hinted.item.writingLevel);
        assertEquals(1, messy.item.writingLevel);
    }

    @Test
    public void duplicateTokenDoesNotAdvanceTwice() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = item("裂").withToken("token-1");
        HashSet<String> consumed = new HashSet<>();
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "easy", false, false, false, 0);

        Records.ReviewResult first = scheduler.applyReview(item, request, consumed, 1000L);
        Records.ReviewResult second = scheduler.applyReview(first.item.withToken("token-1"), request, consumed, 2000L);

        assertFalse(first.duplicate);
        assertTrue(second.duplicate);
        assertEquals(first.item.totalReviews, second.item.totalReviews);
    }

    @Test
    public void nextSessionRotatesTaskShape() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudySession session = scheduler.nextSession(Arrays.asList(item("裂")), Arrays.asList(row("裂", 10)), 1000L);

        assertNotNull(session);
        assertFalse(session.writingRequired);
        assertEquals("meaning_flashcard", session.taskType);
    }

    @Test
    public void lapsedKanjiReturnsAsFontRecognitionBeforeMoreWriting() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem lapsed = new Records.StudyItem("裂", "learning", 0L, 0.3, 6.0, 1, 1, 0, 0, null, 0L);

        Records.StudySession session = scheduler.nextSession(
                Collections.singletonList(lapsed),
                Collections.singletonList(row("裂", 10)),
                1000L
        );

        assertNotNull(session);
        assertFalse(session.writingRequired);
        assertEquals("font_recognition", session.taskType);
    }

    @Test
    public void nextSessionPrioritizesDueReviewsBeforeNewCards() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem newProblem = new Records.StudyItem("裂", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);
        Records.StudyItem dueReview = new Records.StudyItem("謎", "review", 500L, 1.8, 4.8, 2, 0, 2, 3, null, 0L);

        Records.StudySession session = scheduler.nextSession(
                Arrays.asList(newProblem, dueReview),
                Arrays.asList(row("裂", 30), row("謎", 20)),
                1000L
        );

        assertNotNull(session);
        assertEquals("謎", session.item.kanji);
        assertEquals("blind_writing", session.taskType);
        assertTrue(session.writingRequired);
    }

    @Test
    public void seedQueueRetiresItemsMissingFromDashboardRows() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.Settings settings = new Records.Settings("Kiku", "Mining", "Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", 21, 1, 3000, 1, 2);
        Records.StudyItem stale = new Records.StudyItem("古", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.singletonList(stale),
                settings,
                1000L,
                0L
        );

        assertEquals("new", findItem(items, "裂").state);
        assertEquals("retired", findItem(items, "古").state);
    }

    @Test
    public void seedQueueRetiresReviewedItemsWithEnoughMatureSupport() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem reviewed = new Records.StudyItem("裂", "review", 0L, 1.5, 4.0, 3, 0, 2, 2, null, 0L);
        Records.DashboardRow covered = new Records.DashboardRow("裂", 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2, new ArrayList<>());

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(covered),
                Collections.singletonList(reviewed),
                Records.Settings.kikuDefaults(),
                1000L,
                0L
        );

        assertEquals("retired", findItem(items, "裂").state);
    }

    @Test
    public void seedQueueReopensRetiredItemsWhenWeakEvidenceReturns() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem retired = new Records.StudyItem("裂", "retired", 0L, 1.5, 4.0, 3, 0, 2, 2, null, 0L);

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.singletonList(retired),
                Records.Settings.kikuDefaults(),
                1000L,
                0L
        );

        Records.StudyItem reopened = findItem(items, "裂");
        assertEquals("new", reopened.state);
        assertEquals(0, reopened.totalReviews);
        assertEquals(1000L, reopened.createdAtMillis);
    }

    @Test
    public void adaptivePlanLimitsNewAdmissionsToFocusSet() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.AdaptiveLoadPlan plan = new Records.AdaptiveLoadPlan(
                20,
                1,
                1,
                Collections.singletonList("謎"),
                1,
                false,
                "focus"
        );

        List<Records.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(row("裂", 50), row("謎", 10)),
                Collections.emptyList(),
                Records.Settings.kikuDefaults(),
                1000L,
                0L,
                plan
        );

        assertEquals(1, items.size());
        assertEquals("謎", items.get(0).kanji);
    }

    @Test
    public void allKanjiAdaptivePlanAdmitsEveryCandidate() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.AdaptiveLoadPlan plan = new Records.AdaptiveLoadPlan(
                100,
                3,
                3,
                Arrays.asList("裂", "謎", "示"),
                3,
                true,
                "all"
        );

        List<Records.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(row("裂", 50), row("謎", 10), row("示", 5)),
                Collections.emptyList(),
                new Records.Settings("Kiku", "Mining", "Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", 21, 2, 3000, 1, 1),
                1000L,
                0L,
                plan
        );

        assertEquals(3, items.size());
    }

    @Test
    public void nextSessionSkipsItemsWithoutCurrentDashboardRows() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem stale = new Records.StudyItem("古", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);
        Records.StudyItem current = new Records.StudyItem("裂", "new", 500L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);

        Records.StudySession session = scheduler.nextSession(
                Arrays.asList(stale, current),
                Collections.singletonList(row("裂", 30)),
                1000L
        );

        assertNotNull(session);
        assertEquals("裂", session.item.kanji);
    }

    @Test
    public void reseedPreservesExistingProgressForSameKanji() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem learned = new Records.StudyItem("裂", "learning", 1234L, 1.2, 4.4, 2, 1, 1, 2, "active", 55L);

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.singletonList(learned),
                Records.Settings.kikuDefaults(),
                2000L,
                0L
        );

        Records.StudyItem item = findItem(items, "裂");
        assertEquals("learning", item.state);
        assertEquals(1234L, item.dueAtMillis);
        assertEquals(2, item.totalReviews);
        assertEquals(1, item.lapses);
        assertEquals(2, item.writingLevel);
        assertEquals("active", item.activeToken);
    }

    @Test
    public void reviewSequenceLearnsThenReviewsAndMissReturnsSoon() {
        BridgeScheduler scheduler = new BridgeScheduler();
        List<Records.DashboardRow> rows = Collections.singletonList(row("裂", 30));
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult first = scheduler.applyReview(
                item("裂").withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "good", false, false, false, 0),
                consumed,
                0L
        );
        assertEquals("learning", first.item.state);
        assertEquals(1, first.item.learningStep);
        assertEquals(0, first.item.writingLevel);
        assertEquals(600_000L, first.item.dueAtMillis);
        assertNull(scheduler.nextSession(Collections.singletonList(first.item), rows, 599_999L));

        Records.StudySession dueLearning = scheduler.nextSession(Collections.singletonList(first.item), rows, 600_000L);
        assertNotNull(dueLearning);
        assertEquals("guided_writing", dueLearning.taskType);
        assertTrue(dueLearning.writingRequired);

        Records.ReviewResult second = scheduler.applyReview(
                first.item.withToken("t2"),
                new Records.ReviewRequest("裂", "t2", "good", true, true, false, 0),
                consumed,
                600_000L
        );
        assertEquals("review", second.item.state);
        assertEquals(2, second.item.learningStep);
        assertEquals(1, second.item.writingLevel);

        Records.StudyItem dueReview = new Records.StudyItem(
                second.item.kanji,
                second.item.state,
                900_000L,
                second.item.stability,
                second.item.difficulty,
                second.item.totalReviews,
                second.item.lapses,
                second.item.learningStep,
                second.item.writingLevel,
                "t3",
                second.item.createdAtMillis
        );
        Records.ReviewResult miss = scheduler.applyReview(
                dueReview,
                new Records.ReviewRequest("裂", "t3", "again", true, false, false, 0),
                consumed,
                900_000L
        );
        assertEquals("learning", miss.item.state);
        assertEquals(1, miss.item.lapses);
        assertEquals(0, miss.item.learningStep);
        assertEquals(0, miss.item.writingLevel);
        assertEquals(1_500_000L, miss.item.dueAtMillis);
    }

    @Test
    public void customParametersAffectReviewInterval() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 3.0, 5.0, 3, 0, 2, 1, "token-1", 0);
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", false, false, false, 0);
        Records.SchedulerParameters shorter = new Records.SchedulerParameters(0.90, 0.45, 1.2, 1.4, 2.2, 0, 0);
        Records.SchedulerParameters longer = new Records.SchedulerParameters(0.90, 0.45, 1.2, 2.8, 4.2, 0, 0);

        Records.ReviewResult shortResult = scheduler.applyReview(item, request, new HashSet<>(), 1000L, shorter);
        Records.ReviewResult longResult = scheduler.applyReview(item, request, new HashSet<>(), 1000L, longer);

        assertTrue(longResult.item.dueAtMillis > shortResult.item.dueAtMillis);
    }

    private Records.StudyItem item(String kanji) {
        return new Records.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, null, 0);
    }

    private Records.StudyItem findItem(List<Records.StudyItem> items, String kanji) {
        for (Records.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + kanji);
    }

    private Records.DashboardRow row(String kanji, int score) {
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>());
    }
}
