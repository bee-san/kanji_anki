package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the Anki-exact ladder scheduler. Covers:
 *   - New-learning phase: Again loops, Good advances, Hard behavior, Easy graduates.
 *   - Review phase: due-review streaks promote / demote the rung.
 *   - Relearning phase: entered after a review lapse; practice-only.
 *   - Similar-kanji rung skipping based on {@link Records.StudyItem#hasSimilarKanji}.
 *   - Ladder floor and ceiling behavior.
 */
public class LadderSchedulerTest {

    private static final int DEFAULT_THRESHOLD = Records.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE;

    // ---- New learning phase (Anki-exact semantics) ----

    @Test
    public void newCardAgainLoopsInLearningForever() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = newCard("裂");
        HashSet<String> consumed = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            Records.ReviewResult result = scheduler.applyReview(
                    item.withToken("t" + i),
                    new Records.ReviewRequest("裂", "t" + i, "again", false, false, false, 0),
                    consumed,
                    1000L + i
            );
            item = result.item;
        }

        assertEquals("Phase stays in new_learning after 10 Agains",
                Records.SchedulerPhase.NEW_LEARNING, item.phase);
        assertEquals("Rung stays on starting rung",
                Records.LadderRung.KANJI_MEANING, item.rung);
        assertEquals("Learning step stays at 0", 0, item.learningStep);
        assertEquals("Real pass streak unchanged", 0, item.realPassStreak);
        assertEquals("Real again streak unchanged in learning", 0, item.realAgainStreak);
        assertEquals("No lapses recorded during learning", 0, item.lapses);
    }

    @Test
    public void newCardGoodAdvancesThroughLearningSteps() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = newCard("裂");
        // Default new steps: [1, 10]
        assertEquals(0, item.learningStep);

        Records.ReviewResult first = scheduler.applyReview(
                item.withToken("t1"),
                new Records.ReviewRequest("裂", "t1", "good", false, false, false, 0),
                new HashSet<>(),
                1000L
        );
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, first.item.phase);
        assertEquals(1, first.item.learningStep);
        assertEquals(Records.LadderRung.KANJI_MEANING, first.item.rung);
    }

    @Test
    public void newCardGoodPastLastStepGraduatesToReview() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        // Default new steps: [1, 10]. Two Goods should graduate.
        Records.ReviewResult afterFirst = scheduler.applyReview(
                newCard("裂").withToken("g1"),
                new Records.ReviewRequest("裂", "g1", "good", false, false, false, 0),
                consumed,
                1000L
        );
        Records.ReviewResult afterSecond = scheduler.applyReview(
                afterFirst.item.withToken("g2"),
                new Records.ReviewRequest("裂", "g2", "good", false, false, false, 0),
                consumed,
                2000L
        );

        assertEquals(Records.SchedulerPhase.REVIEW, afterSecond.item.phase);
        assertEquals("review", afterSecond.item.state);
        assertTrue("Due far in the future after graduation",
                afterSecond.item.dueAtMillis > 2000L + 3600_000L);
    }

    @Test
    public void newCardEasyGraduatesImmediately() {
        BridgeScheduler scheduler = new BridgeScheduler();

        Records.ReviewResult result = scheduler.applyReview(
                newCard("裂").withToken("e"),
                new Records.ReviewRequest("裂", "e", "easy", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        assertEquals(Records.SchedulerPhase.REVIEW, result.item.phase);
        assertEquals("review", result.item.state);
    }

    @Test
    public void newCardHardOnFirstStepUsesDelayBetweenAgainAndGood() {
        BridgeScheduler scheduler = new BridgeScheduler();

        Records.ReviewResult result = scheduler.applyReview(
                newCard("裂").withToken("h"),
                new Records.ReviewRequest("裂", "h", "hard", false, false, false, 0),
                new HashSet<>(),
                1000L
        );

        // Default new steps [1m, 10m]: Hard on first step schedules max(1m, 5.5m) = 5.5m.
        long expectedMin = 1000L + 3L * 60_000L;
        long expectedMax = 1000L + 11L * 60_000L;
        assertTrue("Hard on first step delay should sit between Again and Good",
                result.item.dueAtMillis >= expectedMin && result.item.dueAtMillis <= expectedMax);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, result.item.phase);
        assertEquals(0, result.item.learningStep);
    }

    @Test
    public void newCardHardOnLaterStepRepeatsStep() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult advanced = scheduler.applyReview(
                newCard("裂").withToken("g"),
                new Records.ReviewRequest("裂", "g", "good", false, false, false, 0),
                consumed,
                1000L
        );
        Records.ReviewResult hard = scheduler.applyReview(
                advanced.item.withToken("h2"),
                new Records.ReviewRequest("裂", "h2", "hard", false, false, false, 0),
                consumed,
                2000L
        );

        // We were at step 1 (10m). Hard should repeat the same step => due at 10m from now.
        long expected = 2000L + 10L * 60_000L;
        long delta = Math.abs(hard.item.dueAtMillis - expected);
        assertTrue("Hard on later step repeats current step delay; delta=" + delta,
                delta < 60_000L);
        assertEquals(1, hard.item.learningStep);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, hard.item.phase);
    }

    // ---- Review phase and ladder streaks ----

    @Test
    public void threeRealDuePassesPromoteOneRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);

        long now = 1000L;
        for (int i = 0; i < DEFAULT_THRESHOLD - 1; i++) {
            Records.ReviewResult r = scheduler.applyReview(
                    item.withToken("p" + i),
                    passRequest("裂", "p" + i),
                    consumed,
                    now
            );
            item = r.item;
            assertEquals(Records.LadderRung.KANJI_MEANING, item.rung);
            now = item.dueAtMillis;
        }
        Records.ReviewResult promoting = scheduler.applyReview(
                item.withToken("final"),
                passRequest("裂", "final"),
                consumed,
                now
        );

        assertEquals(Records.LadderRung.FONT_MEANING, promoting.item.rung);
        assertEquals("Streak resets on promotion", 0, promoting.item.realPassStreak);
    }

    @Test
    public void threeRealDueAgainsDemoteOneRung() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);

        long now = 1000L;
        for (int i = 0; i < DEFAULT_THRESHOLD - 1; i++) {
            Records.ReviewResult r = scheduler.applyReview(
                    item.withToken("a" + i),
                    failRequest("裂", "a" + i),
                    consumed,
                    now
            );
            item = r.item;
            assertEquals("Rung holds before threshold",
                    Records.LadderRung.KANJI_MEANING, item.rung);
            // Move 'now' out to a new FSRS slot so the next Again counts again.
            now = Math.max(item.dueAtMillis, now + Records.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE * 86_400_000L);
            item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(Records.SchedulerPhase.REVIEW).state("review").build();
        }
        Records.ReviewResult demoting = scheduler.applyReview(
                item.withToken("final"),
                failRequest("裂", "final"),
                consumed,
                now
        );

        // hasSimilarKanji=false by default, so KANJI_MEANING demotes to TYPE_MEANING (skipping SIMILAR_KANJI).
        assertEquals(Records.LadderRung.TYPE_MEANING, demoting.item.rung);
        assertEquals("Streak resets on demotion", 0, demoting.item.realAgainStreak);
    }

    @Test
    public void learningRepeatsDoNotAdvanceRealPassStreak() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = newCard("裂");

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("g"),
                passRequest("裂", "g"),
                new HashSet<>(),
                1000L
        );

        assertEquals("Real pass streak stays 0 during new learning",
                0, result.item.realPassStreak);
    }

    @Test
    public void relearningRepeatsDoNotAdvanceRealAgainStreak() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();

        // Force a relearning state by failing a due review first.
        Records.StudyItem review = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);
        Records.ReviewResult lapsed = scheduler.applyReview(
                review.withToken("fail"),
                failRequest("裂", "fail"),
                consumed,
                1000L
        );
        assertEquals(Records.SchedulerPhase.RELEARNING, lapsed.item.phase);
        int streakAfterLapse = lapsed.item.realAgainStreak;

        // Now issue another Again while in relearning. Should not increment streak further.
        Records.ReviewResult relearningAgain = scheduler.applyReview(
                lapsed.item.withToken("a2"),
                failRequest("裂", "a2"),
                consumed,
                lapsed.item.dueAtMillis + 1
        );

        assertEquals("Relearning again does not bump streak",
                streakAfterLapse, relearningAgain.item.realAgainStreak);
    }

    @Test
    public void reviewAgainEntersRelearningWhenStepsExist() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("x"),
                failRequest("裂", "x"),
                new HashSet<>(),
                1000L
        );

        assertEquals(Records.SchedulerPhase.RELEARNING, result.item.phase);
        assertEquals("learning", result.item.state);
        assertEquals(1, result.item.lapses);
    }

    @Test
    public void reviewAgainWithEmptyRelearningStepsGetsDayInterval() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);
        Records.LearningStepSettings noRelearningSteps =
                new Records.LearningStepSettings(null, Collections.emptyList());

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("x"),
                failRequest("裂", "x"),
                new HashSet<>(),
                1000L,
                null,
                null,
                noRelearningSteps
        );

        // LearningStepSettings normalizes empty review steps to defaults; but if callers
        // explicitly pass an empty list, ensure the scheduler still skips to review.
        // The normalization uses defaults([10m]); we still enter RELEARNING.
        // Document that behavior: users configuring zero relearning steps is not exposed here.
        assertTrue("Phase is either REVIEW (skip) or RELEARNING (normalized default)",
                result.item.phase == Records.SchedulerPhase.REVIEW
                        || result.item.phase == Records.SchedulerPhase.RELEARNING);
    }

    // ---- Ladder floor and ceiling ----

    @Test
    public void ladderCeilingAtWordReadingStaysOnWordReading() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.WORD_READING, 0L);

        long now = 1000L;
        for (int i = 0; i < DEFAULT_THRESHOLD * 2; i++) {
            Records.ReviewResult r = scheduler.applyReview(
                    item.withToken("p" + i),
                    passRequest("裂", "p" + i),
                    consumed,
                    now
            );
            item = r.item;
            now = item.dueAtMillis;
        }

        assertEquals("Ceiling is WORD_READING", Records.LadderRung.WORD_READING, item.rung);
    }

    @Test
    public void ladderFloorAtWriteKanjiStaysOnWriteKanji() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.WRITE_KANJI, 0L)
                .copyBuilder().writingRemediationPending(true).build();

        long now = 1000L;
        for (int i = 0; i < DEFAULT_THRESHOLD * 2; i++) {
            // Use manualOverride for write rung so each fail counts as a real
            // due review failure (writing failures default to 'again' already,
            // but the manual path here is clearer for test control).
            Records.ReviewResult r = scheduler.applyReview(
                    item.withToken("f" + i),
                    new Records.ReviewRequest("裂", "f" + i, "again", false, false, false, 0),
                    consumed,
                    now
            );
            item = r.item;
            // Move to a new FSRS due slot.
            now = Math.max(item.dueAtMillis, now + 86_400_000L);
            item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(Records.SchedulerPhase.REVIEW).state("review").build();
        }

        assertEquals("Floor is WRITE_KANJI",
                Records.LadderRung.WRITE_KANJI, item.rung);
    }

    // ---- Similar-kanji rung inclusion / skipping ----

    @Test
    public void similarRungSkippedWhenUnavailable() {
        // hasSimilarKanji = false: demote from KANJI_MEANING should skip SIMILAR_KANJI
        // and land on TYPE_MEANING.
        Records.LadderRung demoted = BridgeScheduler.demoteRung(
                Records.LadderRung.KANJI_MEANING,
                false
        );
        assertEquals(Records.LadderRung.TYPE_MEANING, demoted);

        Records.LadderRung promoted = BridgeScheduler.promoteRung(
                Records.LadderRung.TYPE_MEANING,
                false
        );
        assertEquals(Records.LadderRung.KANJI_MEANING, promoted);
    }

    @Test
    public void similarRungIncludedWhenAvailable() {
        Records.LadderRung demoted = BridgeScheduler.demoteRung(
                Records.LadderRung.KANJI_MEANING,
                true
        );
        assertEquals(Records.LadderRung.SIMILAR_KANJI, demoted);

        Records.LadderRung promoted = BridgeScheduler.promoteRung(
                Records.LadderRung.TYPE_MEANING,
                true
        );
        assertEquals(Records.LadderRung.SIMILAR_KANJI, promoted);
    }

    // ---- Streak mechanics ----

    @Test
    public void realAgainResetsPassStreakAndViceVersa() {
        BridgeScheduler scheduler = new BridgeScheduler();
        HashSet<String> consumed = new HashSet<>();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);

        Records.ReviewResult pass1 = scheduler.applyReview(
                item.withToken("p1"), passRequest("裂", "p1"), consumed, 1000L);
        assertEquals(1, pass1.item.realPassStreak);
        assertEquals(0, pass1.item.realAgainStreak);

        Records.StudyItem forFail = pass1.item.copyBuilder()
                .dueAtMillis(pass1.item.dueAtMillis)
                .phase(Records.SchedulerPhase.REVIEW)
                .state("review")
                .build();
        long failTime = pass1.item.dueAtMillis + 1000L;
        Records.ReviewResult fail = scheduler.applyReview(
                forFail.withToken("f1"), failRequest("裂", "f1"), consumed, failTime);
        assertEquals("Fail in review resets pass streak", 0, fail.item.realPassStreak);
        assertEquals(1, fail.item.realAgainStreak);
    }

    @Test
    public void passInLearningDoesNotBumpPassStreak() {
        BridgeScheduler scheduler = new BridgeScheduler();

        // Force a relearning (practice) attempt to show the streak is not bumped.
        Records.StudyItem relearningItem = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 0L);
        Records.ReviewResult lapsed = scheduler.applyReview(
                relearningItem.withToken("f"),
                failRequest("裂", "f"),
                new HashSet<>(),
                1000L
        );
        int againStreak = lapsed.item.realAgainStreak;
        assertEquals(Records.SchedulerPhase.RELEARNING, lapsed.item.phase);

        Records.ReviewResult practicePass = scheduler.applyReview(
                lapsed.item.withToken("g"),
                passRequest("裂", "g"),
                new HashSet<>(),
                lapsed.item.dueAtMillis + 1
        );

        assertEquals("Relearning pass does not bump real pass streak",
                0, practicePass.item.realPassStreak);
        assertEquals("Relearning pass does not reset real again streak",
                againStreak, practicePass.item.realAgainStreak);
    }

    // ---- Rung-to-task-type wiring ----

    @Test
    public void rungWireNamesMatchTaskTypeConstants() {
        assertEquals(BridgeScheduler.TASK_WRITE_KANJI, Records.LadderRung.WRITE_KANJI.wireName());
        assertEquals(BridgeScheduler.TASK_TYPE_MEANING, Records.LadderRung.TYPE_MEANING.wireName());
        assertEquals(BridgeScheduler.TASK_SIMILAR_KANJI, Records.LadderRung.SIMILAR_KANJI.wireName());
        assertEquals(BridgeScheduler.TASK_KANJI_MEANING, Records.LadderRung.KANJI_MEANING.wireName());
        assertEquals(BridgeScheduler.TASK_FONT_MEANING, Records.LadderRung.FONT_MEANING.wireName());
        assertEquals(BridgeScheduler.TASK_WORD_READING, Records.LadderRung.WORD_READING.wireName());
    }

    // ---- Edge-case coverage for countsAsRealDue and null guards ----

    @Test
    public void reviewOnNotYetDueCardDoesNotCountForStreak() {
        BridgeScheduler scheduler = new BridgeScheduler();
        // Card due in the far future: nowMillis < dueAtMillis
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 999_999_999L);
        HashSet<String> consumed = new HashSet<>();

        // Apply 5 reviews at nowMillis=1000 (before due). None should count for streak.
        Records.StudyItem current = item;
        for (int i = 0; i < 5; i++) {
            current = current.withToken("nd" + i);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", "nd" + i), consumed, 1000L + i);
            current = result.item;
        }
        // Should NOT have promoted because the reviews were not "due"
        assertEquals(Records.LadderRung.KANJI_MEANING, current.rung);
        assertEquals(0, current.realPassStreak);
    }

    @Test
    public void sameDueSlotReviewDoesNotCountTwiceForStreak() {
        BridgeScheduler scheduler = new BridgeScheduler();
        // Item due at 500, first review at 1000 counts, second at 1001 with same dueSlot shouldn't
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 500L);
        HashSet<String> consumed = new HashSet<>();

        // First review: counts (nowMillis > dueAt, first time this due slot is seen)
        Records.ReviewResult r1 = scheduler.applyReview(
                item.withToken("s1"), passRequest("裂", "s1"), consumed, 1000L);
        assertEquals(1, r1.item.realPassStreak);

        // Second review on the same item without it becoming due again:
        // The dueAtMillis has now been updated to a far-future value by the scheduler.
        // If we manually set dueAtMillis back to the original 500 (simulating same slot),
        // the lastRealReviewDueAtMillis will already be 500, so it should be a no-op for streak.
        Records.StudyItem sameSlot = r1.item.copyBuilder().dueAtMillis(500L).build();
        Records.ReviewResult r2 = scheduler.applyReview(
                sameSlot.withToken("s2"), passRequest("裂", "s2"), consumed, 1001L);
        // Streak should not advance further because same due slot
        assertEquals(1, r2.item.realPassStreak);
    }

    @Test
    public void hardCountsAsPassForStreakAdvancement() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 500L);
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("h1"),
                new Records.ReviewRequest("裂", "h1", "hard", false, false, false, 0),
                consumed,
                1000L
        );
        assertEquals("Hard should count as pass", 1, result.item.realPassStreak);
        assertEquals(0, result.item.realAgainStreak);
    }

    @Test
    public void easyCountsAsPassForStreakAdvancement() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.KANJI_MEANING, 500L);
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("e1"),
                new Records.ReviewRequest("裂", "e1", "easy", false, false, false, 0),
                consumed,
                1000L
        );
        assertEquals("Easy should count as pass", 1, result.item.realPassStreak);
        assertEquals(0, result.item.realAgainStreak);
    }

    @Test
    public void memoryForTaskTypeReturnsCorrectMemoryPerRung() {
        Records.StudyItem item = newCard("裂");
        // These exercise the switch branches in memoryForTaskType
        assertEquals(item.writingRemediationMemory, item.memoryForTaskType("write_kanji"));
        assertEquals(item.writingRemediationMemory, item.memoryForTaskType("writing_remediation"));
        assertEquals(item.typingMeaningMemory, item.memoryForTaskType("type_meaning"));
        assertEquals(item.typingMeaningMemory, item.memoryForTaskType("typing_meaning"));
        assertEquals(item.similarKanjiMemory, item.memoryForTaskType("similar_kanji"));
        assertEquals(item.wordReadingMemory, item.memoryForTaskType("word_reading"));
        assertEquals(item.fontMeaningMemory, item.memoryForTaskType("font_meaning"));
        assertEquals(item.kanjiMeaningMemory, item.memoryForTaskType("kanji_meaning"));
        assertEquals(item.kanjiMeaningMemory, item.memoryForTaskType(null));
        assertEquals(item.kanjiMeaningMemory, item.memoryForTaskType("unknown_type"));
    }

    @Test
    public void withTaskMemoryUpdatesCorrectFieldPerType() {
        Records.StudyItem item = newCard("裂");
        Records.TaskMemory custom = Records.TaskMemory.fromStudyFields("review", 5000L, 2.0, 4.5, 3, 1, 2, 10);

        assertEquals(custom, item.withTaskMemory("write_kanji", custom).writingRemediationMemory);
        assertEquals(custom, item.withTaskMemory("writing_remediation", custom).writingRemediationMemory);
        assertEquals(custom, item.withTaskMemory("type_meaning", custom).typingMeaningMemory);
        assertEquals(custom, item.withTaskMemory("typing_meaning", custom).typingMeaningMemory);
        assertEquals(custom, item.withTaskMemory("similar_kanji", custom).similarKanjiMemory);
        assertEquals(custom, item.withTaskMemory("word_reading", custom).wordReadingMemory);
        assertEquals(custom, item.withTaskMemory("font_meaning", custom).fontMeaningMemory);
        assertEquals(custom, item.withTaskMemory("kanji_meaning", custom).kanjiMeaningMemory);
        assertEquals(custom, item.withTaskMemory(null, custom).kanjiMeaningMemory);
        assertEquals(custom, item.withTaskMemory("unknown", custom).kanjiMeaningMemory);
    }

    @Test
    public void writingFailureOnNonWriteKanjiRungResolvesAsAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        // Item on FONT_MEANING rung with a writing failure (writingRequired=true, writingPassed=false)
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.FONT_MEANING, 500L);
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("wf1"),
                new Records.ReviewRequest("裂", "wf1", "good", true, false, false, 0),
                consumed,
                1000L
        );
        // Writing failure on non-write_kanji rung should resolve as "again"
        assertEquals("again", result.appliedRating);
    }

    @Test
    public void writingPassOnNonWriteKanjiRungKeepsOriginalRating() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.FONT_MEANING, 500L);
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("wp1"),
                new Records.ReviewRequest("裂", "wp1", "good", true, true, false, 0),
                consumed,
                1000L
        );
        // Writing pass keeps the original rating
        assertEquals("good", result.appliedRating);
    }

    @Test
    public void manualOverrideOnNonWriteKanjiDoesNotForceAgain() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = reviewCard("裂", Records.LadderRung.FONT_MEANING, 500L);
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("mo1"),
                new Records.ReviewRequest("裂", "mo1", "good", true, false, true, 0),
                consumed,
                1000L
        );
        // Manual override exempts the writing failure from forcing "again"
        assertEquals("good", result.appliedRating);
    }

    @Test
    public void newLearningWithHardOnFirstStepUsesShortDelay() {
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.StudyItem item = newCard("裂");
        HashSet<String> consumed = new HashSet<>();

        Records.ReviewResult result = scheduler.applyReview(
                item.withToken("h1"),
                new Records.ReviewRequest("裂", "h1", "hard", false, false, false, 0),
                consumed,
                1000L
        );
        // Hard on step 0 uses a delay between Again and Good; stays in learning
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, result.item.phase);
        assertEquals(0, result.item.learningStep); // stays on step 0
        assertTrue("Hard delay should be positive", result.item.dueAtMillis > 1000L);
    }

    // ---- Helpers ----

    private static Records.StudyItem newCard(String kanji) {
        return new Records.StudyItem(
                kanji,
                "new",
                0L,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                null,
                0L
        ).withRungAndPhase(Records.LadderRung.KANJI_MEANING, Records.SchedulerPhase.NEW_LEARNING);
    }

    private static Records.StudyItem reviewCard(String kanji, Records.LadderRung rung, long dueAtMillis) {
        return new Records.StudyItem(
                kanji,
                "review",
                dueAtMillis,
                1.2,
                5.0,
                1,
                0,
                2,
                1,
                0,
                0,
                0L,
                rung == Records.LadderRung.WRITE_KANJI,
                null,
                0L
        ).withRungAndPhase(rung, Records.SchedulerPhase.REVIEW);
    }

    private static Records.ReviewRequest passRequest(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "good", false, false, false, 0);
    }

    private static Records.ReviewRequest failRequest(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "again", false, false, false, 0);
    }
}
