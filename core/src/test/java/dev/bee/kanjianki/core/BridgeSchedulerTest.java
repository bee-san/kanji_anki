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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
        RecordsSyncModels.Settings settings = new RecordsSyncModels.Settings(
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
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("裂", 20), row("謎", 19), row("示", 18));

        List<RecordsStudyModels.StudyItem> items = new BridgeScheduler().seedQueue(rows, Collections.emptyList(), settings, 1000L, 0L);

        assertEquals(1, items.size());
        assertEquals("裂", items.get(0).kanji);
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, items.get(0).rung);
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, items.get(0).phase);
    }

    @Test
    public void writingFailureOnWriteKanjiRungMapsToAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
                .withRungAndPhase(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW);
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", true, false, false, 0);

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("again", result.appliedRating);
    }

    @Test
    public void manualOverrideAllowsWritingRatingOnWriteKanjiRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
                .withRungAndPhase(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW);
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", true, false, true, 0);

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        // Manual override on the write rung is mapped to Hard by the scheduler
        // so the learner keeps progress but does not auto-pass.
        assertEquals("hard", result.appliedRating);
        assertFalse(result.duplicate);
        assertEquals(2, result.item.writingLevel);
    }

    @Test
    public void writingHelpOnlyChangesAfterWritingReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0);
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest("裂", "token-1", "easy", false, false, false, 0);

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(item, request, new HashSet<>(), 1000L);

        assertEquals("easy", result.appliedRating);
        assertEquals(2, result.item.writingLevel);
    }

    @Test
    public void cleanWritingAdvancesHintAssistedAndMessyWritingHold() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem template = new RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 1, "clean", 0)
                .withRungAndPhase(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW);

        RecordsSchedulerModels.ReviewResult clean = scheduler.applyReview(
                template,
                new RecordsSchedulerModels.ReviewRequest("裂", "clean", "hard", true, true, true, false, 0),
                new HashSet<>(),
                1000L
        );
        RecordsSchedulerModels.ReviewResult hinted = scheduler.applyReview(
                template.withToken("hinted"),
                new RecordsSchedulerModels.ReviewRequest("裂", "hinted", "good", true, true, true, false, 1),
                new HashSet<>(),
                1000L
        );
        RecordsSchedulerModels.ReviewResult messy = scheduler.applyReview(
                template.withToken("messy"),
                new RecordsSchedulerModels.ReviewRequest("裂", "messy", "hard", true, true, false, false, 0),
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
        RecordsStudyModels.StudyItem item = item("裂").withToken("token-1");
        HashSet<String> consumed = new HashSet<>();
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest("裂", "token-1", "easy", false, false, false, 0);

        RecordsSchedulerModels.ReviewResult first = scheduler.applyReview(item, request, consumed, 1000L);
        RecordsSchedulerModels.ReviewResult second = scheduler.applyReview(first.item.withToken("token-1"), request, consumed, 2000L);

        assertFalse(first.duplicate);
        assertTrue(second.duplicate);
        assertEquals(first.item.totalReviews, second.item.totalReviews);
    }

    @Test
    public void nextSessionRotatesTaskShapeForEachRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsSchedulerModels.StudySession typing = scheduler.nextSession(
                Collections.singletonList(itemAtRung("拉", RecordsBase.LadderRung.TYPE_MEANING)),
                Collections.singletonList(row("拉", 10)),
                1000L
        );
        RecordsSchedulerModels.StudySession kanji = scheduler.nextSession(
                Collections.singletonList(itemAtRung("裂", RecordsBase.LadderRung.KANJI_MEANING)),
                Collections.singletonList(row("裂", 10)),
                1000L
        );
        RecordsSchedulerModels.StudySession meaningKanji = scheduler.nextSession(
                Collections.singletonList(itemAtRung("浅", RecordsBase.LadderRung.MEANING_KANJI)),
                Collections.singletonList(row("浅", 10)),
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.MEANING_KANJI, true)
        );
        RecordsSchedulerModels.StudySession font = scheduler.nextSession(
                Collections.singletonList(itemAtRung("謎", RecordsBase.LadderRung.FONT_MEANING)),
                Collections.singletonList(row("謎", 10)),
                1000L
        );
        RecordsSchedulerModels.StudySession word = scheduler.nextSession(
                Collections.singletonList(itemAtRung("示", RecordsBase.LadderRung.WORD_READING)),
                Collections.singletonList(row("示", 10)),
                1000L
        );

        assertNotNull(typing);
        assertNotNull(meaningKanji);
        assertNotNull(kanji);
        assertNotNull(font);
        assertNotNull(word);
        assertEquals("type_meaning", typing.taskType);
        assertEquals("meaning_kanji", meaningKanji.taskType);
        assertEquals("kanji_meaning", kanji.taskType);
        assertEquals("font_meaning", font.taskType);
        assertEquals("word_reading", word.taskType);
        assertFalse(typing.writingRequired);
        assertFalse(meaningKanji.writingRequired);
        assertFalse(kanji.writingRequired);
        assertFalse(font.writingRequired);
        assertFalse(word.writingRequired);
    }

    @Test
    public void writeKanjiRungRoutesToWritingTask() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem pending = itemAtRung("裂", RecordsBase.LadderRung.WRITE_KANJI)
                .copyBuilder()
                .writingRemediationPending(true)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build();

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
                Collections.singletonList(pending),
                Collections.singletonList(row("裂", 10)),
                1000L
        );

        assertNotNull(session);
        assertTrue(session.writingRequired);
        assertEquals("write_kanji", session.taskType);
    }

    @Test
    public void targetedSessionUsesExistingItemAndLearnerMeaningPrompt() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem existing = itemAtRung("裂", RecordsBase.LadderRung.WORD_READING)
                .copyBuilder()
                .state("review")
                .activeToken("keep-token")
                .hasSimilarKanji(true)
                .build();

        RecordsSchedulerModels.StudySession session = scheduler.targetedSession(
                Collections.singletonList(existing),
                rowWithMeaning("裂", "split", "reason fallback"),
                1234L,
                RecordsBase.StudyLadderSettings.defaults()
        );

        assertNotNull(session);
        assertEquals("keep-token", session.token);
        assertEquals("keep-token", session.item.activeToken);
        assertSame(existing, scheduler.targetedStudyItem(Collections.singletonList(existing), "裂", 1234L, RecordsBase.StudyLadderSettings.defaults()));
        assertEquals(RecordsBase.LadderRung.WORD_READING, session.item.rung);
        assertEquals("word_reading", session.taskType);
        assertEquals("split", session.prompt);
        assertFalse(session.writingRequired);
    }

    @Test
    public void targetedSessionCreatesNewItemWithFallbackPromptAndEffectiveRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false);

        RecordsSchedulerModels.StudySession session = scheduler.targetedSession(
                Collections.emptyList(),
                rowWithMeaning("謎", "", "local reason"),
                1234L,
                ladder
        );

        assertNotNull(session);
        assertEquals("謎", session.item.kanji);
        assertEquals("new", session.item.state);
        assertEquals(1234L, session.item.dueAtMillis);
        assertEquals(1234L, session.item.createdAtMillis);
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, session.item.rung);
        assertEquals("meaning_kanji", session.taskType);
        assertEquals("local reason", session.prompt);
        assertFalse(session.writingRequired);
        assertTrue(session.token.startsWith("謎-"));
        assertEquals(session.token, session.item.activeToken);
    }

    @Test
    public void nextSessionPrioritizesDueReviewsBeforeNewCards() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem newProblem = itemAtRung("裂", RecordsBase.LadderRung.KANJI_MEANING);
        RecordsStudyModels.StudyItem dueReview = itemAtRung("謎", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("review")
                .dueAtMillis(500L)
                .stability(1.8)
                .difficulty(4.8)
                .totalReviews(2)
                .learningStep(2)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
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
        RecordsStudyModels.StudyItem review = itemAtRung("謎", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("review")
                .stability(2.0)
                .difficulty(4.0)
                .totalReviews(4)
                .learningStep(2)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
        RecordsStudyModels.StudyItem relearning = itemAtRung("習", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .dueAtMillis(500L)
                .stability(0.9)
                .difficulty(5.5)
                .totalReviews(2)
                .lapses(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build();
        RecordsStudyModels.StudyItem writeRung = itemAtRung("裂", RecordsBase.LadderRung.WRITE_KANJI)
                .copyBuilder()
                .state("review")
                .stability(1.0)
                .difficulty(5.0)
                .totalReviews(5)
                .lapses(2)
                .learningStep(2)
                .writingLevel(1)
                .writingRemediationPending(true)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();

        RecordsSchedulerModels.StudySession writeSession = scheduler.nextSession(
                Arrays.asList(review, relearning, writeRung),
                Arrays.asList(row("謎", 100), row("習", 10), row("裂", 1)),
                1000L
        );
        RecordsSchedulerModels.StudySession relearningSession = scheduler.nextSession(
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
    public void randomizedSessionTaskKeysUseDeterministicSeedAcrossTaskTypes() {
        BridgeScheduler scheduler = new BridgeScheduler();
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(
                reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L),
                reviewItem("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L),
                reviewItem("示", RecordsBase.LadderRung.WORD_READING, 0L)
        );
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("謎", 10), row("裂", 80), row("示", 50));

        List<String> first = scheduler.randomizedSessionTaskKeys(items, rows, 1000L, 0L, null, RecordsSyncModels.Settings.kikuDefaults(), RecordsBase.StudyLadderSettings.defaults(), 42L);
        List<String> second = scheduler.randomizedSessionTaskKeys(items, rows, 1000L, 0L, null, RecordsSyncModels.Settings.kikuDefaults(), RecordsBase.StudyLadderSettings.defaults(), 42L);
        List<String> dueSorted = Arrays.asList(
                BridgeScheduler.sessionTaskKeyForItem(items.get(1)),
                BridgeScheduler.sessionTaskKeyForItem(items.get(0)),
                BridgeScheduler.sessionTaskKeyForItem(items.get(2))
        );

        assertEquals(first, second);
        assertEquals(3, first.size());
        assertTrue(first.containsAll(dueSorted));
        assertNotEquals(dueSorted, first);
    }

    @Test
    public void plannedSessionSkipsWrongAnswerRelearningRepeatUntilNextSession() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem failedReview = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("review-token");
        RecordsStudyModels.StudyItem nextReview = reviewItem("謎", RecordsBase.LadderRung.WORD_READING, 0L);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("裂", 80), row("謎", 10));
        List<String> initialPlan = Arrays.asList(
                BridgeScheduler.sessionTaskKeyForItem(failedReview),
                BridgeScheduler.sessionTaskKeyForItem(nextReview)
        );

        RecordsSchedulerModels.ReviewResult wrong = scheduler.applyReview(
                failedReview,
                new RecordsSchedulerModels.ReviewRequest("裂", "review-token", "again", false, false, false, 0),
                new HashSet<>(),
                1000L
        );
        RecordsSchedulerModels.StudySession currentSessionNext = scheduler.nextSessionForTaskKeys(
                Arrays.asList(wrong.item, nextReview),
                rows,
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults(),
                initialPlan.subList(1, initialPlan.size())
        );
        List<String> nextSessionPlan = scheduler.randomizedSessionTaskKeys(
                Arrays.asList(wrong.item, nextReview),
                rows,
                wrong.item.dueAtMillis,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults(),
                7L
        );

        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, wrong.item.phase);
        assertTrue(wrong.item.dueAtMillis > 1000L);
        assertNotNull(currentSessionNext);
        assertEquals("謎", currentSessionNext.item.kanji);
        assertTrue(nextSessionPlan.contains(BridgeScheduler.sessionTaskKeyForItem(wrong.item)));
    }

    @Test
    public void nextSessionUsesWeaknessAndKanjiTieBreakersForSamePriorityDueItems() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem lowerWeakness = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        RecordsStudyModels.StudyItem higherWeakness = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        RecordsStudyModels.StudyItem firstKanji = reviewItem("亜", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        RecordsStudyModels.StudyItem laterKanji = reviewItem("唖", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        RecordsSchedulerModels.StudySession weaknessSession = scheduler.nextSession(
                Arrays.asList(lowerWeakness, higherWeakness),
                Arrays.asList(row("謎", 10), row("裂", 80)),
                1000L
        );
        RecordsSchedulerModels.StudySession kanjiSession = scheduler.nextSession(
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
        RecordsSyncModels.Settings settings = new RecordsSyncModels.Settings(
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
        RecordsStudyModels.StudyItem stale = item("古");

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
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
        RecordsStudyModels.StudyItem reviewed = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 0L)
                .copyBuilder()
                .totalReviews(3)
                .stability(1.5)
                .build();
        RecordsImportModels.DashboardRow covered = new RecordsImportModels.DashboardRow("裂", 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2, new ArrayList<>());

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(covered),
                Collections.singletonList(reviewed),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L
        );

        assertEquals("retired", findItem(items, "裂").state);
    }

    @Test
    public void seedQueueKeepsUnreviewedItemsEvenWithEnoughMatureSupport() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem unreviewed = item("裂");
        RecordsImportModels.DashboardRow covered = new RecordsImportModels.DashboardRow("裂", 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2, new ArrayList<>());

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(covered),
                Collections.singletonList(unreviewed),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L
        );

        assertEquals("new", findItem(items, "裂").state);
    }

    @Test
    public void seedQueueReopensRetiredItemsWhenWeakEvidenceReturns() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem retired = item("裂").copyBuilder()
                .state("retired")
                .stability(1.5)
                .difficulty(4.0)
                .totalReviews(3)
                .learningStep(2)
                .writingLevel(2)
                .rung(RecordsBase.LadderRung.WORD_READING)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.singletonList(retired),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L
        );

        RecordsStudyModels.StudyItem reopened = findItem(items, "裂");
        assertEquals("new", reopened.state);
        assertEquals(0, reopened.totalReviews);
        assertEquals(1000L, reopened.createdAtMillis);
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, reopened.rung);
    }

    @Test
    public void retiredItemsStayRetiredWhenAdmissionRoomIsFull() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem active = item("謎").copyBuilder()
                .createdAtMillis(0L)
                .build();
        RecordsStudyModels.StudyItem retired = item("裂").copyBuilder()
                .state("retired")
                .totalReviews(3)
                .build();

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
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
        RecordsStudyModels.StudyItem active = item("裂").copyBuilder()
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
        assertTrue(result.admittedAny());
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
        RecordsStudyModels.StudyItem active = item("謎");
        RecordsStudyModels.StudyItem retired = item("裂").copyBuilder()
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
    public void seedExtraNewCardsReportsNoAdmissionWhenRequestIsZero() {
        BridgeScheduler scheduler = new BridgeScheduler();

        BridgeScheduler.ExtraNewCardsResult result = scheduler.seedExtraNewCards(
                Arrays.asList(row("裂", 50), row("謎", 40)),
                Collections.emptyList(),
                settingsWithQueue(4, 1),
                2000L,
                0L,
                0
        );

        assertEquals(0, result.admittedCount);
        assertFalse(result.admittedAny());
        assertEquals(2, result.availableCount);
        assertTrue(result.admittedKanji.isEmpty());
    }

    @Test
    public void seedExtraNewCardsHonorsConfiguredNewCardSortMode() {
        BridgeScheduler scheduler = new BridgeScheduler();
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                rankedRow("低", 300, 40, example("低", 3.0, 0.60)),
                rankedRow("難", 100, 20, example("難", 8.0, 0.90)),
                rankedRow("弱", 200, 80, example("弱", null, 45.0))
        );

        assertEquals(Arrays.asList("難", "弱", "低"), scheduler.seedExtraNewCards(
                rows,
                Collections.emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                2000L,
                0L,
                3
        ).admittedKanji);
        assertEquals(Arrays.asList("難", "低", "弱"), scheduler.seedExtraNewCards(
                rows,
                Collections.emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
                2000L,
                0L,
                3
        ).admittedKanji);
        assertEquals(Arrays.asList("弱", "低", "難"), scheduler.seedExtraNewCards(
                rows,
                Collections.emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                2000L,
                0L,
                3
        ).admittedKanji);
        assertEquals(Arrays.asList("弱", "低", "難"), scheduler.seedExtraNewCards(
                rows,
                Collections.emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
                2000L,
                0L,
                3
        ).admittedKanji);
    }

    @Test
    public void nextSessionUsesNewCardSortOnlyForUnseenNewCards() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsSyncModels.Settings difficultySort = settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                rankedRow("低", 300, 90, example("低", 3.0, 0.60)),
                rankedRow("難", 100, 10, example("難", 8.0, 0.90))
        );
        BridgeScheduler.ExtraNewCardsResult result = scheduler.seedExtraNewCards(
                rows,
                Collections.emptyList(),
                difficultySort,
                2000L,
                0L,
                2
        );

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(result.items, rows, 2000L, 0L, null, difficultySort);

        assertNotNull(session);
        assertEquals("難", session.item.kanji);
    }

    @Test
    public void matureHigherRungSuppressesLowerRecognitionSiblings() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem word = matureReview("裂", RecordsBase.LadderRung.WORD_READING);
        RecordsStudyModels.StudyItem font = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 0L);
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(kanji, word, font));

        RecordsStudyModels.StudyItem updatedKanji = findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING);
        RecordsStudyModels.StudyItem updatedFont = findItemAtRung(result, RecordsBase.LadderRung.FONT_MEANING);
        assertEquals(RecordsBase.LadderRung.WORD_READING.wireName(), updatedKanji.suppressedByTaskType);
        assertEquals(RecordsBase.LadderRung.WORD_READING.wireName(), updatedFont.suppressedByTaskType);
        assertTrue(updatedKanji.suppressedAtMillis > 0L);
        assertTrue(updatedFont.suppressedAtMillis > 0L);
        assertTrue(findItemAtRung(result, RecordsBase.LadderRung.WORD_READING).suppressedByTaskType.isEmpty());
    }

    @Test
    public void fontMeaningOnlySuppressesKanjiMeaningSibling() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem font = matureReview("裂", RecordsBase.LadderRung.FONT_MEANING);
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        RecordsStudyModels.StudyItem typeMeaning = reviewItem("裂", RecordsBase.LadderRung.TYPE_MEANING, 0L);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(font, kanji, typeMeaning));

        assertEquals(RecordsBase.LadderRung.FONT_MEANING.wireName(), findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType);
        assertTrue(findItemAtRung(result, RecordsBase.LadderRung.TYPE_MEANING).suppressedByTaskType.isEmpty());
    }

    @Test
    public void writingRemediationSuppressesLowerRungsButNotWritingSiblings() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem writing = reviewItem("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L)
                .copyBuilder()
                .writingRemediationPending(true)
                .build();
        RecordsStudyModels.StudyItem typeMeaning = reviewItem("裂", RecordsBase.LadderRung.TYPE_MEANING, 0L);
        RecordsStudyModels.StudyItem otherWriting = reviewItem("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L)
                .copyBuilder()
                .activeToken("other-writing")
                .build();

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(typeMeaning, writing, otherWriting));

        assertEquals(RecordsBase.LadderRung.WRITE_KANJI.wireName(), findItemAtRung(result, RecordsBase.LadderRung.TYPE_MEANING).suppressedByTaskType);
        assertTrue(result.get(2).suppressedByTaskType.isEmpty());
    }

    @Test
    public void matureSiblingMemoryWithPassingRatingSuppressesLowerSibling() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.TaskMemory passingWordMemory = new RecordsStudyModels.TaskMemory("review", 0L, 5.0, 5.0, 12, 1, 0, "good", 21, 0, 0L);
        RecordsStudyModels.StudyItem word = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 0L)
                .copyBuilder()
                .wordReadingMemory(passingWordMemory)
                .matureIntervalDays(0)
                .totalReviews(0)
                .build();
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(word, kanji));

        assertEquals(RecordsBase.LadderRung.WORD_READING.wireName(), findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType);
    }

    @Test
    public void suppressionClearsWhenDominatingSiblingIsNoLongerMature() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem staleSuppressed = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .suppressedByTaskType(RecordsBase.LadderRung.FONT_MEANING.wireName())
                .suppressedAtMillis(123L)
                .build();
        RecordsStudyModels.StudyItem youngFont = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 0L)
                .copyBuilder()
                .matureIntervalDays(20)
                .totalReviews(10)
                .build();

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(staleSuppressed, youngFont));

        RecordsStudyModels.StudyItem updated = findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING);
        assertTrue(updated.suppressedByTaskType.isEmpty());
        assertEquals(0L, updated.suppressedAtMillis);
    }

    @Test
    public void suppressionIgnoresRetiredAndLearningSiblingsAndKeepsCurrentSuppressionWhenStillValid() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem retired = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .copyBuilder()
                .state("retired")
                .build();
        RecordsStudyModels.StudyItem learning = matureReview("裂", RecordsBase.LadderRung.FONT_MEANING)
                .copyBuilder()
                .state("new")
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build();
        RecordsStudyModels.StudyItem suppressed = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .suppressedByTaskType(RecordsBase.LadderRung.FONT_MEANING.wireName())
                .suppressedAtMillis(456L)
                .build();
        RecordsStudyModels.StudyItem matureFont = matureReview("裂", RecordsBase.LadderRung.FONT_MEANING);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(retired, learning, suppressed, matureFont));

        assertSame(retired, findItemAtRung(result, RecordsBase.LadderRung.WORD_READING));
        RecordsStudyModels.StudyItem updated = findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING);
        assertSame(suppressed, updated);
        assertEquals(RecordsBase.LadderRung.FONT_MEANING.wireName(), updated.suppressedByTaskType);
        assertEquals(456L, updated.suppressedAtMillis);
    }


    @Test
    public void matureSiblingSuppressionUsesAnswerSignatureNotOnlyKanji() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem word = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .withAnswerSignature("裂|裂ける|さける|split");
        RecordsStudyModels.StudyItem differentMeaning = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|破裂|はれつ|burst");

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(word, differentMeaning));

        assertTrue(findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType.isEmpty());
    }

    @Test
    public void matureSiblingSuppressionRequiresLastRatingNotAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.TaskMemory lapsedWordMemory = new RecordsStudyModels.TaskMemory("review", 0L, 5.0, 5.0, 12, 1, 0, "again", 21, 0, 0L);
        RecordsStudyModels.StudyItem lapsedWord = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .copyBuilder()
                .wordReadingMemory(lapsedWordMemory)
                .build();
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(lapsedWord, kanji));

        assertTrue(findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType.isEmpty());
    }

    @Test
    public void seededSiblingSuppressionRespectsSettingsMatureDays() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem wordBelowCustomMaturity = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .copyBuilder()
                .matureIntervalDays(21)
                .build()
                .withAnswerSignature("裂|裂ける|さける|split");
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|裂ける|さける|split");
        List<RecordsImportModels.DashboardRow> rows = Collections.singletonList(rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "裂ける", "さける", "split", "", false, 0)
        ));

        List<RecordsStudyModels.StudyItem> result = scheduler.seedQueue(
                rows,
                Arrays.asList(wordBelowCustomMaturity, kanji),
                settingsWithMatureDays(30),
                1000L,
                0L,
                (RecordsBase.StudyLadderSettings) null
        );

        assertTrue(findItemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType.isEmpty());
    }

    @Test
    public void coreSessionSelectionHidesSameFamilyWithoutPermanentSuppression() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem word = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .withAnswerSignature("裂|裂ける|さける|split");
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|裂ける|さける|split");
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(kanji, word);
        List<RecordsImportModels.DashboardRow> rows = Collections.singletonList(rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "裂ける", "さける", "split", "", false, 0)
        ));

        List<RecordsStudyModels.StudyItem> active = scheduler.activeQueueItems(items, rows, 1000L, null);
        RecordsSchedulerModels.StudySession session = scheduler.nextSession(items, rows, 1000L);

        assertEquals(1, active.size());
        assertEquals(RecordsBase.LadderRung.WORD_READING, active.get(0).rung);
        assertEquals(1, scheduler.dueCount(items, rows, 1000L));
        assertNotNull(session);
        assertEquals(RecordsBase.LadderRung.WORD_READING, session.item.rung);
    }

    @Test
    public void dueCountersDoNotCountSuppressedSiblingsWithoutRows() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem word = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .withAnswerSignature("裂|裂ける|さける|split");
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|裂ける|さける|split");
        List<RecordsStudyModels.StudyItem> suppressed = scheduler.applySuppression(Arrays.asList(kanji, word));

        assertEquals(1, scheduler.dueCount(suppressed, 1000L));
    }

    @Test
    public void immaturePromotedSiblingHidesLowerFamilyWithoutPermanentSuppression() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem immatureWord = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 0L)
                .copyBuilder()
                .matureIntervalDays(7)
                .totalReviews(2)
                .build()
                .withAnswerSignature("裂|裂ける|さける|split");
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|裂ける|さける|split");
        List<RecordsImportModels.DashboardRow> rows = Collections.singletonList(rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "裂ける", "さける", "split", "", false, 0)
        ));

        List<RecordsStudyModels.StudyItem> active = scheduler.activeQueueItems(Arrays.asList(kanji, immatureWord), rows, 1000L, null);
        List<RecordsStudyModels.StudyItem> suppressed = scheduler.applySuppression(Arrays.asList(kanji, immatureWord));

        assertEquals(1, active.size());
        assertEquals(RecordsBase.LadderRung.WORD_READING, active.get(0).rung);
        assertTrue(findItemAtRung(suppressed, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType.isEmpty());
    }

    @Test
    public void adaptivePlanLimitsNewAdmissionsToFocusSet() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsSchedulerModels.AdaptiveLoadPlan plan = new RecordsSchedulerModels.AdaptiveLoadPlan(
                20,
                1,
                1,
                Collections.singletonList("謎"),
                1,
                false,
                "focus"
        );

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(row("裂", 50), row("謎", 10)),
                Collections.emptyList(),
                RecordsSyncModels.Settings.kikuDefaults(),
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
        RecordsSchedulerModels.AdaptiveLoadPlan plan = new RecordsSchedulerModels.AdaptiveLoadPlan(
                20,
                2,
                2,
                Arrays.asList("古", "裂"),
                2,
                false,
                "focus"
        );
        RecordsImportModels.DashboardRow firstFamily = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "古い", "ふるい", "old", "", false, 0)
        );
        RecordsImportModels.DashboardRow secondFamily = rowWithExamples(
                "裂",
                25,
                new RecordsImportModels.Example("active", 2L, 2L, "新しい", "あたらしい", "new", "", false, 0)
        );
        RecordsStudyModels.StudyItem ambiguousLegacy = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|stale|stale|stale");

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
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
        RecordsSchedulerModels.AdaptiveLoadPlan plan = new RecordsSchedulerModels.AdaptiveLoadPlan(
                100,
                3,
                3,
                Arrays.asList("裂", "謎", "示"),
                3,
                true,
                "all"
        );

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Arrays.asList(row("裂", 50), row("謎", 10), row("示", 5)),
                Collections.emptyList(),
                new RecordsSyncModels.Settings(
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
        RecordsStudyModels.StudyItem stale = item("古");
        RecordsStudyModels.StudyItem current = item("裂").copyBuilder().dueAtMillis(500L).build();

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
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
        RecordsStudyModels.StudyItem learned = item("裂").copyBuilder()
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
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build();

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.singletonList(learned),
                RecordsSyncModels.Settings.kikuDefaults(),
                2000L,
                0L
        );

        RecordsStudyModels.StudyItem item = findItem(items, "裂");
        assertEquals("learning", item.state);
        assertEquals(1234L, item.dueAtMillis);
        assertEquals(2, item.totalReviews);
        assertEquals(1, item.lapses);
        assertEquals(2, item.writingLevel);
        assertEquals("active", item.activeToken);
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, item.phase);
    }

    @Test
    public void latestFsrsUsesLastReviewElapsedDaysForOnTimeReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long dueAt = 30L * BridgeScheduler.DAY;
        RecordsStudyModels.TaskMemory taskMemory = new RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .build()
                .withToken("latest");

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item,
                new RecordsSchedulerModels.ReviewRequest("裂", "latest", "good", false, false, false, 0),
                new HashSet<>(),
                dueAt
        );

        assertEquals(18, result.item.matureIntervalDays);
        assertEquals(dueAt + 18L * BridgeScheduler.DAY, result.item.dueAtMillis);
        assertEquals(18.01, result.item.stability, 0.0);
        assertEquals(5.99, result.item.difficulty, 0.0);
    }

    @Test
    public void reviewTransitionPassesOverdueElapsedDaysToFsrs() {
        RecordingFsrsAdapter adapter = new RecordingFsrsAdapter(3L * BridgeScheduler.DAY);
        BridgeScheduler scheduler = new BridgeScheduler(adapter);
        long now = 40L * BridgeScheduler.DAY;
        long dueAt = now - 2L * BridgeScheduler.DAY;
        RecordsStudyModels.TaskMemory taskMemory = new RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .activeToken("overdue")
                .build();

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item,
                new RecordsSchedulerModels.ReviewRequest("裂", "overdue", "good", false, false, false, 0),
                new HashSet<>(),
                now
        );

        assertEquals(9, adapter.elapsedDays);
        assertEquals(now + 3L * BridgeScheduler.DAY, result.item.dueAtMillis);
        assertEquals(3, result.item.matureIntervalDays);
    }

    @Test
    public void reviewTransitionFloorsFractionalElapsedDaysForFsrs() {
        RecordingFsrsAdapter adapter = new RecordingFsrsAdapter(4L * BridgeScheduler.DAY);
        BridgeScheduler scheduler = new BridgeScheduler(adapter);
        long halfDay = BridgeScheduler.DAY / 2L;
        long now = 40L * BridgeScheduler.DAY + halfDay;
        long dueAt = now - 2L * BridgeScheduler.DAY - halfDay;
        RecordsStudyModels.TaskMemory taskMemory = new RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .kanjiMeaningMemory(taskMemory)
                .activeToken("fractional")
                .build();

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item,
                new RecordsSchedulerModels.ReviewRequest("裂", "fractional", "good", false, false, false, 0),
                new HashSet<>(),
                now
        );

        assertEquals(9, adapter.elapsedDays);
        assertEquals(now + 4L * BridgeScheduler.DAY, result.item.dueAtMillis);
        assertEquals(4, result.item.matureIntervalDays);
    }

    @Test
    public void relearningGraduationPreservesPostLapseStability() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long dueAt = 30L * BridgeScheduler.DAY;
        RecordsStudyModels.TaskMemory taskMemory = new RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .build();
        HashSet<String> consumed = new HashSet<>();

        RecordsSchedulerModels.ReviewResult lapsed = scheduler.applyReview(
                item.withToken("lapse"),
                new RecordsSchedulerModels.ReviewRequest("裂", "lapse", "again", false, false, false, 0),
                consumed,
                dueAt
        );
        double postLapseStability = lapsed.item.stability;

        RecordsSchedulerModels.ReviewResult graduated = scheduler.applyReview(
                lapsed.item.withToken("graduate"),
                new RecordsSchedulerModels.ReviewRequest("裂", "graduate", "good", false, false, false, 0),
                consumed,
                lapsed.item.dueAtMillis
        );

        assertEquals(RecordsBase.SchedulerPhase.REVIEW, graduated.item.phase);
        assertEquals(postLapseStability, graduated.item.stability, 0.0);
        assertEquals(postLapseStability, graduated.item.kanjiMeaningMemory.stability, 0.0);
    }

    @Test
    public void activeRungInitialMemoryFallsBackToItemFsrsState() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long dueAt = 30L * BridgeScheduler.DAY;
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .build()
                .withToken("fallback");

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item,
                new RecordsSchedulerModels.ReviewRequest("裂", "fallback", "good", false, false, false, 0),
                new HashSet<>(),
                dueAt
        );

        assertEquals(18, result.item.matureIntervalDays);
        assertEquals(dueAt + 18L * BridgeScheduler.DAY, result.item.dueAtMillis);
        assertEquals(18, result.item.fontMeaningMemory.matureIntervalDays);
    }

    @Test
    public void promotionUpdatesNewActiveRungMemory() {
        BridgeScheduler scheduler = schedulerWithReviewIntervalDays(22);
        HashSet<String> consumed = new HashSet<>();
        long dueAt = 30L * BridgeScheduler.DAY;
        RecordsStudyModels.TaskMemory taskMemory = new RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .build();

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item.withToken("promote"),
                new RecordsSchedulerModels.ReviewRequest("裂", "promote", "good", false, false, false, 0),
                consumed,
                item.dueAtMillis
        );
        item = result.item;

        assertEquals(RecordsBase.LadderRung.FONT_MEANING, item.rung);
        assertEquals(item.dueAtMillis, item.fontMeaningMemory.dueAtMillis);
        assertEquals(item.matureIntervalDays, item.fontMeaningMemory.matureIntervalDays);
        assertEquals(item.totalReviews, item.fontMeaningMemory.totalReviews);
    }

    @Test
    public void fsrsPromotionBoundaryRequiresMoreThanConfiguredDays() {
        long dueAt = 30L * BridgeScheduler.DAY;
        RecordsStudyModels.TaskMemory taskMemory = new RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .build();

        RecordsSchedulerModels.ReviewResult exactBoundary = schedulerWithReviewIntervalDays(21).applyReview(
                item.withToken("exact-boundary"),
                new RecordsSchedulerModels.ReviewRequest("裂", "exact-boundary", "good", false, false, false, 0),
                new HashSet<>(),
                dueAt
        );
        RecordsSchedulerModels.ReviewResult beyondBoundary = schedulerWithReviewIntervalDays(22).applyReview(
                item.withToken("beyond-boundary"),
                new RecordsSchedulerModels.ReviewRequest("裂", "beyond-boundary", "good", false, false, false, 0),
                new HashSet<>(),
                dueAt
        );

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, exactBoundary.item.rung);
        assertEquals(21, exactBoundary.item.matureIntervalDays);
        assertEquals(dueAt + 21L * BridgeScheduler.DAY, exactBoundary.item.dueAtMillis);
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, beyondBoundary.item.rung);
        assertEquals(22, beyondBoundary.item.matureIntervalDays);
        assertEquals(dueAt + 22L * BridgeScheduler.DAY, beyondBoundary.item.dueAtMillis);
    }

    @Test
    public void matureSiblingSuppressionBoundaryStartsAtTwentyOneDays() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem staleSuppressed = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .suppressedByTaskType(RecordsBase.LadderRung.FONT_MEANING.wireName())
                .suppressedAtMillis(123L)
                .build();
        RecordsStudyModels.StudyItem youngFont = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 0L)
                .copyBuilder()
                .matureIntervalDays(20)
                .totalReviews(12)
                .build();
        RecordsStudyModels.StudyItem boundaryFont = reviewItem("語", RecordsBase.LadderRung.FONT_MEANING, 0L)
                .copyBuilder()
                .matureIntervalDays(21)
                .totalReviews(12)
                .build();
        RecordsStudyModels.StudyItem boundaryKanji = reviewItem("語", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(
                Arrays.asList(staleSuppressed, youngFont, boundaryKanji, boundaryFont)
        );

        RecordsStudyModels.StudyItem unsuppressedYoungSibling = findItem(result, "裂");
        RecordsStudyModels.StudyItem suppressedBoundarySibling = findItem(result, "語");
        assertTrue(unsuppressedYoungSibling.suppressedByTaskType.isEmpty());
        assertEquals(0L, unsuppressedYoungSibling.suppressedAtMillis);
        assertEquals(RecordsBase.LadderRung.FONT_MEANING.wireName(), suppressedBoundarySibling.suppressedByTaskType);
        assertTrue(suppressedBoundarySibling.suppressedAtMillis > 0L);
    }

    @Test
    public void customParametersAffectReviewInterval() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.TaskMemory matureMemory = new RecordsStudyModels.TaskMemory("review", 0L, 10.0, 5.0, 5, 0, 0, "good", 10);
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .stability(10.0)
                .difficulty(5.0)
                .totalReviews(5)
                .learningStep(0)
                .kanjiMeaningMemory(matureMemory)
                .build()
                .withToken("token-1");
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", false, false, false, 0);
        RecordsSchedulerModels.SchedulerParameters highRetention = new RecordsSchedulerModels.SchedulerParameters(0.95, 0.45, 1.2, 1.4, 2.2, 0, 0);
        RecordsSchedulerModels.SchedulerParameters lowRetention = new RecordsSchedulerModels.SchedulerParameters(0.80, 0.45, 1.2, 2.8, 4.2, 0, 0);

        RecordsSchedulerModels.ReviewResult highResult = scheduler.applyReview(item, request, new HashSet<>(), 1000L, highRetention);
        RecordsSchedulerModels.ReviewResult lowResult = scheduler.applyReview(item, request, new HashSet<>(), 1000L, lowRetention);

        assertTrue(lowResult.item.dueAtMillis > highResult.item.dueAtMillis);
    }

    @Test
    public void allowedKanjiFilterExcludesSuspendedKanjiFromActiveReview() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem suspended = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        RecordsStudyModels.StudyItem active = reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        Set<String> allowed = new HashSet<>(Collections.singletonList("提"));

        List<RecordsStudyModels.StudyItem> activeItems = scheduler.activeQueueItems(
                Arrays.asList(suspended, active),
                Arrays.asList(row("裂", 30), row("提", 20)),
                1000L,
                allowed
        );
        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
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

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row("裂", 30)),
                Collections.emptyList(),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L,
                (RecordsSchedulerModels.AdaptiveLoadPlan) null
        );

        assertEquals(1, items.size());
        assertEquals("裂", items.get(0).kanji);
    }

    @Test
    public void seedQueueAlignsLegacyEmptySignatureToSuspendedExample() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem legacy = new RecordsStudyModels.StudyItem("裂", "review", 1234L, 2.0, 4.0, 2, 0, 2, 1, null, 55L)
                .withAnswerSignature("");
        RecordsImportModels.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "破裂", "はれつ", "burst", "", false, 0),
                new RecordsImportModels.Example("suspended", 2L, 2L, "裂ける", "さける", "split", "", true, 0)
        );

        List<RecordsStudyModels.StudyItem> items = scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(legacy),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        );

        RecordsStudyModels.StudyItem aligned = findItem(items, "裂");
        assertEquals("review", aligned.state);
        assertEquals(1234L, aligned.dueAtMillis);
        assertEquals("裂|裂ける|さける|split", aligned.answerSignature);
    }

    @Test
    public void seedQueueKeepsMatchingAnswerSignatureProgress() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem current = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("裂|裂ける|さける|split");
        RecordsImportModels.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("suspended", 2L, 2L, "裂ける", "さける", "split", "", true, 0)
        );

        RecordsStudyModels.StudyItem aligned = findItem(scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(current),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂");

        assertEquals("review", aligned.state);
        assertEquals(1234L, aligned.dueAtMillis);
    }

    @Test
    public void seedQueueUsesFirstExampleFallbackAndNormalizesSignatureParts() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem legacy = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("");
        RecordsImportModels.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("other", 1L, 1L, "  split   apart  ", null, " main   meaning ", "", false, 0)
        );

        RecordsStudyModels.StudyItem aligned = findItem(scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(legacy),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂");

        assertEquals("裂|split apart||main meaning", aligned.answerSignature);
    }

    @Test
    public void seedQueueKeepsFirstActiveExampleWhenNoSuspendedExampleExists() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem legacy = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("");
        RecordsImportModels.DashboardRow row = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "first", "one", "meaning one", "", false, 0),
                new RecordsImportModels.Example("active", 2L, 2L, "second", "two", "meaning two", "", false, 0)
        );

        RecordsStudyModels.StudyItem aligned = findItem(scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.singletonList(legacy),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂");

        assertEquals("裂|first|one|meaning one", aligned.answerSignature);
    }

    @Test
    public void reseedResetsNonRetiredItemWhenAnswerSignatureChanges() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.TaskMemory oldMeaningMemory = new RecordsStudyModels.TaskMemory("review", 5000L, 2.0, 4.5, 7, 1, 2, "good", 12);
        RecordsStudyModels.StudyItem learned = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 5000L)
                .copyBuilder()
                .answerSignature("裂|old|old|old")
                .activeToken("active")
                .meaningKanjiMemory(oldMeaningMemory)
                .realPassStreak(2)
                .realAgainStreak(1)
                .lastRealReviewDueAtMillis(123L)
                .build();
        RecordsImportModels.DashboardRow changed = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "新しい", "あたらしい", "new", "", false, 0)
        );

        RecordsStudyModels.StudyItem reset = findItem(scheduler.seedQueue(
                Collections.singletonList(changed),
                Collections.singletonList(learned),
                RecordsSyncModels.Settings.kikuDefaults(),
                2000L,
                0L
        ), "裂");

        assertEquals("learning", reset.state);
        assertEquals(2000L, reset.dueAtMillis);
        assertEquals(0, reset.totalReviews);
        assertNull(reset.activeToken);
        assertEquals("裂|新しい|あたらしい|new", reset.answerSignature);
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, reset.rung);
        assertEquals(0, reset.realPassStreak);
        assertEquals(0, reset.lastRealReviewDueAtMillis);
        assertEquals("new", reset.meaningKanjiMemory.state);
        assertEquals(0, reset.meaningKanjiMemory.totalReviews);
    }

    @Test
    public void reseedRetiredItemOnlyUpdatesChangedAnswerSignature() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem retired = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 5000L)
                .copyBuilder()
                .state("retired")
                .answerSignature("裂|old|old|old")
                .build();
        RecordsImportModels.DashboardRow changed = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "新しい", "あたらしい", "new", "", false, 0)
        );

        RecordsStudyModels.StudyItem updated = findItem(scheduler.seedQueue(
                Collections.singletonList(changed),
                Collections.singletonList(retired),
                RecordsSyncModels.Settings.kikuDefaults(),
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
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withAnswerSignature("裂|expr|read|meaning");
        RecordsStudyModels.StudyItem word = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 5000L).withAnswerSignature("裂|expr|read|meaning");
        RecordsStudyModels.StudyItem legacyEmptySignature = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L).withAnswerSignature("");

        List<RecordsStudyModels.StudyItem> active = scheduler.activeQueueItems(
                Arrays.asList(kanji, word, legacyEmptySignature),
                Arrays.asList(rowWithExamples("裂", 30, new RecordsImportModels.Example("active", 1L, 1L, "expr", "read", "meaning", "", false, 0)), row("謎", 20)),
                1000L,
                null
        );

        assertEquals(2, active.size());
        assertEquals("謎", findItem(active, "謎").kanji);
        assertEquals(RecordsBase.LadderRung.WORD_READING, findItem(active, "裂").rung);
    }

    @Test
    public void activeQueueRejectsChangedNonEmptyAnswerSignature() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem staleFamily = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|old|read|meaning");
        RecordsImportModels.DashboardRow currentRow = rowWithExamples(
                "裂",
                30,
                new RecordsImportModels.Example("active", 1L, 1L, "new", "read", "meaning", "", false, 0)
        );

        List<RecordsStudyModels.StudyItem> active = scheduler.activeQueueItems(
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
        RecordsSchedulerModels.StudySession reused = scheduler.nextSession(
                Collections.singletonList(reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("kept")),
                Collections.singletonList(row("裂", 30)),
                1000L
        );
        RecordsSchedulerModels.StudySession generated = scheduler.nextSession(
                Collections.singletonList(reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("")),
                Collections.singletonList(row("謎", 30)),
                1000L
        );

        assertEquals("kept", reused.token);
        assertTrue(generated.token.startsWith("謎-"));
    }

    @Test
    public void nextSessionReturnsNullWhenNothingDueOrAllowed() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem future = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 2000L);

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
        RecordsStudyModels.StudyItem active = item("裂").withToken("expected");

        RecordsSchedulerModels.ReviewResult duplicate = scheduler.applyReview(
                active,
                new RecordsSchedulerModels.ReviewRequest("裂", "actual", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L,
                null,
                null
        );
        RecordsSchedulerModels.ReviewResult normalized = scheduler.applyReview(
                item("提"),
                new RecordsSchedulerModels.ReviewRequest("提", "token", null, false, false, false, 0),
                new HashSet<>(),
                1000L,
                null,
                null
        );

        assertTrue(duplicate.duplicate);
        assertEquals("again", normalized.appliedRating);
        assertEquals("learning", normalized.item.state);

        RecordsSchedulerModels.ReviewResult emptyToken = scheduler.applyReview(
                item("空").withToken(""),
                new RecordsSchedulerModels.ReviewRequest("空", "token", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L
        );
        assertFalse(emptyToken.duplicate);
    }

    @Test
    public void invalidRatingDefaultsToAgainAndResetsLearningStep() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem item = item("裂").copyBuilder().learningStep(1).build();

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item.withToken("bad-rating"),
                new RecordsSchedulerModels.ReviewRequest("裂", "bad-rating", "perfect", false, false, false, 0),
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
        RecordsStudyModels.StudyItem relearning = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .state("learning")
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .learningStep(0)
                .activeToken("relearn")
                .build();
        RecordsSchedulerModels.LearningStepSettings steps = new RecordsSchedulerModels.LearningStepSettings(
                RecordsSchedulerModels.LearningStepSettings.defaultNewSteps(),
                Arrays.asList(5, 20)
        );

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                relearning,
                new RecordsSchedulerModels.ReviewRequest("裂", "relearn", "good", false, false, false, 0),
                new HashSet<>(),
                1000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                steps
        );

        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, result.item.phase);
        assertEquals(1, result.item.learningStep);
    }

    @Test
    public void learningHardRepeatsLaterStep() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem learning = item("裂").copyBuilder()
                .state("learning")
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .learningStep(1)
                .activeToken("hard")
                .build()
                .withTaskMemory(
                        BridgeScheduler.TASK_KANJI_MEANING,
                        new RecordsStudyModels.TaskMemory("learning", 0L, 0.4, 5.0, 1, 0, 1, "", 0)
                );

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                learning,
                new RecordsSchedulerModels.ReviewRequest("裂", "hard", "hard", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(1, result.item.learningStep);
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, result.item.phase);
    }

    @Test
    public void learningHardOnSingleStepUsesAgainDelay() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem learning = item("裂").copyBuilder()
                .state("learning")
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .activeToken("hard")
                .build();
        RecordsSchedulerModels.LearningStepSettings steps = new RecordsSchedulerModels.LearningStepSettings(
                Collections.singletonList(5),
                RecordsSchedulerModels.LearningStepSettings.defaultReviewSteps()
        );

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                learning,
                new RecordsSchedulerModels.ReviewRequest("裂", "hard", "hard", false, false, false, 0),
                new HashSet<>(),
                1000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                steps
        );

        assertEquals(0, result.item.learningStep);
        assertEquals(1000L + 5 * 60_000L, result.item.dueAtMillis);
    }

    @Test
    public void futureReviewAgainDoesNotCountAsRealDueFailure() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem future = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 5000L)
                .withToken("future");

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                future,
                new RecordsSchedulerModels.ReviewRequest("裂", "future", "again", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(0, result.item.realAgainStreak);
        assertEquals(0L, result.item.lastRealReviewDueAtMillis);
    }

    @Test
    public void practicedLearningCardsSortBeforeDueReviews() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem practicedLearning = item("学").copyBuilder()
                .state("learning")
                .dueAtMillis(0L)
                .totalReviews(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build();
        RecordsStudyModels.StudyItem review = reviewItem("復", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
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
        RecordsStudyModels.StudyItem retired = item("古").copyBuilder().state("retired").build();
        RecordsStudyModels.StudyItem missing = item("消");
        RecordsStudyModels.StudyItem fontRung = itemAtRung("裂", RecordsBase.LadderRung.FONT_MEANING);

        List<RecordsStudyModels.StudyItem> active = scheduler.activeQueueItems(
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
        RecordsStudyModels.StudyItem due = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1000L).withToken("due");
        RecordsStudyModels.StudyItem future = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 5000L).withToken("future");
        RecordsStudyModels.StudyItem earlierFuture = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 3000L).withToken("early");
        RecordsStudyModels.StudyItem laterFuture = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 5000L).withToken("late");

        List<RecordsStudyModels.StudyItem> activeDue = scheduler.activeQueueItems(
                Arrays.asList(future, due),
                Collections.singletonList(row("裂", 30)),
                2000L,
                null
        );
        List<RecordsStudyModels.StudyItem> activeFuture = scheduler.activeQueueItems(
                Arrays.asList(laterFuture, earlierFuture),
                Collections.singletonList(row("謎", 30)),
                1000L,
                null
        );

        assertEquals(1, activeDue.size());
        assertEquals("due", activeDue.get(0).activeToken);
        assertEquals(1, activeFuture.size());
        assertEquals("early", activeFuture.get(0).activeToken);

        List<RecordsStudyModels.StudyItem> alreadyBest = scheduler.activeQueueItems(
                Arrays.asList(due, future),
                Collections.singletonList(row("裂", 30)),
                2000L,
                null
        );
        assertEquals("due", alreadyBest.get(0).activeToken);
    }

    @Test
    public void rungsForItemSkipsSimilarOnlyWhenUnavailable() {
        RecordsStudyModels.StudyItem withoutSimilar = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        RecordsStudyModels.StudyItem withSimilar = withoutSimilar.withHasSimilarKanji(true);

        List<RecordsBase.LadderRung> without = BridgeScheduler.rungsForItem(withoutSimilar);
        List<RecordsBase.LadderRung> with = BridgeScheduler.rungsForItem(withSimilar);

        assertFalse(without.contains(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertTrue(with.contains(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertTrue(without.contains(RecordsBase.LadderRung.MEANING_KANJI));
        assertTrue(with.contains(RecordsBase.LadderRung.MEANING_KANJI));
        assertTrue(with.indexOf(RecordsBase.LadderRung.TYPE_MEANING) < with.indexOf(RecordsBase.LadderRung.MEANING_KANJI));
        assertTrue(with.indexOf(RecordsBase.LadderRung.MEANING_KANJI) < with.indexOf(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, with.get(0));
        assertEquals(RecordsBase.LadderRung.WORD_READING, with.get(with.size() - 1));
    }

    @Test
    public void rungsForItemHonorsDisabledConfiguredRungs() {
        RecordsStudyModels.StudyItem withSimilar = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withHasSimilarKanji(true);
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false);

        List<RecordsBase.LadderRung> rungs = BridgeScheduler.rungsForItem(withSimilar, ladder);

        assertFalse(rungs.contains(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertFalse(rungs.contains(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, rungs.get(0));
    }

    @Test
    public void nextSessionMapsDisabledCurrentRungToNearestEnabledRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false);
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withHasSimilarKanji(true);

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
                Collections.singletonList(item),
                Collections.singletonList(row("裂", 20)),
                0L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        );

        assertNotNull(session);
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, session.item.rung);
        assertEquals(BridgeScheduler.TASK_MEANING_KANJI, session.taskType);
    }

    @Test
    public void mappedMeaningKanjiReviewInheritsExistingSchedulerMemory() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false);
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 500L)
                .copyBuilder()
                .stability(2.5)
                .difficulty(4.2)
                .totalReviews(5)
                .matureIntervalDays(12)
                .build();
        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
                Collections.singletonList(item),
                Collections.singletonList(row("裂", 20)),
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        );

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                session.item.withToken("meaning-pass"),
                new RecordsSchedulerModels.ReviewRequest("裂", "meaning-pass", "good", false, false, false, 0),
                new HashSet<>(),
                1000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        );

        assertEquals(6, result.item.meaningKanjiMemory.totalReviews);
    }

    @Test
    public void customLadderOrderControlsPromotion() {
        BridgeScheduler scheduler = schedulerWithReviewIntervalDays(22);
        HashSet<String> consumed = new HashSet<>();
        RecordsBase.StudyLadderSettings ladder = new RecordsBase.StudyLadderSettings(
                Arrays.asList(
                        RecordsBase.LadderRung.WRITE_KANJI,
                        RecordsBase.LadderRung.KANJI_MEANING,
                        RecordsBase.LadderRung.WORD_READING,
                        RecordsBase.LadderRung.FONT_MEANING,
                        RecordsBase.LadderRung.TYPE_MEANING,
                        RecordsBase.LadderRung.SIMILAR_KANJI
                ),
                Arrays.asList(
                        RecordsBase.LadderRung.WRITE_KANJI,
                        RecordsBase.LadderRung.KANJI_MEANING,
                        RecordsBase.LadderRung.WORD_READING,
                        RecordsBase.LadderRung.FONT_MEANING,
                        RecordsBase.LadderRung.TYPE_MEANING,
                        RecordsBase.LadderRung.SIMILAR_KANJI
                )
        );
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                item.withToken("custom-order"),
                new RecordsSchedulerModels.ReviewRequest("裂", "custom-order", "good", false, false, false, 0),
                consumed,
                item.dueAtMillis,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        );
        item = result.item;

        assertEquals(RecordsBase.LadderRung.WORD_READING, item.rung);
    }

    @Test
    public void dueCountAndTokenSetCoverCollectionHelpers() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> tokens = scheduler.tokenSet(Arrays.asList("a", "b", "a"));

        assertEquals(2, tokens.size());
        assertEquals(1, scheduler.dueCount(
                Arrays.asList(
                        reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L),
                        reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, 0L).copyBuilder().state("retired").build(),
                        reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 2000L)
                ),
                1000L
        ));
        assertEquals(1, scheduler.dueCount(
                Arrays.asList(
                        reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L),
                        reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, 0L).copyBuilder().state("retired").build(),
                        reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 2000L)
                ),
                Arrays.asList(row("裂", 30), row("提", 20), row("謎", 10)),
                1000L
        ));
    }

    @Test
    public void studyAheadZeroMinutesMatchesBaselineActiveQueue() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem dueNow = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1000L);
        RecordsStudyModels.StudyItem inFiveMin = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 1000L + 5L * 60_000L);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("裂", 30), row("謎", 20));

        List<RecordsStudyModels.StudyItem> baseline = scheduler.activeQueueItems(Arrays.asList(dueNow, inFiveMin), rows, 1000L, null);
        List<RecordsStudyModels.StudyItem> zeroAhead = scheduler.activeQueueItems(Arrays.asList(dueNow, inFiveMin), rows, 1000L, 0L, null);

        assertEquals(baseline.size(), zeroAhead.size());
        assertNotNull(scheduler.nextSession(Arrays.asList(dueNow, inFiveMin), rows, 1000L, 0L, null));
    }

    @Test
    public void studyAheadFifteenMinutesPullsItemDueWithinHorizon() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = 1_000_000L;
        long dueIn10Min = now + 10L * 60_000L;
        RecordsStudyModels.StudyItem ahead = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, dueIn10Min);
        List<RecordsImportModels.DashboardRow> rows = Collections.singletonList(row("謎", 20));

        RecordsSchedulerModels.StudySession none = scheduler.nextSession(Collections.singletonList(ahead), rows, now);
        assertNull(none);

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
                Collections.singletonList(ahead), rows, now, 15L * 60_000L, null
        );
        assertNotNull(session);
        assertEquals("謎", session.item.kanji);
    }

    @Test
    public void studyAheadFifteenMinutesExcludesItemBeyondHorizon() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = 1_000_000L;
        long dueIn30Min = now + 30L * 60_000L;
        RecordsStudyModels.StudyItem beyond = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, dueIn30Min);
        List<RecordsImportModels.DashboardRow> rows = Collections.singletonList(row("謎", 20));

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
                Collections.singletonList(beyond), rows, now, 15L * 60_000L, null
        );
        assertNull(session);
    }

    @Test
    public void studyAheadDueCountIncludesHorizonEligibleItems() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = 1_000_000L;
        RecordsStudyModels.StudyItem dueNow = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, now);
        RecordsStudyModels.StudyItem dueIn5 = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, now + 5L * 60_000L);
        RecordsStudyModels.StudyItem dueIn30 = reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, now + 30L * 60_000L);
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(dueNow, dueIn5, dueIn30);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("裂", 30), row("謎", 20), row("提", 10));

        assertEquals(1, scheduler.dueCount(items, rows, now));
        assertEquals(2, scheduler.dueCount(items, rows, now, 15L * 60_000L));
        assertEquals(3, scheduler.dueCount(items, rows, now, 60L * 60_000L));
    }

    @Test
    public void studyAheadClampsNegativeToZeroAndAboveDayToDay() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = 1_000_000L;
        assertEquals(
                (long) SettingsInputRules.MAX_STUDY_AHEAD_MINUTES * 60_000L,
                StudyLadderRules.clampStudyAheadMillis(Long.MAX_VALUE)
        );
        long dueIn1Hour = now + 60L * 60_000L;
        long dueIn25Hours = now + 25L * 60L * 60_000L;
        RecordsStudyModels.StudyItem nearItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, dueIn1Hour);
        RecordsStudyModels.StudyItem farItem = reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, dueIn25Hours);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("謎", 20), row("提", 10));

        RecordsSchedulerModels.StudySession negative = scheduler.nextSession(
                Collections.singletonList(nearItem), rows, now, -5L * 60_000L, null
        );
        assertNull(negative);

        RecordsSchedulerModels.StudySession farBeyondDay = scheduler.nextSession(
                Collections.singletonList(farItem), rows, now, 48L * 60L * 60_000L, null
        );
        assertNull(farBeyondDay);

        RecordsSchedulerModels.StudySession withinDay = scheduler.nextSession(
                Collections.singletonList(nearItem), rows, now, 48L * 60L * 60_000L, null
        );
        assertNotNull(withinDay);
        assertEquals("謎", withinDay.item.kanji);
    }

    @Test
    public void studyAheadPrefersTrulyDueOverHorizonEligibleAtSameRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = 1_000_000L;
        RecordsStudyModels.StudyItem dueNow = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, now).withToken("now");
        RecordsStudyModels.StudyItem dueIn5 = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, now + 5L * 60_000L).withToken("ahead");
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("裂", 30), row("謎", 20));

        RecordsSchedulerModels.StudySession session = scheduler.nextSession(
                Arrays.asList(dueIn5, dueNow), rows, now, 15L * 60_000L, null
        );
        assertNotNull(session);
        assertEquals("裂", session.item.kanji);
    }

    @Test
    public void studyAheadDoesNotShiftLearningStepDelaysOnAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = 1_000_000L;
        long dueIn5Min = now + 5L * 60_000L;
        RecordsStudyModels.StudyItem reviewItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueIn5Min)
                .copyBuilder()
                .activeToken("token-ahead")
                .build();
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest("裂", "token-ahead", "again", false, false, false, 0);

        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(reviewItem, request, new HashSet<>(), now);

        long expectedNextDue = now + RecordsSchedulerModels.LearningStepSettings.defaults().reviewStepsMinutes.get(0) * 60_000L;
        assertEquals(expectedNextDue, result.item.dueAtMillis);
    }

    // --- Test factories ---

    private RecordsStudyModels.StudyItem item(String kanji) {
        return new RecordsStudyModels.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0);
    }

    private RecordsStudyModels.StudyItem itemAtRung(String kanji, RecordsBase.LadderRung rung) {
        return item(kanji).withRungAndPhase(rung, RecordsBase.SchedulerPhase.NEW_LEARNING);
    }

    private RecordsStudyModels.StudyItem reviewItem(String kanji, RecordsBase.LadderRung rung, long dueAtMillis) {
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
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
    }

    private RecordsStudyModels.StudyItem matureReview(String kanji, RecordsBase.LadderRung rung) {
        return reviewItem(kanji, rung, 0L).copyBuilder()
                .matureIntervalDays(21)
                .totalReviews(12)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
    }

    private RecordsStudyModels.StudyItem findItemAtRung(List<RecordsStudyModels.StudyItem> items, RecordsBase.LadderRung rung) {
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.rung == rung) {
                return item;
            }
        }
        throw new AssertionError("Missing study item at rung " + rung);
    }

    private RecordsStudyModels.StudyItem findItem(List<RecordsStudyModels.StudyItem> items, String kanji) {
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + kanji);
    }

    private String ambiguousLegacyState(List<RecordsStudyModels.StudyItem> items) {
        for (RecordsStudyModels.StudyItem item : items) {
            if ("裂|stale|stale|stale".equals(item.answerSignature)) {
                return item.state;
            }
        }
        throw new AssertionError("Missing ambiguous legacy item");
    }

    private RecordsImportModels.DashboardRow row(String kanji, int score) {
        return new RecordsImportModels.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, new ArrayList<>());
    }

    private RecordsImportModels.DashboardRow rowWithMeaning(String kanji, String meaning, String reasonText) {
        return new RecordsImportModels.DashboardRow(kanji, 900, meaning, "reading", "search", 10, "reason", reasonText, 1, 0, 0, new ArrayList<>());
    }

    private RecordsImportModels.DashboardRow rowWithExamples(String kanji, int score, RecordsImportModels.Example... examples) {
        ArrayList<RecordsImportModels.Example> list = new ArrayList<>();
        Collections.addAll(list, examples);
        return new RecordsImportModels.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, list);
    }

    private RecordsImportModels.DashboardRow rankedRow(String kanji, Integer rank, int score, RecordsImportModels.Example... examples) {
        ArrayList<RecordsImportModels.Example> list = new ArrayList<>();
        Collections.addAll(list, examples);
        return new RecordsImportModels.DashboardRow(kanji, rank, "meaning", "reading", "search", score, "reason", "reason text", 1, score > 15 ? 1 : 0, 0, list);
    }

    private RecordsImportModels.Example example(String kanji, Double difficulty, Double retrievability) {
        long id = kanji.codePointAt(0);
        return new RecordsImportModels.Example(
                "active",
                id,
                id + 1L,
                kanji,
                "reading",
                "meaning",
                "",
                false,
                0,
                10,
                3,
                20.0,
                difficulty,
                retrievability
        );
    }

    private BridgeScheduler schedulerWithReviewIntervalDays(long intervalDays) {
        return new BridgeScheduler(new FixedIntervalFsrsAdapter(intervalDays * BridgeScheduler.DAY));
    }

    private static final class FixedIntervalFsrsAdapter implements KaniFsrsAdapter {
        private final long reviewIntervalMillis;

        FixedIntervalFsrsAdapter(long reviewIntervalMillis) {
            this.reviewIntervalMillis = reviewIntervalMillis;
        }

        @Override
        public KaniFsrsReviewResult initialReview(
                String rating,
                double currentStability,
                double currentDifficulty,
                double targetRetention,
                boolean isNewLearning
        ) {
            return new KaniFsrsReviewResult(currentStability, currentDifficulty, BridgeScheduler.DAY);
        }

        @Override
        public KaniFsrsReviewResult review(
                double stability,
                double difficulty,
                String rating,
                int elapsedDays,
                double targetRetention
        ) {
            return new KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis);
        }
    }

    private static final class RecordingFsrsAdapter implements KaniFsrsAdapter {
        private final long reviewIntervalMillis;
        private int elapsedDays = -1;

        RecordingFsrsAdapter(long reviewIntervalMillis) {
            this.reviewIntervalMillis = reviewIntervalMillis;
        }

        @Override
        public KaniFsrsReviewResult initialReview(
                String rating,
                double currentStability,
                double currentDifficulty,
                double targetRetention,
                boolean isNewLearning
        ) {
            return new KaniFsrsReviewResult(currentStability, currentDifficulty, BridgeScheduler.DAY);
        }

        @Override
        public KaniFsrsReviewResult review(
                double stability,
                double difficulty,
                String rating,
                int elapsedDays,
                double targetRetention
        ) {
            this.elapsedDays = elapsedDays;
            return new KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis);
        }
    }

    private RecordsSyncModels.Settings settingsWithQueue(int activeQueueCap, int newPerDay) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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

    private RecordsSyncModels.Settings settingsWithMatureDays(int matureDays) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove
        );
    }

    private RecordsSyncModels.Settings settingsWithSortMode(String mode) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                defaults.importActiveCards,
                defaults.importSuspendedCards,
                defaults.importTaggedCards,
                defaults.importTags,
                defaults.importWeakCards,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                defaults.importMinMatchingCardsPerKanji,
                defaults.importBrowserQueryCards,
                defaults.importBrowserQuery,
                mode
        );
    }
}
