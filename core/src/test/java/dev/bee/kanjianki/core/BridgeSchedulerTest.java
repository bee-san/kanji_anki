package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Records.StudySession typing = scheduler.nextSession(Arrays.asList(item("拉", -1)), Arrays.asList(row("拉", 10)), 1000L);
        Records.StudySession simple = scheduler.nextSession(Arrays.asList(item("裂", 0)), Arrays.asList(row("裂", 10)), 1000L);
        Records.StudySession font = scheduler.nextSession(Arrays.asList(item("謎", 1)), Arrays.asList(row("謎", 10)), 1000L);
        Records.StudySession word = scheduler.nextSession(Arrays.asList(item("示", 2)), Arrays.asList(row("示", 10)), 1000L);

        assertNotNull(typing);
        assertNotNull(simple);
        assertNotNull(font);
        assertNotNull(word);
        assertFalse(typing.writingRequired);
        assertFalse(simple.writingRequired);
        assertFalse(font.writingRequired);
        assertFalse(word.writingRequired);
        assertEquals("typing_meaning", typing.taskType);
        assertEquals("kanji_meaning", simple.taskType);
        assertEquals("font_meaning", font.taskType);
        assertEquals("word_reading", word.taskType);
    }

    @Test
    public void pendingRemediationRoutesToWritingTask() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem pending = new Records.StudyItem("裂", "learning", 0L, 0.3, 6.0, 3, 3, 0, 0, 0, 3, 0L, true, null, 0L);

        Records.StudySession session = scheduler.nextSession(
                Collections.singletonList(pending),
                Collections.singletonList(row("裂", 10)),
                1000L
        );

        assertNotNull(session);
        assertTrue(session.writingRequired);
        assertEquals("writing_remediation", session.taskType);
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
        assertEquals("kanji_meaning", session.taskType);
        assertFalse(session.writingRequired);
    }

    @Test
    public void nextSessionTreatsRemediationAndRelearningAsUrgentBeforeReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem review = new Records.StudyItem("謎", "review", 0L, 2.0, 4.0, 4, 0, 2, 2, null, 0L);
        Records.StudyItem relearning = new Records.StudyItem("習", "new", 500L, 0.9, 5.5, 2, 1, 1, 1, null, 0L);
        Records.StudyItem remediation = new Records.StudyItem("裂", "review", 0L, 1.0, 5.0, 5, 2, 2, 1, 2, 3, 0L, true, null, 0L);

        Records.StudySession remediationSession = scheduler.nextSession(
                Arrays.asList(review, relearning, remediation),
                Arrays.asList(row("謎", 100), row("習", 10), row("裂", 1)),
                1000L
        );
        Records.StudySession relearningSession = scheduler.nextSession(
                Arrays.asList(review, relearning),
                Arrays.asList(row("謎", 100), row("習", 10)),
                1000L
        );

        assertNotNull(remediationSession);
        assertEquals("裂", remediationSession.item.kanji);
        assertEquals("writing_remediation", remediationSession.taskType);
        assertNotNull(relearningSession);
        assertEquals("習", relearningSession.item.kanji);
        assertEquals("kanji_meaning", relearningSession.taskType);
    }

    @Test
    public void nextSessionUsesWeaknessAndKanjiTieBreakersForSamePriorityDueItems() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem lowerWeakness = new Records.StudyItem("謎", "review", 0L, 2.0, 4.0, 4, 0, 2, 1, null, 0L);
        Records.StudyItem higherWeakness = new Records.StudyItem("裂", "review", 0L, 2.0, 4.0, 4, 0, 2, 1, null, 0L);
        Records.StudyItem firstKanji = new Records.StudyItem("亜", "review", 0L, 2.0, 4.0, 4, 0, 2, 1, null, 0L);
        Records.StudyItem laterKanji = new Records.StudyItem("唖", "review", 0L, 2.0, 4.0, 4, 0, 2, 1, null, 0L);

        Records.StudySession weaknessSession = scheduler.nextSession(
                Arrays.asList(lowerWeakness, higherWeakness),
                Arrays.asList(row("謎", 10), row("裂", 80)),
                1000L
        );
        Records.StudySession kanjiSession = scheduler.nextSession(
                Arrays.asList(laterKanji, firstKanji),
                Arrays.asList(row("唖", 20), row("亜", 20)),
                1000L
        );

        assertNotNull(weaknessSession);
        assertEquals("裂", weaknessSession.item.kanji);
        assertNotNull(kanjiSession);
        assertEquals("亜", kanjiSession.item.kanji);
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
    public void recognitionPassesMoveUpAndCeilingHolds() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult fromTyping = scheduler.applyReview(
                item("裂", -1).withToken("t0"),
                new Records.ReviewRequest("裂", "t0", "good", false, false, false, 0),
                consumed,
                1000L
        );
        assertEquals(0, fromTyping.item.recognitionStage);
        assertEquals(0, fromTyping.item.consecutiveFailedRecognitionDays);
        assertFalse(fromTyping.item.writingRemediationPending);

        Records.ReviewResult first = scheduler.applyReview(
                fromTyping.item.withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "good", false, false, false, 0),
                consumed,
                fromTyping.item.dueAtMillis
        );
        assertEquals(1, first.item.recognitionStage);
        assertEquals(0, first.item.consecutiveFailedRecognitionDays);
        assertFalse(first.item.writingRemediationPending);

        Records.ReviewResult second = scheduler.applyReview(
                first.item.withToken("t2"),
                new Records.ReviewRequest("裂", "t2", "good", false, false, false, 0),
                consumed,
                first.item.dueAtMillis
        );
        assertEquals(2, second.item.recognitionStage);

        Records.ReviewResult third = scheduler.applyReview(
                second.item.withToken("t3"),
                new Records.ReviewRequest("裂", "t3", "hard", false, false, false, 0),
                consumed,
                second.item.dueAtMillis
        );
        assertEquals(2, third.item.recognitionStage);
    }

    @Test
    public void recognitionFailsMoveDownToTypingThenWritingRepair() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        long today = localDayStart(System.currentTimeMillis()) + 60_000L;

        Records.ReviewResult fromWord = scheduler.applyReview(
                item("裂", 2).withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "again", false, false, false, 0),
                consumed,
                today
        );
        assertEquals(1, fromWord.item.recognitionStage);

        Records.ReviewResult fromFont = scheduler.applyReview(
                item("裂", 1).withToken("t2"),
                new Records.ReviewRequest("裂", "t2", "again", false, false, false, 0),
                consumed,
                today
        );
        assertEquals(0, fromFont.item.recognitionStage);

        Records.ReviewResult fromKanji = scheduler.applyReview(
                item("裂", 0).withToken("t3"),
                new Records.ReviewRequest("裂", "t3", "again", false, false, false, 0),
                consumed,
                today
        );
        assertEquals(-1, fromKanji.item.recognitionStage);
        assertFalse(fromKanji.item.writingRemediationPending);

        Records.ReviewResult fromTyping = scheduler.applyReview(
                item("裂", -1).withToken("t4"),
                new Records.ReviewRequest("裂", "t4", "again", false, false, false, 0),
                consumed,
                today
        );
        assertEquals(-1, fromTyping.item.recognitionStage);
        assertTrue(fromTyping.item.writingRemediationPending);
    }

    @Test
    public void kanjiMeaningMissMakesNextDueSessionTypingBeforeKanji() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = localDayStart(System.currentTimeMillis()) + 60_000L;

        Records.ReviewResult miss = scheduler.applyReview(
                item("裂", 0).withToken("miss"),
                new Records.ReviewRequest("裂", "miss", "again", false, false, false, 0),
                new HashSet<>(),
                now
        );
        Records.StudySession session = scheduler.nextSession(
                Collections.singletonList(miss.item),
                Collections.singletonList(row("裂", 30)),
                miss.item.dueAtMillis
        );

        assertEquals(-1, miss.item.recognitionStage);
        assertFalse(miss.item.writingRemediationPending);
        assertNotNull(session);
        assertEquals("typing_meaning", session.taskType);
        assertFalse(session.writingRequired);
    }

    @Test
    public void recognitionMissDaysDeduplicateSameDayAndPassResets() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        long today = localDayStart(System.currentTimeMillis()) + 60_000L;
        long tomorrow = moveLocalDays(localDayStart(today), 1) + 60_000L;

        Records.ReviewResult first = scheduler.applyReview(
                item("裂", 2).withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "again", false, false, false, 0),
                consumed,
                today
        );
        Records.ReviewResult sameDay = scheduler.applyReview(
                first.item.withToken("t2"),
                new Records.ReviewRequest("裂", "t2", "again", false, false, false, 0),
                consumed,
                today + 3_600_000L
        );
        assertEquals(1, sameDay.item.consecutiveFailedRecognitionDays);

        Records.ReviewResult nextDay = scheduler.applyReview(
                sameDay.item.withToken("t3"),
                new Records.ReviewRequest("裂", "t3", "again", false, false, false, 0),
                consumed,
                tomorrow
        );
        assertEquals(2, nextDay.item.consecutiveFailedRecognitionDays);
        assertEquals(-1, nextDay.item.recognitionStage);

        Records.ReviewResult pass = scheduler.applyReview(
                nextDay.item.withToken("t4"),
                new Records.ReviewRequest("裂", "t4", "good", false, false, false, 0),
                consumed,
                tomorrow + 3_600_000L
        );
        assertEquals(0, pass.item.recognitionStage);
        assertEquals(0, pass.item.consecutiveFailedRecognitionDays);
        assertEquals(0L, pass.item.lastFailedRecognitionDayMillis);
        assertFalse(pass.item.writingRemediationPending);
    }

    @Test
    public void typingMissMakesNextDueSessionWritingRemediation() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        long today = localDayStart(System.currentTimeMillis()) + 60_000L;

        Records.ReviewResult first = scheduler.applyReview(
                item("裂", 0).withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "again", false, false, false, 0),
                consumed,
                today
        );
        Records.ReviewResult second = scheduler.applyReview(
                first.item.withToken("t2"),
                new Records.ReviewRequest("裂", "t2", "again", false, false, false, 0),
                consumed,
                moveLocalDays(localDayStart(today), 1) + 60_000L
        );

        assertEquals(-1, first.item.recognitionStage);
        assertTrue(second.item.writingRemediationPending);
        Records.StudySession session = scheduler.nextSession(
                Collections.singletonList(second.item),
                Collections.singletonList(row("裂", 30)),
                second.item.dueAtMillis
        );
        assertNotNull(session);
        assertEquals("writing_remediation", session.taskType);
        assertTrue(session.writingRequired);
    }

    @Test
    public void writingRemediationFailPersistsAndPassReturnsToTypingRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        Records.StudyItem pending = new Records.StudyItem("裂", "learning", 0L, 0.8, 5.5, 3, 2, 0, 1, 2, 3, localDayStart(System.currentTimeMillis()), true, "fail", 0L);

        Records.ReviewResult fail = scheduler.applyReview(
                pending,
                new Records.ReviewRequest("裂", "fail", "good", true, false, false, 0),
                consumed,
                1000L
        );
        assertTrue(fail.item.writingRemediationPending);

        Records.ReviewResult pass = scheduler.applyReview(
                fail.item.withToken("pass"),
                new Records.ReviewRequest("裂", "pass", "good", true, true, true, false, 0),
                consumed,
                fail.item.dueAtMillis
        );
        assertFalse(pass.item.writingRemediationPending);
        assertEquals(-1, pass.item.recognitionStage);
        assertEquals(0, pass.item.consecutiveFailedRecognitionDays);
    }

    @Test
    public void manualOverrideClearsWritingRemediationWithWeakRating() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem pending = new Records.StudyItem("裂", "learning", 0L, 0.8, 5.5, 3, 2, 0, 1, 1, 3, localDayStart(System.currentTimeMillis()), true, "override", 0L);

        Records.ReviewResult result = scheduler.applyReview(
                pending,
                new Records.ReviewRequest("裂", "override", "good", true, false, true, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals("hard", result.appliedRating);
        assertFalse(result.item.writingRemediationPending);
        assertEquals(-1, result.item.recognitionStage);
    }

    @Test
    public void matureWordReadingSuppressesLowerContextSiblings() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem word = new Records.StudyItem("裂", "review", 0L, 10.0, 4.0, 3, 0, 2, 2, 2, 0, 0L, false, "word", 0L);

        Records.ReviewResult result = scheduler.applyReview(
                word,
                new Records.ReviewRequest("裂", "word", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals("word_reading", result.item.suppressedByTaskType);
        assertTrue(result.item.matureIntervalDays >= Records.Settings.kikuDefaults().matureDays);
        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(item("裂", 0), item("裂", 1), result.item),
                Collections.singletonList(row("裂", 30)),
                2000L,
                null
        );
        assertEquals(1, active.size());
        assertEquals(2, active.get(0).recognitionStage);
        assertEquals(0, scheduler.dueCount(Arrays.asList(item("裂", 0), item("裂", 1), result.item), Collections.singletonList(row("裂", 30)), 2000L));
        assertNull(scheduler.nextSession(Arrays.asList(item("裂", 0), item("裂", 1), result.item), Collections.singletonList(row("裂", 30)), 2000L));
    }

    @Test
    public void immaturePromotedWordReadingHidesLowerSiblingsWithoutPermanentSuppression() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem font = new Records.StudyItem("裂", "review", 0L, 0.7, 4.0, 2, 0, 1, 1, 1, 0, 0L, false, "font", 0L);

        Records.ReviewResult promoted = scheduler.applyReview(
                font,
                new Records.ReviewRequest("裂", "font", "good", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(2, promoted.item.recognitionStage);
        assertEquals("", promoted.item.suppressedByTaskType);
        assertTrue(promoted.item.matureIntervalDays < Records.Settings.kikuDefaults().matureDays);
        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(item("裂", 0), promoted.item),
                Collections.singletonList(row("裂", 30)),
                2000L,
                null
        );
        assertEquals(1, active.size());
        assertEquals(2, active.get(0).recognitionStage);
        assertEquals(0, scheduler.dueCount(Arrays.asList(item("裂", 0), promoted.item), Collections.singletonList(row("裂", 30)), 2000L));
    }

    @Test
    public void wordReadingMaturityUsesWordTaskMemoryOnly() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem word = new Records.StudyItem(
                "裂",
                "review",
                0L,
                20.0,
                4.0,
                7,
                0,
                2,
                2,
                2,
                0,
                0L,
                false,
                null,
                0L,
                0,
                "裂|裂ける|さける|split",
                "word",
                0L,
                Records.TaskMemory.fromStudyFields("review", 0L, 20.0, 4.0, 4, 0, 2, 31),
                Records.TaskMemory.fromStudyFields("review", 0L, 20.0, 4.0, 3, 0, 2, 31),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial()
        );

        Records.ReviewResult result = scheduler.applyReview(
                word,
                new Records.ReviewRequest("裂", "word", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals("", result.item.suppressedByTaskType);
        assertTrue(result.item.matureIntervalDays < Records.Settings.kikuDefaults().matureDays);
        assertEquals(1, result.item.wordReadingMemory.totalReviews);
        assertEquals(4, result.item.kanjiMeaningMemory.totalReviews);
        assertEquals(3, result.item.fontMeaningMemory.totalReviews);
    }

    @Test
    public void typingMeaningUsesSeparateTaskMemory() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem typing = new Records.StudyItem(
                "裂",
                "learning",
                0L,
                1.2,
                4.8,
                8,
                1,
                1,
                1,
                -1,
                2,
                localDayStart(System.currentTimeMillis()),
                false,
                null,
                0L,
                0,
                "裂|裂ける|さける|split",
                "typing",
                0L,
                Records.TaskMemory.fromStudyFields("learning", 0L, 1.2, 4.8, 2, 1, 1, 0),
                Records.TaskMemory.fromStudyFields("review", 0L, 10.0, 4.0, 5, 0, 2, 31),
                Records.TaskMemory.fromStudyFields("review", 0L, 8.0, 4.0, 4, 0, 2, 21),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial()
        );

        Records.ReviewResult result = scheduler.applyReview(
                typing,
                new Records.ReviewRequest("裂", "typing", "good", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(0, result.item.recognitionStage);
        assertEquals(3, result.item.typingMeaningMemory.totalReviews);
        assertEquals(5, result.item.kanjiMeaningMemory.totalReviews);
        assertEquals(4, result.item.fontMeaningMemory.totalReviews);
        assertEquals(0, result.item.wordReadingMemory.totalReviews);
    }

    @Test
    public void againOnDominatingSiblingClearsSuppression() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem suppressed = item("裂", 2)
                .withSuppression("word_reading", 1000L, 31)
                .withToken("again");

        Records.ReviewResult result = scheduler.applyReview(
                suppressed,
                new Records.ReviewRequest("裂", "again", "again", false, false, false, 0),
                new HashSet<>(),
                2000L
        );

        assertEquals("", result.item.suppressedByTaskType);
        assertEquals(0L, result.item.suppressedAtMillis);
        assertEquals(0, result.item.matureIntervalDays);
        assertEquals(1, result.item.recognitionStage);
    }

    @Test
    public void anchorResetClearsSuppressionForChangedAnswerSignature() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem oldWord = item("裂", 2)
                .withSuppression("word_reading", 1000L, 31)
                .withAnswerSignature("裂|古語|こご|old word");
        Records.DashboardRow changed = rowWithExample("裂", 30, "suspended", "裂ける", "さける", "split");

        List<Records.StudyItem> seeded = scheduler.seedQueue(
                Collections.singletonList(changed),
                Collections.singletonList(oldWord),
                Records.Settings.kikuDefaults(),
                5000L,
                0L
        );

        Records.StudyItem item = findItem(seeded, "裂");
        assertEquals("", item.suppressedByTaskType);
        assertEquals(0L, item.suppressedAtMillis);
        assertEquals(0, item.matureIntervalDays);
        assertEquals(1, item.recognitionStage);
        assertEquals(5000L, item.dueAtMillis);
        assertEquals("裂|裂ける|さける|split", item.answerSignature);
    }

    @Test
    public void matureSiblingSuppressionDoesNotCrossAnswerSignatures() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem matureWord = new Records.StudyItem(
                "裂",
                "review",
                1000L + 31L * 86_400_000L,
                20.0,
                4.0,
                4,
                0,
                2,
                2,
                2,
                0,
                0L,
                false,
                "word_reading",
                1000L,
                31,
                "裂|裂ける|さける|split",
                null,
                0L
        );
        Records.StudyItem differentSignatureLower = item("裂", 0)
                .withAnswerSignature("裂|烈火|れっか|raging fire");
        Records.DashboardRow matureRow = rowWithExample("裂", 30, "suspended", "裂ける", "さける", "split");
        Records.DashboardRow lowerRow = rowWithExample("裂", 30, "active", "烈火", "れっか", "raging fire");
        List<Records.DashboardRow> rows = Arrays.asList(matureRow, lowerRow);

        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(matureWord, differentSignatureLower),
                rows,
                2000L,
                null
        );
        Records.StudySession session = scheduler.nextSession(
                Arrays.asList(matureWord, differentSignatureLower),
                rows,
                2000L
        );

        assertTrue(active.contains(differentSignatureLower));
        assertEquals(1, scheduler.dueCount(Arrays.asList(matureWord, differentSignatureLower), rows, 2000L));
        assertNotNull(session);
        assertEquals("kanji_meaning", session.taskType);
        assertEquals("裂|烈火|れっか|raging fire", session.item.answerSignature);
    }

    @Test
    public void seedQueueKeepsSeparateAnswerSignaturesForSameKanji() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.DashboardRow oldTarget = rowWithExample("裂", 30, "suspended", "裂ける", "さける", "split");
        Records.DashboardRow newTarget = rowWithExample("裂", 30, "active", "破裂", "はれつ", "burst");

        List<Records.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(oldTarget, newTarget),
                Collections.singletonList(new Records.StudyItem("裂", "review", 0L, 2.0, 4.0, 2, 0, 2, 1, null, 0L)
                        .withAnswerSignature("裂|裂ける|さける|split")),
                Records.Settings.kikuDefaults(),
                1000L,
                0L
        );

        assertEquals(2, items.size());
        assertNotNull(findItemBySignature(items, "裂|裂ける|さける|split"));
        assertNotNull(findItemBySignature(items, "裂|破裂|はれつ|burst"));
        List<Records.StudyItem> active = scheduler.activeQueueItems(items, Arrays.asList(oldTarget, newTarget), 1000L, null);
        assertEquals(2, active.size());
    }

    @Test
    public void onlyOneSiblingPerKanjiFamilyCanBeActive() {
        BridgeScheduler scheduler = new BridgeScheduler();
        List<Records.StudyItem> siblings = Arrays.asList(item("裂", 0), item("裂", 1), item("裂", 2));

        List<Records.StudyItem> active = scheduler.activeQueueItems(siblings, Collections.singletonList(row("裂", 30)), 1000L, null);
        Records.StudySession session = scheduler.nextSession(siblings, Collections.singletonList(row("裂", 30)), 1000L);

        assertEquals(1, active.size());
        assertEquals(2, active.get(0).recognitionStage);
        assertNotNull(session);
        assertEquals("word_reading", session.taskType);
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

    @Test
    public void allowedKanjiFilterExcludesSuspendedKanjiFromActiveReview() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem suspended = new Records.StudyItem("裂", "review", 0, 1.0, 5.0, 1, 0, 2, 1, null, 0);
        Records.StudyItem active = new Records.StudyItem("提", "review", 0, 1.0, 5.0, 1, 0, 2, 1, null, 0);
        Set<String> allowed = new HashSet<>(Collections.singletonList("提"));

        List<Records.StudyItem> activeItems = scheduler.activeQueueItems(
                Arrays.asList(suspended, active),
                Arrays.asList(row("裂", 30), row("提", 20)),
                1000L,
                allowed
        );
        Records.StudySession session = scheduler.nextSession(
                Arrays.asList(suspended, active),
                Arrays.asList(row("裂", 30), row("提", 20)),
                1000L,
                allowed
        );

        assertEquals(1, activeItems.size());
        assertEquals("提", activeItems.get(0).kanji);
        assertNotNull(session);
        assertEquals("提", session.item.kanji);
    }

    @Test
    public void nullAdaptivePlanUsesDefaultSeedingPath() {
        BridgeScheduler scheduler = new BridgeScheduler();

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.emptyList(),
                Records.Settings.kikuDefaults(),
                1000L,
                0L,
                null
        );

        assertEquals(1, items.size());
        assertEquals("裂", items.get(0).kanji);
    }

    @Test
    public void seedQueueAlignsLegacyEmptySignatureToSuspendedExample() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem legacy = new Records.StudyItem("裂", "review", 1234L, 2.0, 4.0, 2, 0, 2, 1, null, 55L)
                .withAnswerSignature("");
        Records.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new Records.Example("active", 1L, 1L, "破裂", "はれつ", "burst", "", false, 0),
                new Records.Example("suspended", 2L, 2L, "裂ける", "さける", "split", "", true, 0)
        );

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(legacy),
                Records.Settings.kikuDefaults(),
                5000L,
                0L
        );

        Records.StudyItem aligned = findItem(items, "裂");
        assertEquals("review", aligned.state);
        assertEquals(1234L, aligned.dueAtMillis);
        assertEquals("裂|裂ける|さける|split", aligned.answerSignature);
    }

    @Test
    public void changedAnswerSignatureKeepsCoveredRetiredItemRetired() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem retired = item("裂", 2)
                .withAnswerSignature("裂|古語|こご|old word");
        retired = new Records.StudyItem(
                retired.kanji,
                "retired",
                7777L,
                3.2,
                4.4,
                6,
                1,
                2,
                2,
                2,
                0,
                0L,
                false,
                null,
                0L,
                31,
                retired.answerSignature,
                "old-token",
                99L
        );
        ArrayList<Records.Example> examples = new ArrayList<>();
        examples.add(new Records.Example("suspended", 1L, 1L, "裂ける", "さける", "split", "", false, 0));
        Records.DashboardRow covered = new Records.DashboardRow("裂", 900, "split", "さける", "search", 30, "reason", "reason text", 2, 0, 2, examples);

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(covered),
                Collections.singletonList(retired),
                Records.Settings.kikuDefaults(),
                5000L,
                0L
        );

        Records.StudyItem aligned = findItem(items, "裂");
        assertEquals("retired", aligned.state);
        assertEquals(7777L, aligned.dueAtMillis);
        assertEquals(6, aligned.totalReviews);
        assertEquals(1, aligned.recognitionStage);
        assertEquals("裂|裂ける|さける|split", aligned.answerSignature);
        assertNull(aligned.activeToken);
    }

    @Test
    public void nextSessionReturnsNullWhenNothingDueOrAllowed() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem future = new Records.StudyItem("裂", "review", 2000L, 1.0, 5.0, 1, 0, 2, 1, null, 0);

        assertNull(scheduler.nextSession(Collections.singletonList(future), Collections.singletonList(row("裂", 30)), 1000L));
        assertNull(scheduler.nextSession(
                Collections.singletonList(item("裂")),
                Collections.singletonList(row("裂", 30)),
                1000L,
                Collections.singleton("提")
        ));
    }

    @Test
    public void tokenMismatchAndNullReviewInputsStaySafe() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem active = item("裂").withToken("expected");

        Records.ReviewResult duplicate = scheduler.applyReview(
                active,
                new Records.ReviewRequest("裂", "actual", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L,
                null,
                null
        );
        Records.ReviewResult normalized = scheduler.applyReview(
                item("提"),
                new Records.ReviewRequest("提", "token", null, false, false, false, 0),
                new HashSet<>(),
                1000L,
                null,
                null
        );

        assertTrue(duplicate.duplicate);
        assertEquals("again", normalized.appliedRating);
        assertEquals("learning", normalized.item.state);
    }

    @Test
    public void invalidRatingDefaultsToAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();

        Records.ReviewResult result = scheduler.applyReview(
                item("裂").withToken("bad-rating"),
                new Records.ReviewRequest("裂", "bad-rating", "perfect", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals("again", result.appliedRating);
        assertEquals("learning", result.item.state);
        assertEquals(1, result.item.lapses);
    }

    @Test
    public void activeQueueFiltersRetiredMissingAndSuppressedSiblings() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem retired = new Records.StudyItem("古", "retired", 0, 1.0, 5.0, 1, 0, 2, 1, null, 0);
        Records.StudyItem missing = item("消");
        Records.StudyItem matureWord = item("裂", 2).withSuppression(BridgeScheduler.TASK_WORD_READING, 1000L, 31);
        Records.StudyItem lowerSibling = item("裂", 0);

        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(retired, missing, matureWord, lowerSibling),
                Collections.singletonList(row("裂", 30)),
                1000L,
                null
        );

        assertEquals(1, active.size());
        assertEquals(2, active.get(0).recognitionStage);
    }

    @Test
    public void activeFamilyItemPrefersDueStatusThenEarlierDueTimeWithinRank() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem due = new Records.StudyItem("裂", "review", 1000L, 1.0, 5.0, 1, 0, 2, 1, "due", 0);
        Records.StudyItem future = new Records.StudyItem("裂", "review", 5000L, 1.0, 5.0, 1, 0, 2, 1, "future", 0);
        Records.StudyItem earlierFuture = new Records.StudyItem("謎", "review", 3000L, 1.0, 5.0, 1, 0, 2, 1, "early", 0);
        Records.StudyItem laterFuture = new Records.StudyItem("謎", "review", 5000L, 1.0, 5.0, 1, 0, 2, 1, "late", 0);

        List<Records.StudyItem> activeDue = scheduler.activeQueueItems(
                Arrays.asList(future, due),
                Collections.singletonList(row("裂", 30)),
                2000L,
                null
        );
        List<Records.StudyItem> activeFuture = scheduler.activeQueueItems(
                Arrays.asList(laterFuture, earlierFuture),
                Collections.singletonList(row("謎", 30)),
                1000L,
                null
        );

        assertEquals(1, activeDue.size());
        assertEquals("due", activeDue.get(0).activeToken);
        assertEquals(1, activeFuture.size());
        assertEquals("early", activeFuture.get(0).activeToken);
    }

    @Test
    public void dueCountAndTokenSetCoverCollectionHelpers() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> tokens = scheduler.tokenSet(Arrays.asList("a", "b", "a"));

        assertEquals(2, tokens.size());
        assertEquals(1, scheduler.dueCount(
                Arrays.asList(
                        new Records.StudyItem("裂", "review", 0, 1.0, 5.0, 1, 0, 2, 1, null, 0),
                        new Records.StudyItem("提", "retired", 0, 1.0, 5.0, 1, 0, 2, 1, null, 0),
                        new Records.StudyItem("謎", "review", 2000L, 1.0, 5.0, 1, 0, 2, 1, null, 0)
                ),
                1000L
        ));
    }

    private Records.StudyItem item(String kanji) {
        return item(kanji, 0);
    }

    private Records.StudyItem item(String kanji, int recognitionStage) {
        return new Records.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, recognitionStage, 0, 0L, false, null, 0);
    }

    private static long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long moveLocalDays(long localDayStart, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localDayStart);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
    }

    private Records.StudyItem findItem(List<Records.StudyItem> items, String kanji) {
        for (Records.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + kanji);
    }

    private Records.StudyItem findItemBySignature(List<Records.StudyItem> items, String answerSignature) {
        for (Records.StudyItem item : items) {
            if (item.answerSignature.equals(answerSignature)) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + answerSignature);
    }

    private Records.DashboardRow row(String kanji, int score) {
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>());
    }

    private Records.DashboardRow rowWithExample(String kanji, int score, String sourceType, String expression, String reading, String meaning) {
        ArrayList<Records.Example> examples = new ArrayList<>();
        examples.add(new Records.Example(sourceType, 1L, 1L, expression, reading, meaning, "", false, 0));
        return new Records.DashboardRow(kanji, 900, meaning, reading, "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, examples);
    }

    private Records.DashboardRow rowWithExamples(String kanji, int score, Records.Example... examples) {
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>(Arrays.asList(examples)));
    }
}
