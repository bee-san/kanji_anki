package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Scheduler tests that do not depend on the ladder state machine directly:
 * queue seeding, admission caps, token handling, adaptive focus, and retired
 * reconciliation. Ladder / rung / phase transition tests live in
 * {@link LadderSchedulerTest}.
 */
public class BridgeSchedulerTest {
    @Test
    public void seedsQueueWithDailyNewAndActiveCaps() {
        Records.Settings settings = new Records.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                2,
                3000,
                2,
                1
        );
        List<Records.DashboardRow> rows = Arrays.asList(row("裂", 20), row("謎", 19), row("示", 18));

        List<Records.StudyItem> items = new BridgeScheduler().seedQueue(rows, Collections.emptyList(), settings, 1000L, 0L);

        assertEquals(1, items.size());
        assertEquals("裂", items.get(0).kanji);
        assertEquals(Records.LadderRung.KANJI_MEANING, items.get(0).rung);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, items.get(0).phase);
    }

    @Test
    public void writingFailureOnWriteKanjiRungMapsToAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
                .withRungAndPhase(Records.LadderRung.WRITE_KANJI, Records.SchedulerPhase.REVIEW);
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", true, false, false, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("again", result.appliedRating);
    }

    @Test
    public void manualOverrideAllowsWritingRatingOnWriteKanjiRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
                .withRungAndPhase(Records.LadderRung.WRITE_KANJI, Records.SchedulerPhase.REVIEW);
        Records.ReviewRequest request = new Records.ReviewRequest("裂", "token-1", "good", true, false, true, 0);

        Records.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        // Manual override on the write rung is mapped to Hard by the scheduler
        // so the learner keeps progress but does not auto-pass.
        assertEquals("hard", result.appliedRating);
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
        Records.StudyItem template = new Records.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 1, "clean", 0)
                .withRungAndPhase(Records.LadderRung.WRITE_KANJI, Records.SchedulerPhase.REVIEW);

        Records.ReviewResult clean = scheduler.applyReview(
                template,
                new Records.ReviewRequest("裂", "clean", "hard", true, true, true, false, 0),
                new HashSet<>(),
                1000L
        );
        Records.ReviewResult hinted = scheduler.applyReview(
                template.withToken("hinted"),
                new Records.ReviewRequest("裂", "hinted", "good", true, true, true, false, 1),
                new HashSet<>(),
                1000L
        );
        Records.ReviewResult messy = scheduler.applyReview(
                template.withToken("messy"),
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
    public void nextSessionRotatesTaskShapeForEachRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudySession typing = scheduler.nextSession(
                Collections.singletonList(itemAtRung("拉", Records.LadderRung.TYPE_MEANING)),
                Collections.singletonList(row("拉", 10)),
                1000L
        );
        Records.StudySession kanji = scheduler.nextSession(
                Collections.singletonList(itemAtRung("裂", Records.LadderRung.KANJI_MEANING)),
                Collections.singletonList(row("裂", 10)),
                1000L
        );
        Records.StudySession font = scheduler.nextSession(
                Collections.singletonList(itemAtRung("謎", Records.LadderRung.FONT_MEANING)),
                Collections.singletonList(row("謎", 10)),
                1000L
        );
        Records.StudySession word = scheduler.nextSession(
                Collections.singletonList(itemAtRung("示", Records.LadderRung.WORD_READING)),
                Collections.singletonList(row("示", 10)),
                1000L
        );

        assertNotNull(typing);
        assertNotNull(kanji);
        assertNotNull(font);
        assertNotNull(word);
        assertEquals("type_meaning", typing.taskType);
        assertEquals("kanji_meaning", kanji.taskType);
        assertEquals("font_meaning", font.taskType);
        assertEquals("word_reading", word.taskType);
        assertFalse(typing.writingRequired);
        assertFalse(kanji.writingRequired);
        assertFalse(font.writingRequired);
        assertFalse(word.writingRequired);
    }

    @Test
    public void writeKanjiRungRoutesToWritingTask() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem pending = itemAtRung("裂", Records.LadderRung.WRITE_KANJI)
                .copyBuilder()
                .writingRemediationPending(true)
                .phase(Records.SchedulerPhase.RELEARNING)
                .build();

        Records.StudySession session = scheduler.nextSession(
                Collections.singletonList(pending),
                Collections.singletonList(row("裂", 10)),
                1000L
        );

        assertNotNull(session);
        assertTrue(session.writingRequired);
        assertEquals("write_kanji", session.taskType);
    }

    @Test
    public void nextSessionPrioritizesDueReviewsBeforeNewCards() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem newProblem = itemAtRung("裂", Records.LadderRung.KANJI_MEANING);
        Records.StudyItem dueReview = itemAtRung("謎", Records.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("review")
                .dueAtMillis(500L)
                .stability(1.8)
                .difficulty(4.8)
                .totalReviews(2)
                .learningStep(2)
                .phase(Records.SchedulerPhase.REVIEW)
                .build();

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
    public void nextSessionTreatsRelearningAndWriteRungAsUrgentBeforeReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem review = itemAtRung("謎", Records.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("review")
                .stability(2.0)
                .difficulty(4.0)
                .totalReviews(4)
                .learningStep(2)
                .phase(Records.SchedulerPhase.REVIEW)
                .build();
        Records.StudyItem relearning = itemAtRung("習", Records.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .dueAtMillis(500L)
                .stability(0.9)
                .difficulty(5.5)
                .totalReviews(2)
                .lapses(1)
                .learningStep(1)
                .phase(Records.SchedulerPhase.RELEARNING)
                .build();
        Records.StudyItem writeRung = itemAtRung("裂", Records.LadderRung.WRITE_KANJI)
                .copyBuilder()
                .state("review")
                .stability(1.0)
                .difficulty(5.0)
                .totalReviews(5)
                .lapses(2)
                .learningStep(2)
                .writingLevel(1)
                .writingRemediationPending(true)
                .phase(Records.SchedulerPhase.REVIEW)
                .build();

        Records.StudySession writeSession = scheduler.nextSession(
                Arrays.asList(review, relearning, writeRung),
                Arrays.asList(row("謎", 100), row("習", 10), row("裂", 1)),
                1000L
        );
        Records.StudySession relearningSession = scheduler.nextSession(
                Arrays.asList(review, relearning),
                Arrays.asList(row("謎", 100), row("習", 10)),
                1000L
        );

        assertNotNull(writeSession);
        assertEquals("裂", writeSession.item.kanji);
        assertEquals("write_kanji", writeSession.taskType);
        assertNotNull(relearningSession);
        assertEquals("習", relearningSession.item.kanji);
        assertEquals("kanji_meaning", relearningSession.taskType);
    }

    @Test
    public void nextSessionUsesWeaknessAndKanjiTieBreakersForSamePriorityDueItems() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem lowerWeakness = reviewItem("謎", Records.LadderRung.KANJI_MEANING, 0L);
        Records.StudyItem higherWeakness = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L);
        Records.StudyItem firstKanji = reviewItem("亜", Records.LadderRung.KANJI_MEANING, 0L);
        Records.StudyItem laterKanji = reviewItem("唖", Records.LadderRung.KANJI_MEANING, 0L);

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
        Records.Settings settings = new Records.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                1,
                3000,
                1,
                2
        );
        Records.StudyItem stale = item("古");

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
        Records.StudyItem reviewed = reviewItem("裂", Records.LadderRung.FONT_MEANING, 0L)
                .copyBuilder()
                .totalReviews(3)
                .stability(1.5)
                .build();
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
    public void seedQueueKeepsUnreviewedItemsEvenWithEnoughMatureSupport() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem unreviewed = item("裂");
        Records.DashboardRow covered = new Records.DashboardRow("裂", 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2, new ArrayList<>());

        List<Records.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(covered),
                Collections.singletonList(unreviewed),
                Records.Settings.kikuDefaults(),
                1000L,
                0L
        );

        assertEquals("new", findItem(items, "裂").state);
    }

    @Test
    public void seedQueueReopensRetiredItemsWhenWeakEvidenceReturns() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem retired = item("裂").copyBuilder()
                .state("retired")
                .stability(1.5)
                .difficulty(4.0)
                .totalReviews(3)
                .learningStep(2)
                .writingLevel(2)
                .rung(Records.LadderRung.WORD_READING)
                .phase(Records.SchedulerPhase.REVIEW)
                .build();

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
        assertEquals(Records.LadderRung.KANJI_MEANING, reopened.rung);
    }

    @Test
    public void retiredItemsStayRetiredWhenAdmissionRoomIsFull() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem active = item("謎").copyBuilder()
                .createdAtMillis(0L)
                .build();
        Records.StudyItem retired = item("裂").copyBuilder()
                .state("retired")
                .totalReviews(3)
                .build();

        List<Records.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(row("謎", 20), row("裂", 30)),
                Arrays.asList(active, retired),
                settingsWithQueue(1, 3),
                1000L,
                500L
        );

        assertEquals("retired", findItem(items, "裂").state);
    }

    @Test
    public void seedExtraNewCardsAddsRequestedCardsBeyondDailyNewCap() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem active = item("裂").copyBuilder()
                .createdAtMillis(1000L)
                .build();

        BridgeScheduler.ExtraNewCardsResult result = scheduler.seedExtraNewCards(
                Arrays.asList(row("裂", 50), row("謎", 40), row("示", 30), row("浸", 20)),
                Collections.singletonList(active),
                settingsWithQueue(4, 1),
                2000L,
                0L,
                2
        );

        assertEquals(2, result.admittedCount);
        assertEquals(3, result.availableCount);
        assertEquals(Arrays.asList("謎", "示"), result.admittedKanji);
        assertEquals("new", findItem(result.items, "謎").state);
        assertEquals("new", findItem(result.items, "示").state);
        assertEquals("new", findItem(result.items, "裂").state);
        assertFalse(result.admittedKanji.contains("裂"));
    }

    @Test
    public void seedExtraNewCardsClampsToRemainingCandidatesAndReopensRetiredItems() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem active = item("謎");
        Records.StudyItem retired = item("裂").copyBuilder()
                .state("retired")
                .totalReviews(3)
                .build();

        BridgeScheduler.ExtraNewCardsResult result = scheduler.seedExtraNewCards(
                Arrays.asList(row("裂", 50), row("謎", 40), row("示", 30)),
                Arrays.asList(active, retired),
                settingsWithQueue(2, 1),
                2000L,
                0L,
                5
        );

        assertEquals(2, result.availableCount);
        assertEquals(2, result.admittedCount);
        assertEquals(Arrays.asList("裂", "示"), result.admittedKanji);
        assertEquals("new", findItem(result.items, "裂").state);
        assertNull(findItem(result.items, "裂").activeToken);
        assertEquals("new", findItem(result.items, "示").state);
        assertEquals("new", findItem(result.items, "謎").state);
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
    public void adaptiveFocusIgnoresMissingRowsAndRetiresAmbiguousLegacyFamily() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.AdaptiveLoadPlan plan = new Records.AdaptiveLoadPlan(
                20,
                2,
                2,
                Arrays.asList("古", "裂"),
                2,
                false,
                "focus"
        );
        Records.DashboardRow firstFamily = rowWithExamples(
                "裂",
                30,
                new Records.Example("active", 1L, 1L, "古い", "ふるい", "old", "", false, 0)
        );
        Records.DashboardRow secondFamily = rowWithExamples(
                "裂",
                25,
                new Records.Example("active", 2L, 2L, "新しい", "あたらしい", "new", "", false, 0)
        );
        Records.StudyItem ambiguousLegacy = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|stale|stale|stale");

        List<Records.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(firstFamily, secondFamily),
                Collections.singletonList(ambiguousLegacy),
                settingsWithQueue(10, 2),
                1000L,
                0L,
                plan
        );

        assertEquals("retired", ambiguousLegacyState(items));
        assertEquals(2, items.size());
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
                new Records.Settings(
                        "Kiku",
                        "Mining",
                        "Expression",
                        "ExpressionReading",
                        "MainDefinition",
                        "Sentence",
                        "Frequency",
                        "FreqSort",
                        21,
                        2,
                        3000,
                        1,
                        1
                ),
                1000L,
                0L,
                plan
        );

        assertEquals(3, items.size());
    }

    @Test
    public void nextSessionSkipsItemsWithoutCurrentDashboardRows() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem stale = item("古");
        Records.StudyItem current = item("裂").copyBuilder().dueAtMillis(500L).build();

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
        Records.StudyItem learned = item("裂").copyBuilder()
                .state("learning")
                .dueAtMillis(1234L)
                .stability(1.2)
                .difficulty(4.4)
                .totalReviews(2)
                .lapses(1)
                .learningStep(1)
                .writingLevel(2)
                .activeToken("active")
                .createdAtMillis(55L)
                .phase(Records.SchedulerPhase.RELEARNING)
                .build();

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
        assertEquals(Records.SchedulerPhase.RELEARNING, item.phase);
    }

    @Test
    public void customParametersAffectReviewInterval() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .stability(3.0)
                .difficulty(5.0)
                .totalReviews(3)
                .learningStep(2)
                .build()
                .withToken("token-1");
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
        Records.StudyItem suspended = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L);
        Records.StudyItem active = reviewItem("提", Records.LadderRung.KANJI_MEANING, 0L);
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
    public void seedQueueKeepsMatchingAnswerSignatureProgress() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem current = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("裂|裂ける|さける|split");
        Records.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new Records.Example("suspended", 2L, 2L, "裂ける", "さける", "split", "", true, 0)
        );

        Records.StudyItem aligned = findItem(scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(current),
                Records.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂");

        assertEquals("review", aligned.state);
        assertEquals(1234L, aligned.dueAtMillis);
    }

    @Test
    public void seedQueueUsesFirstExampleFallbackAndNormalizesSignatureParts() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem legacy = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("");
        Records.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new Records.Example("other", 1L, 1L, "  split   apart  ", null, " main   meaning ", "", false, 0)
        );

        Records.StudyItem aligned = findItem(scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(legacy),
                Records.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂");

        assertEquals("裂|split apart||main meaning", aligned.answerSignature);
    }

    @Test
    public void seedQueueKeepsFirstActiveExampleWhenNoSuspendedExampleExists() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem legacy = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("");
        Records.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new Records.Example("active", 1L, 1L, "first", "one", "meaning one", "", false, 0),
                new Records.Example("active", 2L, 2L, "second", "two", "meaning two", "", false, 0)
        );

        Records.StudyItem aligned = findItem(scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(legacy),
                Records.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂");

        assertEquals("裂|first|one|meaning one", aligned.answerSignature);
    }

    @Test
    public void reseedResetsNonRetiredItemWhenAnswerSignatureChanges() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem learned = reviewItem("裂", Records.LadderRung.FONT_MEANING, 5000L)
                .copyBuilder()
                .answerSignature("裂|old|old|old")
                .activeToken("active")
                .realPassStreak(2)
                .realAgainStreak(1)
                .lastRealReviewDueAtMillis(123L)
                .build();
        Records.DashboardRow changed = rowWithExamples(
                "裂",
                30,
                new Records.Example("active", 1L, 1L, "新しい", "あたらしい", "new", "", false, 0)
        );

        Records.StudyItem reset = findItem(scheduler.seedQueue(
                Collections.singletonList(changed),
                Collections.singletonList(learned),
                Records.Settings.kikuDefaults(),
                2000L,
                0L
        ), "裂");

        assertEquals("learning", reset.state);
        assertEquals(2000L, reset.dueAtMillis);
        assertEquals(0, reset.totalReviews);
        assertNull(reset.activeToken);
        assertEquals("裂|新しい|あたらしい|new", reset.answerSignature);
        assertEquals(Records.LadderRung.KANJI_MEANING, reset.rung);
        assertEquals(0, reset.realPassStreak);
        assertEquals(0, reset.lastRealReviewDueAtMillis);
    }

    @Test
    public void reseedRetiredItemOnlyUpdatesChangedAnswerSignature() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem retired = reviewItem("裂", Records.LadderRung.WORD_READING, 5000L)
                .copyBuilder()
                .state("retired")
                .answerSignature("裂|old|old|old")
                .build();
        Records.DashboardRow changed = rowWithExamples(
                "裂",
                30,
                new Records.Example("active", 1L, 1L, "新しい", "あたらしい", "new", "", false, 0)
        );

        Records.StudyItem updated = findItem(scheduler.seedQueue(
                Collections.singletonList(changed),
                Collections.singletonList(retired),
                Records.Settings.kikuDefaults(),
                2000L,
                0L
        ), "裂");

        assertEquals("new", updated.state);
        assertEquals("裂|新しい|あたらしい|new", updated.answerSignature);
        assertEquals(0, updated.totalReviews);
    }

    @Test
    public void activeQueueGroupsByFamilyAndPrefersHigherRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem kanji = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L).withAnswerSignature("裂|expr|read|meaning");
        Records.StudyItem word = reviewItem("裂", Records.LadderRung.WORD_READING, 5000L).withAnswerSignature("裂|expr|read|meaning");
        Records.StudyItem legacyEmptySignature = reviewItem("謎", Records.LadderRung.KANJI_MEANING, 0L).withAnswerSignature("");

        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(kanji, word, legacyEmptySignature),
                Arrays.asList(rowWithExamples("裂", 30, new Records.Example("active", 1L, 1L, "expr", "read", "meaning", "", false, 0)), row("謎", 20)),
                1000L,
                null
        );

        assertEquals(2, active.size());
        assertEquals("謎", findItem(active, "謎").kanji);
        assertEquals(Records.LadderRung.WORD_READING, findItem(active, "裂").rung);
    }

    @Test
    public void activeQueueRejectsChangedNonEmptyAnswerSignature() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem staleFamily = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|old|read|meaning");
        Records.DashboardRow currentRow = rowWithExamples(
                "裂",
                30,
                new Records.Example("active", 1L, 1L, "new", "read", "meaning", "", false, 0)
        );

        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Collections.singletonList(staleFamily),
                Collections.singletonList(currentRow),
                1000L,
                null
        );

        assertTrue(active.isEmpty());
    }

    @Test
    public void nextSessionReusesActiveTokenAndGeneratesForEmptyToken() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudySession reused = scheduler.nextSession(
                Collections.singletonList(reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L).withToken("kept")),
                Collections.singletonList(row("裂", 30)),
                1000L
        );
        Records.StudySession generated = scheduler.nextSession(
                Collections.singletonList(reviewItem("謎", Records.LadderRung.KANJI_MEANING, 0L).withToken("")),
                Collections.singletonList(row("謎", 30)),
                1000L
        );

        assertEquals("kept", reused.token);
        assertTrue(generated.token.startsWith("謎-"));
    }

    @Test
    public void nextSessionReturnsNullWhenNothingDueOrAllowed() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem future = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 2000L);

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

        Records.ReviewResult emptyToken = scheduler.applyReview(
                item("空").withToken(""),
                new Records.ReviewRequest("空", "token", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L
        );
        assertFalse(emptyToken.duplicate);
    }

    @Test
    public void invalidRatingDefaultsToAgainAndResetsLearningStep() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = item("裂").copyBuilder().learningStep(1).build();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("bad-rating"),
                new Records.ReviewRequest("裂", "bad-rating", "perfect", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals("again", result.appliedRating);
        assertEquals("learning", result.item.state);
        assertEquals(0, result.item.learningStep);
    }

    @Test
    public void relearningGoodCanAdvanceWithoutGraduating() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem relearning = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .state("learning")
                .phase(Records.SchedulerPhase.RELEARNING)
                .learningStep(0)
                .activeToken("relearn")
                .build();
        Records.LearningStepSettings steps = new Records.LearningStepSettings(
                Records.LearningStepSettings.defaultNewSteps(),
                Arrays.asList(5, 20)
        );

        Records.ReviewResult result = scheduler.applyReview(
                relearning,
                new Records.ReviewRequest("裂", "relearn", "good", false, false, false, 0),
                new HashSet<>(),
                1000L,
                Records.SchedulerParameters.defaults(),
                Records.Settings.kikuDefaults(),
                steps
        );

        assertEquals(Records.SchedulerPhase.RELEARNING, result.item.phase);
        assertEquals(1, result.item.learningStep);
    }

    @Test
    public void learningHardRepeatsLaterStep() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem learning = item("裂").copyBuilder()
                .state("learning")
                .phase(Records.SchedulerPhase.NEW_LEARNING)
                .learningStep(1)
                .activeToken("hard")
                .build()
                .withTaskMemory(
                        BridgeScheduler.TASK_KANJI_MEANING,
                        new Records.TaskMemory("learning", 0L, 0.4, 5.0, 1, 0, 1, "", 0)
                );

        Records.ReviewResult result = scheduler.applyReview(
                learning,
                new Records.ReviewRequest("裂", "hard", "hard", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(1, result.item.learningStep);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, result.item.phase);
    }

    @Test
    public void learningHardOnSingleStepUsesAgainDelay() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem learning = item("裂").copyBuilder()
                .state("learning")
                .phase(Records.SchedulerPhase.NEW_LEARNING)
                .activeToken("hard")
                .build();
        Records.LearningStepSettings steps = new Records.LearningStepSettings(
                Collections.singletonList(5),
                Records.LearningStepSettings.defaultReviewSteps()
        );

        Records.ReviewResult result = scheduler.applyReview(
                learning,
                new Records.ReviewRequest("裂", "hard", "hard", false, false, false, 0),
                new HashSet<>(),
                1000L,
                Records.SchedulerParameters.defaults(),
                Records.Settings.kikuDefaults(),
                steps
        );

        assertEquals(0, result.item.learningStep);
        assertEquals(1000L + 5 * 60_000L, result.item.dueAtMillis);
    }

    @Test
    public void futureReviewAgainDoesNotCountAsRealDueFailure() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem future = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 5000L)
                .withToken("future");

        Records.ReviewResult result = scheduler.applyReview(
                future,
                new Records.ReviewRequest("裂", "future", "again", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(0, result.item.realAgainStreak);
        assertEquals(0L, result.item.lastRealReviewDueAtMillis);
    }

    @Test
    public void practicedLearningCardsSortBeforeDueReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem practicedLearning = item("学").copyBuilder()
                .state("learning")
                .dueAtMillis(0L)
                .totalReviews(1)
                .phase(Records.SchedulerPhase.NEW_LEARNING)
                .build();
        Records.StudyItem review = reviewItem("復", Records.LadderRung.KANJI_MEANING, 0L);

        Records.StudySession session = scheduler.nextSession(
                Arrays.asList(review, practicedLearning),
                Arrays.asList(row("復", 100), row("学", 1)),
                1000L
        );

        assertNotNull(session);
        assertEquals("学", session.item.kanji);
    }

    @Test
    public void activeQueueFiltersRetiredAndMissingRows() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem retired = item("古").copyBuilder().state("retired").build();
        Records.StudyItem missing = item("消");
        Records.StudyItem fontRung = itemAtRung("裂", Records.LadderRung.FONT_MEANING);

        List<Records.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(retired, missing, fontRung),
                Collections.singletonList(row("裂", 30)),
                1000L,
                null
        );

        assertEquals(1, active.size());
        assertEquals("裂", active.get(0).kanji);
    }

    @Test
    public void activeFamilyItemPrefersDueStatusThenEarlierDueTimeWithinRank() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem due = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 1000L).withToken("due");
        Records.StudyItem future = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 5000L).withToken("future");
        Records.StudyItem earlierFuture = reviewItem("謎", Records.LadderRung.KANJI_MEANING, 3000L).withToken("early");
        Records.StudyItem laterFuture = reviewItem("謎", Records.LadderRung.KANJI_MEANING, 5000L).withToken("late");

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

        List<Records.StudyItem> alreadyBest = scheduler.activeQueueItems(
                Arrays.asList(due, future),
                Collections.singletonList(row("裂", 30)),
                2000L,
                null
        );
        assertEquals("due", alreadyBest.get(0).activeToken);
    }

    @Test
    public void rungsForItemSkipsSimilarOnlyWhenUnavailable() {
        Records.StudyItem withoutSimilar = reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L);
        Records.StudyItem withSimilar = withoutSimilar.withHasSimilarKanji(true);

        List<Records.LadderRung> without = BridgeScheduler.rungsForItem(withoutSimilar);
        List<Records.LadderRung> with = BridgeScheduler.rungsForItem(withSimilar);

        assertFalse(without.contains(Records.LadderRung.SIMILAR_KANJI));
        assertTrue(with.contains(Records.LadderRung.SIMILAR_KANJI));
        assertEquals(Records.LadderRung.WRITE_KANJI, with.get(0));
        assertEquals(Records.LadderRung.WORD_READING, with.get(with.size() - 1));
    }

    @Test
    public void dueCountAndTokenSetCoverCollectionHelpers() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> tokens = scheduler.tokenSet(Arrays.asList("a", "b", "a"));

        assertEquals(2, tokens.size());
        assertEquals(1, scheduler.dueCount(
                Arrays.asList(
                        reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L),
                        reviewItem("提", Records.LadderRung.KANJI_MEANING, 0L).copyBuilder().state("retired").build(),
                        reviewItem("謎", Records.LadderRung.KANJI_MEANING, 2000L)
                ),
                1000L
        ));
        assertEquals(1, scheduler.dueCount(
                Arrays.asList(
                        reviewItem("裂", Records.LadderRung.KANJI_MEANING, 0L),
                        reviewItem("提", Records.LadderRung.KANJI_MEANING, 0L).copyBuilder().state("retired").build(),
                        reviewItem("謎", Records.LadderRung.KANJI_MEANING, 2000L)
                ),
                Arrays.asList(row("裂", 30), row("提", 20), row("謎", 10)),
                1000L
        ));
    }

    // --- Test factories ---

    private Records.StudyItem item(String kanji) {
        return new Records.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0);
    }

    private Records.StudyItem itemAtRung(String kanji, Records.LadderRung rung) {
        return item(kanji).withRungAndPhase(rung, Records.SchedulerPhase.NEW_LEARNING);
    }

    private Records.StudyItem reviewItem(String kanji, Records.LadderRung rung, long dueAtMillis) {
        return item(kanji).copyBuilder()
                .state("review")
                .dueAtMillis(dueAtMillis)
                .stability(1.0)
                .difficulty(5.0)
                .totalReviews(1)
                .lapses(0)
                .learningStep(2)
                .writingLevel(1)
                .rung(rung)
                .phase(Records.SchedulerPhase.REVIEW)
                .build();
    }

    private Records.StudyItem findItem(List<Records.StudyItem> items, String kanji) {
        for (Records.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + kanji);
    }

    private String ambiguousLegacyState(List<Records.StudyItem> items) {
        for (Records.StudyItem item : items) {
            if ("裂|stale|stale|stale".equals(item.answerSignature)) {
                return item.state;
            }
        }
        throw new AssertionError("Missing ambiguous legacy item");
    }

    private Records.DashboardRow row(String kanji, int score) {
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>());
    }

    private Records.DashboardRow rowWithExamples(String kanji, int score, Records.Example... examples) {
        ArrayList<Records.Example> list = new ArrayList<>();
        Collections.addAll(list, examples);
        return new Records.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, list);
    }

    private Records.Settings settingsWithQueue(int activeQueueCap, int newPerDay) {
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
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                activeQueueCap,
                newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove
        );
    }
}
