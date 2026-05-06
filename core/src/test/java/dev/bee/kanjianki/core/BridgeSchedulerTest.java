package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
        Records.StudySession simple = scheduler.nextSession(Arrays.asList(item("裂", 0)), Arrays.asList(row("裂", 10)), 1000L);
        Records.StudySession font = scheduler.nextSession(Arrays.asList(item("謎", 1)), Arrays.asList(row("謎", 10)), 1000L);
        Records.StudySession word = scheduler.nextSession(Arrays.asList(item("示", 2)), Arrays.asList(row("示", 10)), 1000L);

        assertNotNull(simple);
        assertNotNull(font);
        assertNotNull(word);
        assertFalse(simple.writingRequired);
        assertFalse(font.writingRequired);
        assertFalse(word.writingRequired);
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

        Records.ReviewResult first = scheduler.applyReview(
                item("裂", 0).withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "good", false, false, false, 0),
                consumed,
                1000L
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
    public void recognitionFailsMoveDownAndFloorHolds() {
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

        Records.ReviewResult fromFloor = scheduler.applyReview(
                item("裂", 0).withToken("t3"),
                new Records.ReviewRequest("裂", "t3", "again", false, false, false, 0),
                consumed,
                today
        );
        assertEquals(0, fromFloor.item.recognitionStage);
    }

    @Test
    public void recognitionMissDaysDeduplicateSameDayAndPassResets() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        long today = localDayStart(System.currentTimeMillis()) + 60_000L;
        long tomorrow = moveLocalDays(localDayStart(today), 1) + 60_000L;

        Records.ReviewResult first = scheduler.applyReview(
                item("裂", 1).withToken("t1"),
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

        Records.ReviewResult pass = scheduler.applyReview(
                nextDay.item.withToken("t4"),
                new Records.ReviewRequest("裂", "t4", "good", false, false, false, 0),
                consumed,
                tomorrow + 3_600_000L
        );
        assertEquals(0, pass.item.consecutiveFailedRecognitionDays);
        assertEquals(0L, pass.item.lastFailedRecognitionDayMillis);
        assertFalse(pass.item.writingRemediationPending);
    }

    @Test
    public void recognitionMissThresholdMakesNextDueSessionWritingRemediation() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        long today = localDayStart(System.currentTimeMillis()) + 60_000L;
        Records.Settings thresholdTwo = settingsWithWritingThreshold(2);

        Records.ReviewResult first = scheduler.applyReview(
                item("裂", 0).withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "again", false, false, false, 0),
                consumed,
                today,
                Records.SchedulerParameters.defaults(),
                thresholdTwo
        );
        Records.ReviewResult second = scheduler.applyReview(
                first.item.withToken("t2"),
                new Records.ReviewRequest("裂", "t2", "again", false, false, false, 0),
                consumed,
                moveLocalDays(localDayStart(today), 1) + 60_000L,
                Records.SchedulerParameters.defaults(),
                thresholdTwo
        );

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
    public void writingRemediationFailPersistsAndPassRestartsLadder() {
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
        assertEquals(0, pass.item.recognitionStage);
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
        assertEquals(0, result.item.recognitionStage);
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

    private Records.StudyItem item(String kanji) {
        return item(kanji, 0);
    }

    private Records.StudyItem item(String kanji, int recognitionStage) {
        return new Records.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, recognitionStage, 0, 0L, false, null, 0);
    }

    private Records.Settings settingsWithWritingThreshold(int days) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        return new Records.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankCutoff,
                defaults.activeQueueCap,
                defaults.newPerDay,
                days
        );
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

    private Records.DashboardRow row(String kanji, int score) {
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>());
    }

    private Records.DashboardRow rowWithExample(String kanji, int score, String sourceType, String expression, String reading, String meaning) {
        ArrayList<Records.Example> examples = new ArrayList<>();
        examples.add(new Records.Example(sourceType, 1L, 1L, expression, reading, meaning, "", false, 0));
        return new Records.DashboardRow(kanji, 900, meaning, reading, "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, examples);
    }
}
