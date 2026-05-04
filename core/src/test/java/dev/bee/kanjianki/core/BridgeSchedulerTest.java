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
        Records.StudyItem item = item("裂").withToken("token-1");
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", true, false, false, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("again", result.appliedRating);
        assertEquals("learning", result.item.state);
        assertEquals(1, result.item.lapses);
    }

    @Test
    public void manualOverrideAllowsWritingRating() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = item("裂").withToken("token-1");
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", true, false, true, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("good", result.appliedRating);
        assertFalse(result.duplicate);
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
        assertTrue(session.writingRequired);
        assertEquals("context_writing", session.taskType);
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

    private Records.DashboardRow row(String kanji, int score) {
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>());
    }
}
