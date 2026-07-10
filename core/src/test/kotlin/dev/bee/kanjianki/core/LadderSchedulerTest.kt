package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import java.util.HashSet

/**
 * Tests for the Anki-exact ladder scheduler. Covers:
 *   - New-learning phase: Again loops, Good advances, Hard behavior, Easy graduates.
 *   - Review phase: due-review streaks promote / demote the rung.
 *   - Relearning phase: entered after a review lapse; practice-only.
 *   - Similar-kanji rung skipping based on {@link RecordsStudyModels.StudyItem#hasSimilarKanji}.
 *   - Ladder floor and ceiling behavior.
 */
class LadderSchedulerTest {

    private val DEFAULT_THRESHOLD = RecordsBase.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE

    // ---- New learning phase (Anki-exact semantics) ----

    @Test
    fun newCardAgainLoopsInLearningForever() {
        val scheduler = BridgeScheduler();
        var item = newCard("裂")
        val consumed = HashSet<String>();

        for (i in 0 until 10) {
            val result = scheduler.applyReview(
                    item.withToken("t" + i),
                    RecordsSchedulerModels.ReviewRequest("裂", "t" + i, "again", false, false, false, 0),
                    consumed,
                    1000L + i
            );
            item = result.item;
        }

        assertEquals("Phase stays in new_learning after 10 Agains",
                RecordsBase.SchedulerPhase.NEW_LEARNING, item.phase);
        assertEquals("Rung stays on starting rung",
                RecordsBase.LadderRung.KANJI_MEANING, item.rung);
        assertEquals("Learning step stays at 0", 0, item.learningStep);
        assertEquals("Real pass streak unchanged", 0, item.realPassStreak);
        assertEquals("Real again streak unchanged in learning", 0, item.realAgainStreak);
        assertEquals("No lapses recorded during learning", 0, item.lapses);
    }

    @Test
    fun newCardGoodAdvancesThroughLearningSteps() {
        val scheduler = BridgeScheduler();
        var item = newCard("裂")
        // Default new steps: [1, 10]
        assertEquals(0, item.learningStep);

        val first = scheduler.applyReview(
                item.withToken("t1"),
                RecordsSchedulerModels.ReviewRequest("裂", "t1", "good", false, false, false, 0),
                HashSet<String>(),
                1000L
        );
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, first.item.phase);
        assertEquals(1, first.item.learningStep);
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, first.item.rung);
    }

    @Test
    fun newCardGoodPastLastStepGraduatesToReview() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        // Default new steps: [1, 10]. Two Goods should graduate.
        val afterFirst = scheduler.applyReview(
                newCard("裂").withToken("g1"),
                RecordsSchedulerModels.ReviewRequest("裂", "g1", "good", false, false, false, 0),
                consumed,
                1000L
        );
        val afterSecond = scheduler.applyReview(
                afterFirst.item.withToken("g2"),
                RecordsSchedulerModels.ReviewRequest("裂", "g2", "good", false, false, false, 0),
                consumed,
                2000L
        );

        assertEquals(RecordsBase.SchedulerPhase.REVIEW, afterSecond.item.phase);
        assertEquals("review", afterSecond.item.state);
        assertTrue("Due far in the future after graduation",
                afterSecond.item.dueAtMillis > 2000L + 3600_000L);
    }

    @Test
    fun newCardEasyGraduatesImmediately() {
        val scheduler = BridgeScheduler();

        val result = scheduler.applyReview(
                newCard("裂").withToken("e"),
                RecordsSchedulerModels.ReviewRequest("裂", "e", "easy", false, false, false, 0),
                HashSet<String>(),
                1000L
        );

        assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase);
        assertEquals("review", result.item.state);
    }

    @Test
    fun newCardHardOnFirstStepUsesDelayBetweenAgainAndGood() {
        val scheduler = BridgeScheduler();

        val result = scheduler.applyReview(
                newCard("裂").withToken("h"),
                RecordsSchedulerModels.ReviewRequest("裂", "h", "hard", false, false, false, 0),
                HashSet<String>(),
                1000L
        );

        // Default new steps [1m, 10m]: Hard on first step schedules max(1m, 5.5m) = 5.5m.
        val expectedMin = 1000L + 3L * 60_000L;
        val expectedMax = 1000L + 11L * 60_000L;
        assertTrue("Hard on first step delay should sit between Again and Good",
                result.item.dueAtMillis >= expectedMin && result.item.dueAtMillis <= expectedMax);
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, result.item.phase);
        assertEquals(0, result.item.learningStep);
    }

    @Test
    fun newCardHardOnLaterStepRepeatsStep() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();

        val advanced = scheduler.applyReview(
                newCard("裂").withToken("g"),
                RecordsSchedulerModels.ReviewRequest("裂", "g", "good", false, false, false, 0),
                consumed,
                1000L
        );
        val hard = scheduler.applyReview(
                advanced.item.withToken("h2"),
                RecordsSchedulerModels.ReviewRequest("裂", "h2", "hard", false, false, false, 0),
                consumed,
                2000L
        );

        // We were at step 1 (10m). Hard should repeat the same step => due at 10m from now.
        val expected = 2000L + 10L * 60_000L;
        val delta = Math.abs(hard.item.dueAtMillis - expected);
        assertTrue("Hard on later step repeats current step delay; delta=" + delta,
                delta < 60_000L);
        assertEquals(1, hard.item.learningStep);
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, hard.item.phase);
    }

    // ---- Review phase and ladder streaks ----

    @Test
    fun realDuePassPromotesWhenFsrsIntervalExceedsThreshold() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val promoting = applyQualifyingPasses(scheduler, item, 2);

        assertEquals(RecordsBase.LadderRung.FONT_MEANING, promoting.item.rung);
        assertEquals("Streak resets on promotion", 0, promoting.item.realPassStreak);
    }

    @Test
    fun dueReviewPromotionSkipsDisabledRung() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.FONT_MEANING, false);

        val result = applyQualifyingPasses(
                scheduler, item, 2, RecordsSyncModels.Settings.kikuDefaults(), ladder);

        assertEquals(RecordsBase.LadderRung.WORD_READING, result.item.rung);
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase);
        assertEquals(0, result.item.realPassStreak);
        assertEquals(0, result.item.realAgainStreak);
    }

    @Test
    fun realDuePassDoesNotPromoteAtOrBelowFsrsIntervalThreshold() {
        for (intervalDays in longArrayOf(20L, 21L)) {
            val scheduler = schedulerWithReviewIntervalDays(intervalDays);
            var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
            val result = scheduler.applyReview(
                    item.withToken("p" + intervalDays),
                    passRequest("裂", "p" + intervalDays),
                    HashSet<String>(),
                    1000L
            );

            assertEquals(RecordsBase.LadderRung.KANJI_MEANING, result.item.rung);
            assertEquals(1, result.item.realPassStreak);
        }
    }

    @Test
    fun hardGoodAndEasyUseFsrsIntervalPromotionRule() {
        for (rating in arrayOf("hard", "good", "easy")) {
            val scheduler = schedulerWithReviewIntervalDays(22);
            var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
            val consumed = HashSet<String>();
            var result: RecordsSchedulerModels.ReviewResult? = null
            // Two qualifying real-due passes clear the default min-pass gate.
            for (i in 0 until 2) {
                val due = i.toLong() * 100L * BridgeScheduler.DAY
                item = item.copyBuilder().dueAtMillis(due)
                        .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()
                result = scheduler.applyReview(
                        item.withToken(rating + i),
                        RecordsSchedulerModels.ReviewRequest("裂", rating + i, rating, false, false, false, 0),
                        consumed,
                        due + 1000L
                );
                item = result.item
            }

            assertEquals("Rating " + rating + " should promote by interval",
                    RecordsBase.LadderRung.FONT_MEANING, result!!.item.rung);
        }
    }

    @Test
    fun threeRealDueAgainsDemoteOneRung() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)

        var now = 1000L
        for (i in 0 until DEFAULT_THRESHOLD - 1) {
            val r = scheduler.applyReview(
                    item.withToken("a" + i),
                    failRequest("裂", "a" + i),
                    consumed,
                    now
            );
            item = r.item;
            assertEquals("Rung holds before threshold",
                    RecordsBase.LadderRung.KANJI_MEANING, item.rung);
            // Move 'now' out to a new FSRS slot so the next Again counts again.
            now = Math.max(item.dueAtMillis, now + RecordsBase.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE * 86_400_000L);
            item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        }
        val demoting = scheduler.applyReview(
                item.withToken("final"),
                failRequest("裂", "final"),
                consumed,
                now
        );

        // hasSimilarKanji=false by default, so KANJI_MEANING demotes to MEANING_KANJI.
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, demoting.item.rung);
        assertEquals("Streak resets on demotion", 0, demoting.item.realAgainStreak);
    }

    @Test
    fun learningRepeatsDoNotAdvanceRealPassStreak() {
        val scheduler = BridgeScheduler();
        var item = newCard("裂")

        val result = scheduler.applyReview(
                item.withToken("g"),
                passRequest("裂", "g"),
                HashSet<String>(),
                1000L
        );

        assertEquals("Real pass streak stays 0 during new learning",
                0, result.item.realPassStreak);
    }

    @Test
    fun relearningRepeatsDoNotAdvanceRealAgainStreak() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();

        // Force a relearning state by failing a due review first.
        val review = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val lapsed = scheduler.applyReview(
                review.withToken("fail"),
                failRequest("裂", "fail"),
                consumed,
                1000L
        );
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, lapsed.item.phase);
        val streakAfterLapse = lapsed.item.realAgainStreak;

        // Now issue another Again while in relearning. Should not increment streak further.
        val relearningAgain = scheduler.applyReview(
                lapsed.item.withToken("a2"),
                failRequest("裂", "a2"),
                consumed,
                lapsed.item.dueAtMillis + 1
        );

        assertEquals("Relearning again does not bump streak",
                streakAfterLapse, relearningAgain.item.realAgainStreak);
    }

    @Test
    fun reviewAgainEntersRelearningWhenStepsExist() {
        val scheduler = BridgeScheduler();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val result = scheduler.applyReview(
                item.withToken("x"),
                failRequest("裂", "x"),
                HashSet<String>(),
                1000L
        );

        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, result.item.phase);
        assertEquals("learning", result.item.state);
        assertEquals(1, result.item.lapses);
    }

    @Test
    fun customRelearningStepsControlLapseDelayAndPracticeGraduation() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val customSteps = RecordsSchedulerModels.LearningStepSettings(
                listOf(1, 10),
                listOf(7, 30));

        val lapsed = scheduler.applyReview(
                item.withToken("custom-fail"),
                failRequest("裂", "custom-fail"),
                consumed,
                1000L,
                null,
                null,
                customSteps
        );

        assertEquals("A due review Again enters relearning",
                RecordsBase.SchedulerPhase.RELEARNING, lapsed.item.phase);
        assertEquals("learning", lapsed.item.state);
        assertEquals("Custom first relearning step controls lapse delay",
                1000L + 7L * 60_000L, lapsed.item.dueAtMillis);
        assertEquals(0, lapsed.item.learningStep);
        assertEquals(0, lapsed.item.matureIntervalDays);
        assertEquals(1, lapsed.item.lapses);

        val firstPracticeNow = lapsed.item.dueAtMillis + 1;
        val afterFirstPractice = scheduler.applyReview(
                lapsed.item.withToken("custom-good-1"),
                passRequest("裂", "custom-good-1"),
                consumed,
                firstPracticeNow,
                null,
                null,
                customSteps
        );

        assertEquals("Good advances to the second custom relearning step",
                RecordsBase.SchedulerPhase.RELEARNING, afterFirstPractice.item.phase);
        assertEquals(1, afterFirstPractice.item.learningStep);
        assertEquals(firstPracticeNow + 30L * 60_000L, afterFirstPractice.item.dueAtMillis);

        val secondPracticeNow = afterFirstPractice.item.dueAtMillis + 1;
        val afterSecondPractice = scheduler.applyReview(
                afterFirstPractice.item.withToken("custom-good-2"),
                passRequest("裂", "custom-good-2"),
                consumed,
                secondPracticeNow,
                null,
                null,
                customSteps
        );

        assertEquals("Good past the last custom relearning step graduates back to review",
                RecordsBase.SchedulerPhase.REVIEW, afterSecondPractice.item.phase);
        assertEquals("review", afterSecondPractice.item.state);
        assertEquals(0, afterSecondPractice.item.learningStep);
        assertTrue(afterSecondPractice.item.dueAtMillis > secondPracticeNow);
    }

    @Test
    fun reviewAgainWithEmptyRelearningStepsUsesPostLapseInterval() {
        val scheduler = BridgeScheduler();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val noRelearningSteps =
                RecordsSchedulerModels.LearningStepSettings(null, emptyList<Int?>())

        val result = scheduler.applyReview(
                item.withToken("x"),
                failRequest("裂", "x"),
                HashSet<String>(),
                1000L,
                null,
                null,
                noRelearningSteps
        );

        assertEquals("Empty relearning steps skip practice and return to review",
                RecordsBase.SchedulerPhase.REVIEW, result.item.phase);
        assertEquals("review", result.item.state);
        assertEquals("Post-lapse interval is one day", 1000L + BridgeScheduler.DAY, result.item.dueAtMillis);
        assertEquals(1, result.item.matureIntervalDays);
        assertEquals(1, result.item.lapses);
    }

    // ---- Ladder floor and ceiling ----

    @Test
    fun ladderCeilingAtWordReadingStaysOnWordReading() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.WORD_READING, 0L)

        var now = 1000L
        for (i in 0 until DEFAULT_THRESHOLD * 2) {
            val r = scheduler.applyReview(
                    item.withToken("p" + i),
                    passRequest("裂", "p" + i),
                    consumed,
                    now
            );
            item = r.item;
            now = item.dueAtMillis;
        }

        assertEquals("Ceiling is WORD_READING", RecordsBase.LadderRung.WORD_READING, item.rung);
    }

    @Test
    fun ladderFloorAtWriteKanjiStaysOnWriteKanji() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L)
                .copyBuilder().writingRemediationPending(true).build()

        var now = 1000L
        for (i in 0 until DEFAULT_THRESHOLD * 2) {
            // Use manualOverride for write rung so each fail counts as a real
            // due review failure (writing failures default to 'again' already,
            // but the manual path here is clearer for test control).
            val r = scheduler.applyReview(
                    item.withToken("f" + i),
                    RecordsSchedulerModels.ReviewRequest("裂", "f" + i, "again", false, false, false, 0),
                    consumed,
                    now
            );
            item = r.item;
            // Move to a new FSRS due slot.
            now = Math.max(item.dueAtMillis, now + 86_400_000L);
            item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        }

        assertEquals("Floor is WRITE_KANJI",
                RecordsBase.LadderRung.WRITE_KANJI, item.rung);
    }

    // ---- Similar-kanji rung inclusion / skipping ----

    @Test
    fun similarRungSkippedWhenUnavailable() {
        // hasSimilarKanji=false: movements that cross SIMILAR_KANJI must skip
        // over it in both directions without pausing.
        val demoted = BridgeScheduler.demoteRung(
                RecordsBase.LadderRung.TYPE_MEANING,
                RecordsBase.RungAvailability.none()
        );
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, demoted);

        val promoted = BridgeScheduler.promoteRung(
                RecordsBase.LadderRung.WRITE_KANJI,
                RecordsBase.RungAvailability.none()
        );
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, promoted);

        // Adjacent moves that never touch SIMILAR_KANJI are unaffected.
        assertEquals(
                RecordsBase.LadderRung.MEANING_KANJI,
                BridgeScheduler.demoteRung(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.RungAvailability.none())
        );
        assertEquals(
                RecordsBase.LadderRung.MEANING_KANJI,
                BridgeScheduler.promoteRung(RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.RungAvailability.none())
        );
    }

    @Test
    fun similarRungIncludedWhenAvailable() {
        // New default order (Goal 65): similar_kanji sits between meaning_kanji
        // (below) and kanji_meaning (above).
        val demoted = BridgeScheduler.demoteRung(
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.RungAvailability.of(true)
        );
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, demoted);

        val promoted = BridgeScheduler.promoteRung(
                RecordsBase.LadderRung.MEANING_KANJI,
                RecordsBase.RungAvailability.of(true)
        );
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, promoted);
    }

    @Test
    fun kanjiReadingRungSkippedWhenUnavailable() {
        // hasKanjiReading=false: word_reading demotes across the content-less
        // kanji_reading rung straight to font_meaning, and font_meaning promotes
        // straight to word_reading.
        val none = RecordsBase.RungAvailability.none()
        assertEquals(
            RecordsBase.LadderRung.FONT_MEANING,
            BridgeScheduler.demoteRung(RecordsBase.LadderRung.WORD_READING, none),
        )
        assertEquals(
            RecordsBase.LadderRung.WORD_READING,
            BridgeScheduler.promoteRung(RecordsBase.LadderRung.FONT_MEANING, none),
        )
    }

    @Test
    fun kanjiReadingRungIncludedWhenAvailable() {
        // hasKanjiReading=true: word_reading demotes into kanji_reading and
        // font_meaning promotes into it.
        val withReading = RecordsBase.RungAvailability.of(false, true)
        assertEquals(
            RecordsBase.LadderRung.KANJI_READING,
            BridgeScheduler.demoteRung(RecordsBase.LadderRung.WORD_READING, withReading),
        )
        assertEquals(
            RecordsBase.LadderRung.KANJI_READING,
            BridgeScheduler.promoteRung(RecordsBase.LadderRung.FONT_MEANING, withReading),
        )
    }

    @Test
    fun readingKanjiRungSkippedWhenUnavailable() {
        // hasReadingKanji=false: reading_kanji sits between meaning_kanji and
        // similar_kanji; movements crossing it skip over it.
        val none = RecordsBase.RungAvailability.none()
        assertEquals(
            RecordsBase.LadderRung.MEANING_KANJI,
            BridgeScheduler.demoteRung(RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.RungAvailability.of(true)),
        )
        // With reading data available, a demotion from similar_kanji lands on it.
        assertEquals(
            RecordsBase.LadderRung.READING_KANJI,
            BridgeScheduler.demoteRung(
                RecordsBase.LadderRung.SIMILAR_KANJI,
                RecordsBase.RungAvailability.of(true, false, true),
            ),
        )
    }

    @Test
    fun demotionChainsAcrossBothConditionalRungsWhenNeitherAvailable() {
        // Goal 79: a kanji_meaning demotion with neither similar_kanji nor
        // reading_kanji available crosses BOTH conditional rungs in one move,
        // landing on meaning_kanji, and records both unavailable reason codes.
        val scheduler = BridgeScheduler()
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
            .copyBuilder()
            .hasSimilarKanji(false)
            .hasKanjiReading(false)
            .hasReadingKanji(false)
            .realAgainStreak(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK - 1)
            .build()
            .withToken("chain")
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, failRequest("裂", "chain"), HashSet(), 1_000L).build(),
        )
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, traced.result.item.rung)
        val reasons = traced.trace.transition!!.reasonCodes
        assertTrue(reasons.contains("similar_kanji_unavailable"))
        assertTrue(reasons.contains("reading_kanji_unavailable"))
    }

    // ---- Streak mechanics ----

    @Test
    fun realAgainResetsPassStreakAndViceVersa() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val pass1 = scheduler.applyReview(
                item.withToken("p1"), passRequest("裂", "p1"), consumed, 1000L);
        assertEquals(1, pass1.item.realPassStreak);
        assertEquals(0, pass1.item.realAgainStreak);

        val forFail = pass1.item.copyBuilder()
                .dueAtMillis(pass1.item.dueAtMillis)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .state("review")
                .build();
        val failTime = pass1.item.dueAtMillis + 1000L;
        val fail = scheduler.applyReview(
                forFail.withToken("f1"), failRequest("裂", "f1"), consumed, failTime);
        assertEquals("Fail in review resets pass streak", 0, fail.item.realPassStreak);
        assertEquals(1, fail.item.realAgainStreak);
    }

    @Test
    fun passInLearningDoesNotBumpPassStreak() {
        val scheduler = BridgeScheduler();

        // Force a relearning (practice) attempt to show the streak is not bumped.
        val relearningItem = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val lapsed = scheduler.applyReview(
                relearningItem.withToken("f"),
                failRequest("裂", "f"),
                HashSet<String>(),
                1000L
        );
        val againStreak = lapsed.item.realAgainStreak;
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, lapsed.item.phase);

        val practicePass = scheduler.applyReview(
                lapsed.item.withToken("g"),
                passRequest("裂", "g"),
                HashSet<String>(),
                lapsed.item.dueAtMillis + 1
        );

        assertEquals("Relearning pass does not bump real pass streak",
                0, practicePass.item.realPassStreak);
        assertEquals("Relearning pass does not reset real again streak",
                againStreak, practicePass.item.realAgainStreak);
    }

    // ---- Rung-to-task-type wiring ----

    @Test
    fun rungWireNamesMatchTaskTypeConstants() {
        assertEquals(BridgeScheduler.TASK_WRITE_KANJI, RecordsBase.LadderRung.WRITE_KANJI.wireName());
        assertEquals(BridgeScheduler.TASK_TYPE_MEANING, RecordsBase.LadderRung.TYPE_MEANING.wireName());
        assertEquals(BridgeScheduler.TASK_SIMILAR_KANJI, RecordsBase.LadderRung.SIMILAR_KANJI.wireName());
        assertEquals(BridgeScheduler.TASK_MEANING_KANJI, RecordsBase.LadderRung.MEANING_KANJI.wireName());
        assertEquals(BridgeScheduler.TASK_KANJI_MEANING, RecordsBase.LadderRung.KANJI_MEANING.wireName());
        assertEquals(BridgeScheduler.TASK_FONT_MEANING, RecordsBase.LadderRung.FONT_MEANING.wireName());
        assertEquals(BridgeScheduler.TASK_WORD_READING, RecordsBase.LadderRung.WORD_READING.wireName());
    }

    @Test
    fun ladderRungFromWireNameRoundTripsAllValues() {
        for (rung in RecordsBase.LadderRung.values()) {
            assertEquals(rung, RecordsBase.LadderRung.fromWireName(rung.wireName()));
        }
        // Unknown or null defaults to KANJI_MEANING
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.LadderRung.fromWireName(null));
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.LadderRung.fromWireName("unknown"));
    }

    @Test
    fun schedulerPhaseFromWireNameRoundTripsAllValues() {
        for (phase in RecordsBase.SchedulerPhase.values()) {
            assertEquals(phase, RecordsBase.SchedulerPhase.fromWireName(phase.wireName()));
        }
        // Unknown or null defaults to NEW_LEARNING
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, RecordsBase.SchedulerPhase.fromWireName(null));
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, RecordsBase.SchedulerPhase.fromWireName("unknown"));
    }

    // ---- Edge cases for countsAsRealDue and null guards ----

    @Test
    fun reviewOnNotYetDueCardDoesNotCountForStreak() {
        val scheduler = BridgeScheduler();
        // Card due in the far future: nowMillis < dueAtMillis
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 999_999_999L);
        val consumed = HashSet<String>();

        // Apply 5 reviews at nowMillis=1000 (before due). None should count for streak.
        var current = item
        for (i in 0 until 5) {
            current = current.withToken("nd" + i);
            val result = scheduler.applyReview(
                    current, passRequest("裂", "nd" + i), consumed, 1000L + i);
            current = result.item;
        }
        // Should NOT have promoted because the reviews were not "due"
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, current.rung);
        assertEquals(0, current.realPassStreak);
    }

    @Test
    fun sameDueSlotReviewDoesNotCountTwiceForStreak() {
        val scheduler = BridgeScheduler();
        // Item due at 500, first review at 1000 counts, second at 1001 with same dueSlot shouldn't
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 500L);
        val consumed = HashSet<String>();

        // First review: counts (nowMillis > dueAt, first time this due slot is seen)
        val r1 = scheduler.applyReview(
                item.withToken("s1"), passRequest("裂", "s1"), consumed, 1000L);
        assertEquals(1, r1.item.realPassStreak);

        // Second review on the same item without it becoming due again:
        // The dueAtMillis has now been updated to a far-future value by the scheduler.
        // If we manually set dueAtMillis back to the original 500 (simulating same slot),
        // the lastRealReviewDueAtMillis will already be 500, so it should be a no-op for streak.
        val sameSlot = r1.item.copyBuilder().dueAtMillis(500L).build();
        val r2 = scheduler.applyReview(
                sameSlot.withToken("s2"), passRequest("裂", "s2"), consumed, 1001L);
        // Streak should not advance further because same due slot
        assertEquals(1, r2.item.realPassStreak);
    }

    @Test
    fun hardCountsAsPassForStreakAdvancement() {
        val scheduler = BridgeScheduler();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 500L);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("h1"),
                RecordsSchedulerModels.ReviewRequest("裂", "h1", "hard", false, false, false, 0),
                consumed,
                1000L
        );
        assertEquals("Hard should count as pass", 1, result.item.realPassStreak);
        assertEquals(0, result.item.realAgainStreak);
    }

    @Test
    fun easyCountsAsPassForStreakAdvancement() {
        val scheduler = BridgeScheduler();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 500L);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("e1"),
                RecordsSchedulerModels.ReviewRequest("裂", "e1", "easy", false, false, false, 0),
                consumed,
                1000L
        );
        assertEquals("Easy should count as pass", 1, result.item.realPassStreak);
        assertEquals(0, result.item.realAgainStreak);
    }

    @Test
    fun memoryForTaskTypeReturnsCorrectMemoryPerRung() {
        var item = newCard("裂")
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
    fun withTaskMemoryUpdatesCorrectFieldPerType() {
        var item = newCard("裂")
        val custom = RecordsStudyModels.TaskMemory.fromStudyFields("review", 5000L, 2.0, 4.5, 3, 1, 2, 10);

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
    fun writingFailureOnNonWriteKanjiRungResolvesAsAgain() {
        val scheduler = BridgeScheduler();
        // Item on FONT_MEANING rung with a writing failure (writingRequired=true, writingPassed=false)
        var item = reviewCard("裂", RecordsBase.LadderRung.FONT_MEANING, 500L);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("wf1"),
                RecordsSchedulerModels.ReviewRequest("裂", "wf1", "good", true, false, false, 0),
                consumed,
                1000L
        );
        // Writing failure on non-write_kanji rung should resolve as "again"
        assertEquals("again", result.appliedRating);
    }

    @Test
    fun writingPassOnNonWriteKanjiRungKeepsOriginalRating() {
        val scheduler = BridgeScheduler();
        var item = reviewCard("裂", RecordsBase.LadderRung.FONT_MEANING, 500L);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("wp1"),
                RecordsSchedulerModels.ReviewRequest("裂", "wp1", "good", true, true, false, 0),
                consumed,
                1000L
        );
        // Writing pass keeps the original rating
        assertEquals("good", result.appliedRating);
    }

    @Test
    fun manualOverrideOnNonWriteKanjiDoesNotForceAgain() {
        val scheduler = BridgeScheduler();
        var item = reviewCard("裂", RecordsBase.LadderRung.FONT_MEANING, 500L);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("mo1"),
                RecordsSchedulerModels.ReviewRequest("裂", "mo1", "good", true, false, true, 0),
                consumed,
                1000L
        );
        // Manual override exempts the writing failure from forcing "again"
        assertEquals("good", result.appliedRating);
    }

    @Test
    fun newLearningWithHardOnFirstStepUsesShortDelay() {
        val scheduler = BridgeScheduler();
        var item = newCard("裂")
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("h1"),
                RecordsSchedulerModels.ReviewRequest("裂", "h1", "hard", false, false, false, 0),
                consumed,
                1000L
        );
        // Hard on step 0 uses a delay between Again and Good; stays in learning
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, result.item.phase);
        assertEquals(0, result.item.learningStep); // stays on step 0
        assertTrue("Hard delay should be positive", result.item.dueAtMillis > 1000L);
    }

    @Test
    fun hardInRelearningRepeatsCurrentStep() {
        val scheduler = BridgeScheduler();
        // Create a card in relearning phase at its first relearning step
        // (learningStep positional argument = 0).
        var item = RecordsStudyModels.StudyItem(
                "裂", "review", 500L, 1.2, 5.0, 1, 0, 0, 1, 0, 0, 0L, false, null, 0L
        ).withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.RELEARNING);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("rh1"),
                RecordsSchedulerModels.ReviewRequest("裂", "rh1", "hard", false, false, false, 0),
                consumed,
                1000L
        );
        // Hard in relearning stays in relearning
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, result.item.phase);
    }

    @Test
    fun hardWithStaleStepIndexPastConfiguredStepsGraduates() {
        val scheduler = BridgeScheduler();
        // The relearning steps shrank while this card sat mid-relearning, so
        // its step index (5) points past the last configured step. Anki
        // graduates such a card on Hard instead of trapping it in learning.
        var item = RecordsStudyModels.StudyItem(
                "裂", "review", 500L, 1.2, 5.0, 1, 0, 5, 1, 0, 0, 0L, false, null, 0L
        ).withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.RELEARNING);
        val consumed = HashSet<String>();

        val result = scheduler.applyReview(
                item.withToken("rh-stale"),
                RecordsSchedulerModels.ReviewRequest("裂", "rh-stale", "hard", false, false, false, 0),
                consumed,
                1000L
        );
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase);
        assertTrue("graduation should schedule a future due", result.item.dueAtMillis > 1000L);
    }

    @Test
    fun goodInRelearningAdvancesThroughSteps() {
        val scheduler = BridgeScheduler();
        // Create a card in relearning at step 0
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 500L)
                .withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.RELEARNING);
        val consumed = HashSet<String>();

        // Default relearning steps: [10]. One good should graduate.
        val result = scheduler.applyReview(
                item.withToken("rg1"),
                RecordsSchedulerModels.ReviewRequest("裂", "rg1", "good", false, false, false, 0),
                consumed,
                1000L
        );
        // With single relearning step [10], one Good graduates to review
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase);
    }

    // ---- Goal 17 defect fixes / test gaps ----

    @Test
    fun chronicFloorFailuresKeepAccumulatingTheFailStreak() {
        // At the WRITE_KANJI floor demoteRung cannot move the rung, so the fail streak
        // must keep counting rather than resetting to zero every failStreak fails.
        val scheduler = BridgeScheduler()
        val consumed = HashSet<String>()
        val settings = settingsWithLadderThresholds(21, 3)
        var item = reviewCard("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L)
            .copyBuilder().writingRemediationPending(true).build()

        var now = 1000L
        // Six real due-review failures at the floor.
        for (i in 0 until 6) {
            val r = scheduler.applyReview(
                item.withToken("f$i"),
                RecordsSchedulerModels.ReviewRequest("裂", "f$i", "again", false, false, false, 0),
                consumed,
                now,
                null,
                settings,
            )
            item = r.item
            now = Math.max(item.dueAtMillis, now + 86_400_000L)
            item = item.copyBuilder()
                .dueAtMillis(now - 60_000L)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .state("review")
                .build()
        }

        assertEquals("Stays pinned at the floor", RecordsBase.LadderRung.WRITE_KANJI, item.rung)
        assertTrue(
            "Fail streak keeps accumulating at the floor instead of resetting",
            item.realAgainStreak >= 3,
        )
    }

    @Test
    fun hardOnFirstStepWithDescendingStepsUsesMidpointNotAgainDelay() {
        val scheduler = BridgeScheduler()
        // Descending learning steps [10, 5]: Again returns to step 0 (10 min); Good
        // advances to step 1 (5 min); Hard must sit at the 7.5-min midpoint, strictly
        // between them, not collapse onto the 10-min Again delay.
        val descending = RecordsSchedulerModels.LearningStepSettings(listOf(10, 5), listOf(10))
        val item = newCard("裂")
        val now = 1000L

        val hard = scheduler.applyReview(
            item.withToken("h1"),
            RecordsSchedulerModels.ReviewRequest("裂", "h1", "hard", false, false, false, 0),
            HashSet<String>(),
            now,
            null,
            null,
            descending,
        )

        val expectedMidpointMillis = (10L + 5L) * 60_000L / 2L
        assertEquals(now + expectedMidpointMillis, hard.item.dueAtMillis)
        assertEquals(0, hard.item.learningStep)
    }

    @Test
    fun hardOnSingleLearningStepUsesOneAndAHalfTimesStep() {
        val scheduler = BridgeScheduler()
        val singleStep = RecordsSchedulerModels.LearningStepSettings(listOf(10), listOf(10))
        val item = newCard("裂")
        val now = 1000L

        val hard = scheduler.applyReview(
            item.withToken("h1"),
            RecordsSchedulerModels.ReviewRequest("裂", "h1", "hard", false, false, false, 0),
            HashSet<String>(),
            now,
            null,
            null,
            singleStep,
        )

        assertEquals(now + 10L * 60_000L * 3L / 2L, hard.item.dueAtMillis)
        assertEquals(0, hard.item.learningStep)
    }

    @Test
    fun itemRestingOnSimilarKanjiDemotesPastItWhenAvailabilityFlipsFalse() {
        // A card sitting on SIMILAR_KANJI whose hasSimilarKanji becomes false must move
        // across that rung rather than stall on a rung it can no longer render. In the
        // Goal 79 order reading_kanji sits directly below similar_kanji (both
        // conditional), so an unavailable similar_kanji maps UP to kanji_meaning;
        // one demotion then lands on meaning_kanji.
        val scheduler = BridgeScheduler()
        val consumed = HashSet<String>()
        val settings = settingsWithLadderThresholds(21, 3)
        var item = reviewCard("裂", RecordsBase.LadderRung.SIMILAR_KANJI, 0L)
            .copyBuilder().hasSimilarKanji(false).build()

        var now = 1000L
        for (i in 0 until 3) {
            val r = scheduler.applyReview(
                item.withToken("f$i"),
                RecordsSchedulerModels.ReviewRequest("裂", "f$i", "again", false, false, false, 0),
                consumed,
                now,
                null,
                settings,
            )
            item = r.item
            now = Math.max(item.dueAtMillis, now + 86_400_000L)
            item = item.copyBuilder()
                .dueAtMillis(now - 60_000L)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .state("review")
                .hasSimilarKanji(false)
                .build()
        }

        assertEquals(
            "Unavailable SIMILAR_KANJI maps up to kanji_meaning; one demotion lands on meaning_kanji",
            RecordsBase.LadderRung.MEANING_KANJI,
            item.rung,
        )
    }

    @Test
    fun reviewWithClockMovedBackwardsClampsElapsedToZeroInsteadOfNegative() {
        // If the device clock moved backwards, nowMillis can precede the reconstructed
        // last-review time; elapsed days must clamp to >= 0 and still apply cleanly.
        val scheduler = BridgeScheduler()
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 10_000_000_000L)
            .copyBuilder().matureIntervalDays(30).build()

        val result = scheduler.applyReview(
            item.withToken("back"),
            passRequest("裂", "back"),
            HashSet<String>(),
            5_000_000_000L,
        )

        assertEquals("good", result.appliedRating)
        assertTrue("stability stays finite", result.item.stability.isFinite())
        assertTrue("difficulty stays finite", result.item.difficulty.isFinite())
    }

    // ---- Helpers ----

    private fun newCard(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
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
        ).withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.NEW_LEARNING);
    }

    private fun reviewCard(kanji: String, rung: RecordsBase.LadderRung, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
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
                rung == RecordsBase.LadderRung.WRITE_KANJI,
                null,
                0L
        ).withRungAndPhase(rung, RecordsBase.SchedulerPhase.REVIEW);
    }

    private fun passRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, false, false, 0);
    }

    private fun failRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "again", false, false, false, 0);
    }

    // ---- Mixed pass/fail streak-breaking tests ----

    @Test
    fun mixedPassFailSequenceResetsStreakAndPromotesOnlyByInterval() {
        val scheduler = schedulerWithReviewIntervalDays(10);
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var now = 1000L

        for (i in 0 until 2) {
            val r = scheduler.applyReview(
                    item.withToken("p" + i), passRequest("裂", "p" + i), consumed, now);
            item = r.item;
            now = item.dueAtMillis;
            item = item.copyBuilder().dueAtMillis(now).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        }
        assertEquals("Pass streak is 2 before fail", 2, item.realPassStreak);
        assertEquals("Still on KANJI_MEANING", RecordsBase.LadderRung.KANJI_MEANING, item.rung);

        val failResult = scheduler.applyReview(
                item.withToken("f0"), failRequest("裂", "f0"), consumed, now);
        item = failResult.item;
        assertEquals("Pass streak reset to 0 after fail", 0, item.realPassStreak);
        assertEquals("Again streak is 1", 1, item.realAgainStreak);
        assertEquals("Still on KANJI_MEANING after single fail", RecordsBase.LadderRung.KANJI_MEANING, item.rung);

        now = Math.max(item.dueAtMillis, now + 86_400_000L);
        item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        for (i in 0 until 2) {
            val r = scheduler.applyReview(
                    item.withToken("q" + i), passRequest("裂", "q" + i), consumed, now);
            item = r.item;
            now = item.dueAtMillis;
            item = item.copyBuilder().dueAtMillis(now).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        }
        assertEquals("Below-threshold FSRS intervals do not promote",
                RecordsBase.LadderRung.KANJI_MEANING, item.rung);

        val matureScheduler = schedulerWithReviewIntervalDays(22);
        val promoteResult = scheduler.applyReview(
                item.withToken("q2"), passRequest("裂", "q2"), consumed, now);
        assertEquals("Still below threshold with the original scheduler",
                RecordsBase.LadderRung.KANJI_MEANING, promoteResult.item.rung);

        val intervalPromote = matureScheduler.applyReview(
                item.withToken("mature"), passRequest("裂", "mature"), consumed, now);
        assertEquals("Promoted to FONT_MEANING once FSRS interval crosses threshold",
                RecordsBase.LadderRung.FONT_MEANING, intervalPromote.item.rung);
    }

    @Test
    fun realDuePassLeavesLastFailedDayUnchangedAndFailUpdatesIt() {
        val scheduler = schedulerWithReviewIntervalDays(10);
        val consumed = HashSet<String>();
        // Seed a review card that already carries a prior recorded failure day.
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
            .copyBuilder().lastFailedRecognitionDayMillis(5000L).build();
        var now = 100_000L
        item = item.copyBuilder().dueAtMillis(now - 60_000L).build();

        val passResult = scheduler.applyReview(
                item.withToken("pass"), passRequest("裂", "pass"), consumed, now);
        assertEquals("A real-due pass must not touch the legacy last-failed-day mirror",
                5000L, passResult.item.lastFailedRecognitionDayMillis);

        var failItem = passResult.item;
        val failDue = failItem.dueAtMillis
        val failNow = Math.max(failDue + 60_000L, now + 86_400_000L);
        failItem = failItem.copyBuilder().dueAtMillis(failNow - 60_000L)
            .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        val failResult = scheduler.applyReview(
                failItem.withToken("fail"), failRequest("裂", "fail"), consumed, failNow);
        assertEquals("A real-due fail updates the last-failed-day to the fail's due slot",
                failItem.dueAtMillis, failResult.item.lastFailedRecognitionDayMillis);
    }

    @Test
    fun exactPromotionThresholdDoesNotPromote() {
        val scheduler = schedulerWithReviewIntervalMillis(21L * BridgeScheduler.DAY);
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val result = scheduler.applyReview(
                item.withToken("exact"),
                passRequest("裂", "exact"),
                HashSet<String>(),
                1000L
        );

        assertEquals("Exactly 21 days does not promote",
                RecordsBase.LadderRung.KANJI_MEANING, result.item.rung);
    }

    @Test
    fun justOverPromotionThresholdPromotes() {
        val scheduler = schedulerWithReviewIntervalMillis(21L * BridgeScheduler.DAY + 1L);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val result = applyQualifyingPasses(scheduler, item, 2);

        assertEquals("Strictly more than 21 days promotes",
                RecordsBase.LadderRung.FONT_MEANING, result.item.rung);
    }

    // ---- hasSimilarKanji=true promotion via full applyReview path ----

    @Test
    fun promotionLandsOnSimilarKanjiWhenAvailable() {
        // New default order (Goal 65): meaning_kanji promotes into similar_kanji.
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.MEANING_KANJI, 0L)
                .copyBuilder().rung(RecordsBase.LadderRung.MEANING_KANJI).build()
                .withHasSimilarKanji(true);

        val result = applyQualifyingPasses(scheduler, item, 2);

        assertEquals("Promoted to SIMILAR_KANJI when hasSimilarKanji is true",
                RecordsBase.LadderRung.SIMILAR_KANJI, result.item.rung);
    }

    @Test
    fun promotionSkipsSimilarKanjiWhenUnavailableViaApplyReview() {
        // meaning_kanji -> (skip similar_kanji) -> kanji_meaning without content.
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.MEANING_KANJI, 0L)
                .copyBuilder().rung(RecordsBase.LadderRung.MEANING_KANJI).build()
                .withHasSimilarKanji(false);

        val result = applyQualifyingPasses(scheduler, item, 2);

        assertEquals("Promoted to KANJI_MEANING, skipping SIMILAR_KANJI",
                RecordsBase.LadderRung.KANJI_MEANING, result.item.rung);
    }

    @Test
    fun demotionSkipsSimilarKanjiWhenUnavailableViaApplyReview() {
        // New default order (Goal 65): kanji_meaning demotes across the
        // content-less similar_kanji rung down to meaning_kanji.
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder().rung(RecordsBase.LadderRung.KANJI_MEANING).realAgainStreak(2).build()
                .withHasSimilarKanji(false);

        val result = scheduler.applyReview(
                item.withToken("nd"), failRequest("裂", "nd"), consumed, 1000L);

        assertEquals("Demoted to MEANING_KANJI, skipping SIMILAR_KANJI",
                RecordsBase.LadderRung.MEANING_KANJI, result.item.rung);
    }

    @Test
    fun promotionSkipsChainedDisabledAndUnavailableRungs() {
        // similar_kanji has no content and kanji_meaning is disabled: promotion
        // from meaning_kanji must chain across both to font_meaning.
        var ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false);
        val promoted = ladder.nextRung(RecordsBase.LadderRung.MEANING_KANJI, RecordsBase.RungAvailability.none());
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, promoted);
    }

    // ---- Custom ladder thresholds ----

    @Test
    fun customPromotionIntervalDaysControlsPromotion() {
        val scheduler = schedulerWithReviewIntervalDays(30);
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val thirtyDayThreshold = settingsWithLadderThresholds(30, 3);
        val twentyNineDayThreshold = settingsWithLadderThresholds(29, 3);

        val held = applyQualifyingPasses(scheduler, item, 2, thirtyDayThreshold);
        assertEquals("Exactly the custom threshold does not promote",
                RecordsBase.LadderRung.KANJI_MEANING, held.item.rung);

        val promoted = applyQualifyingPasses(scheduler, item, 2, twentyNineDayThreshold);
        assertEquals("Strictly above the custom threshold promotes",
                RecordsBase.LadderRung.FONT_MEANING, promoted.item.rung);
    }

    @Test
    fun customThresholdRequiresMoreFailsToDemote() {
        // Use a threshold of 5: need 5 consecutive Again to demote.
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        val customSettings = settingsWithLadderThresholds(21, 5)
        var now = 1000L

        // After 4 fails (less than threshold 5), should NOT demote
        for (i in 0 until 4) {
            val r = scheduler.applyReview(
                    item.withToken("df" + i), failRequest("裂", "df" + i), consumed, now,
                    parameters = null, settings = customSettings, learningSettings = null);
            item = r.item;
            now = Math.max(item.dueAtMillis, now + 86_400_000L);
            item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build();
        }
        assertEquals("Still on KANJI_MEANING after 4/5 fails",
                RecordsBase.LadderRung.KANJI_MEANING, item.rung);

        // 5th fail demotes
        val r = scheduler.applyReview(
                item.withToken("df4"), failRequest("裂", "df4"), consumed, now,
                parameters = null, settings = customSettings, learningSettings = null);
        assertEquals("Demoted to MEANING_KANJI after 5 fails with custom threshold",
                RecordsBase.LadderRung.MEANING_KANJI, r.item.rung);
    }

    /**
     * Apply [passes] consecutive qualifying real-due passes to [startItem],
     * each on a fresh past due slot so every pass counts as real-due evidence.
     * Used by promotion tests now that a single qualifying pass no longer
     * promotes under the default `ladderPromotionMinPasses` gate (Goal 63).
     */
    private fun applyQualifyingPasses(
            scheduler: BridgeScheduler,
            startItem: RecordsStudyModels.StudyItem,
            passes: Int,
            settings: RecordsSyncModels.Settings? = null,
            ladder: RecordsBase.StudyLadderSettings? = null
    ): RecordsSchedulerModels.ReviewResult {
        val consumed = HashSet<String>()
        var item = startItem
        var result: RecordsSchedulerModels.ReviewResult? = null
        for (i in 0 until passes) {
            val due = i.toLong() * 100L * BridgeScheduler.DAY
            item = item.copyBuilder()
                    .dueAtMillis(due)
                    .phase(RecordsBase.SchedulerPhase.REVIEW)
                    .state("review")
                    .build()
            result = scheduler.applyReview(
                    item.withToken("qp" + i), passRequest("裂", "qp" + i), consumed, due + 1000L,
                    null, settings, ladder)
            item = result.item
        }
        return result!!
    }

    // ---- Goal 63: minimum real-due passes before promotion ----

    @Test
    fun qualifyingIntervalWithStreakOneDoesNotPromote() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val first = applyQualifyingPasses(scheduler, item, 1);

        assertEquals("A single qualifying pass does not promote at the default min-pass gate",
                RecordsBase.LadderRung.KANJI_MEANING, first.item.rung);
        assertEquals("Pass streak reaches 1 on the first qualifying pass",
                1, first.item.realPassStreak);
    }

    @Test
    fun secondQualifyingPassPromotes() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);

        val second = applyQualifyingPasses(scheduler, item, 2);

        assertEquals("The second qualifying pass promotes",
                RecordsBase.LadderRung.FONT_MEANING, second.item.rung);
        assertEquals("Streak resets on promotion", 0, second.item.realPassStreak);
    }

    @Test
    fun ladderPromotionMinPassesClampsToAtLeastOne() {
        assertEquals("Zero clamps up to 1",
                1, settingsWithMinPasses(0).ladderPromotionMinPasses);
        assertEquals("Negative clamps up to 1",
                1, settingsWithMinPasses(-5).ladderPromotionMinPasses);
        assertEquals("Default is 2",
                2, RecordsSyncModels.Settings.kikuDefaults().ladderPromotionMinPasses);
    }

    @Test
    fun minPassesOneReproducesLegacySinglePassPromotion() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L);
        val legacy = settingsWithMinPasses(1);

        val first = applyQualifyingPasses(scheduler, item, 1, legacy);

        assertEquals("With ladderPromotionMinPasses=1 a single qualifying pass promotes",
                RecordsBase.LadderRung.FONT_MEANING, first.item.rung);
    }

    @Test
    fun demotedMatureCardDoesNotRepromoteOnFirstPass() {
        // Bounce-back: a formerly-mature card that was just demoted carries
        // cloned stability above the promotion threshold, but its post-demotion
        // pass streak restarts at zero, so the first pass must not re-promote.
        val scheduler = schedulerWithReviewIntervalDays(22);
        val demoted = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder().realPassStreak(0).realAgainStreak(0).build();

        val first = applyQualifyingPasses(scheduler, demoted, 1);

        assertEquals("First post-demotion pass does not re-promote",
                RecordsBase.LadderRung.KANJI_MEANING, first.item.rung);
        assertEquals(1, first.item.realPassStreak);
    }

    // ---- Goal 70: demotion first-review cap with empty relearning steps ----

    private fun emptyRelearningSteps(): RecordsSchedulerModels.LearningStepSettings {
        return RecordsSchedulerModels.LearningStepSettings(
                RecordsSchedulerModels.LearningStepSettings.defaultNewSteps(),
                emptyList<Int?>())
    }

    @Test
    fun demotionWithEmptyRelearningCapsFirstReviewToOneDay() {
        // A large FSRS post-lapse interval (30d fake) would otherwise leave the
        // newly demoted, more-scaffolded rung unpracticed for weeks. With empty
        // relearning steps the demotion caps the first review to <= now + 1 day.
        val scheduler = schedulerWithReviewIntervalDays(30);
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder().realAgainStreak(DEFAULT_THRESHOLD - 1).build()
        val now = 100L * BridgeScheduler.DAY
        item = item.copyBuilder().dueAtMillis(now - 60_000L)
                .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()

        val result = scheduler.applyReview(
                item.withToken("d"), failRequest("裂", "d"), consumed, now,
                null, RecordsSyncModels.Settings.kikuDefaults(), emptyRelearningSteps())

        assertEquals("Third real-due again demotes the rung",
                RecordsBase.LadderRung.MEANING_KANJI, result.item.rung)
        assertTrue("Demoted rung's first review is capped to <= now + 1 day",
                result.item.dueAtMillis <= now + BridgeScheduler.DAY)
    }

    @Test
    fun nonDemotingAgainWithEmptyRelearningKeepsPostLapseInterval() {
        // A single (non-demoting) again with empty relearning steps must still
        // reschedule from the pure FSRS post-lapse interval (G8 behavior), i.e.
        // the cap only applies when the demotion actually moves the rung.
        val scheduler = schedulerWithReviewIntervalDays(30);
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        val now = 100L * BridgeScheduler.DAY
        item = item.copyBuilder().dueAtMillis(now - 60_000L)
                .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()

        val result = scheduler.applyReview(
                item.withToken("a"), failRequest("裂", "a"), consumed, now,
                null, RecordsSyncModels.Settings.kikuDefaults(), emptyRelearningSteps())

        assertEquals("A single again does not demote", RecordsBase.LadderRung.KANJI_MEANING, result.item.rung)
        assertEquals("Pure FSRS post-lapse interval is preserved (not capped)",
                now + 30L * BridgeScheduler.DAY, result.item.dueAtMillis)
    }

    // ---- Goal 67: clean-write gate to leave write_kanji ----

    private fun writeKanjiCard(writingLevel: Int, realPassStreak: Int): RecordsStudyModels.StudyItem {
        return reviewCard("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L)
                .copyBuilder()
                .rung(RecordsBase.LadderRung.WRITE_KANJI)
                .writingLevel(writingLevel)
                .realPassStreak(realPassStreak)
                .build()
    }

    private fun cleanWriteRequest(token: String): RecordsSchedulerModels.ReviewRequest {
        // writingRequired, writingPassed, writingClean, manualOverride=false, hintsUsed=0
        return RecordsSchedulerModels.ReviewRequest(
                "裂", token, "good", true, true, true, false, 0)
    }

    private fun closeWriteRequest(token: String): RecordsSchedulerModels.ReviewRequest {
        // A messy-but-passing ("Save hard") attempt: passed but not clean.
        return RecordsSchedulerModels.ReviewRequest(
                "裂", token, "hard", true, true, false, false, 0)
    }

    @Test
    fun closePassChainDoesNotPromoteOutOfWriteKanjiWithoutCleanWrites() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val consumed = HashSet<String>();
        // Start with writingLevel 1 and a primed pass streak; a CLOSE pass keeps
        // the level below 2 even though the interval and min-pass gates are met.
        var item = writeKanjiCard(writingLevel = 1, realPassStreak = 1)
        var now = 1000L
        var result: RecordsSchedulerModels.ReviewResult? = null
        for (i in 0 until 3) {
            item = item.copyBuilder().dueAtMillis(now - 60_000L)
                    .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()
            result = scheduler.applyReview(
                    item.withToken("c" + i), closeWriteRequest("c" + i), consumed, now)
            item = result.item
            now = Math.max(item.dueAtMillis, now + 100L * 86_400_000L)
        }
        assertEquals("A chain of messy CLOSE passes never promotes off write_kanji",
                RecordsBase.LadderRung.WRITE_KANJI, result!!.item.rung)
        assertTrue("writingLevel stays below the promotion floor",
                result.item.writingLevel < 2)
    }

    @Test
    fun cleanWritesPromoteOutOfWriteKanji() {
        val scheduler = schedulerWithReviewIntervalDays(22);
        val consumed = HashSet<String>();
        // writingLevel 1 + one clean hint-free pass reaches level 2 and, with the
        // min-pass gate already primed, promotes.
        var item = writeKanjiCard(writingLevel = 1, realPassStreak = 1)
        val result = scheduler.applyReview(
                item.withToken("clean"), cleanWriteRequest("clean"), consumed, 1000L)

        assertEquals("Clean hint-free writing promotes off write_kanji",
                RecordsBase.LadderRung.TYPE_MEANING, result.item.rung)
        assertTrue("writingLevel reached the promotion floor",
                result.item.writingLevel >= 2)
    }

    @Test
    fun writingLevelDoesNotBlockNonWritingRungPromotion() {
        // A non-writing rung with writingLevel 0 still promotes normally.
        val scheduler = schedulerWithReviewIntervalDays(22);
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder().writingLevel(0).build()

        val result = applyQualifyingPasses(scheduler, item, 2)

        assertEquals("writingLevel is irrelevant off the write_kanji rung",
                RecordsBase.LadderRung.FONT_MEANING, result.item.rung)
    }

    // ---- Goal 65: default reorder reaches similar_kanji in one demotion step ----

    @Test
    fun firstDemotionReachesSimilarKanjiWhenContentExists() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withHasSimilarKanji(true)
        var now = 1000L
        var result: RecordsSchedulerModels.ReviewResult? = null
        for (i in 0 until DEFAULT_THRESHOLD) {
            item = item.copyBuilder().dueAtMillis(now - 60_000L)
                    .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()
            result = scheduler.applyReview(
                    item.withToken("a" + i), failRequest("裂", "a" + i), consumed, now)
            item = result.item
            now = Math.max(item.dueAtMillis, now + DEFAULT_THRESHOLD * 86_400_000L)
        }
        assertEquals("Starting rung demotes to similar_kanji in one demotion step when content exists",
                RecordsBase.LadderRung.SIMILAR_KANJI, result!!.item.rung)
    }

    @Test
    fun firstDemotionSkipsSimilarKanjiToMeaningKanjiWithoutContent() {
        val scheduler = BridgeScheduler();
        val consumed = HashSet<String>();
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withHasSimilarKanji(false)
        var now = 1000L
        var result: RecordsSchedulerModels.ReviewResult? = null
        for (i in 0 until DEFAULT_THRESHOLD) {
            item = item.copyBuilder().dueAtMillis(now - 60_000L)
                    .phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()
            result = scheduler.applyReview(
                    item.withToken("a" + i), failRequest("裂", "a" + i), consumed, now)
            item = result.item
            now = Math.max(item.dueAtMillis, now + DEFAULT_THRESHOLD * 86_400_000L)
        }
        assertEquals("Without similar-kanji content the first demotion skips to meaning_kanji",
                RecordsBase.LadderRung.MEANING_KANJI, result!!.item.rung)
    }

    // ---- Goal 64: retention-independent promotion trigger (D4) ----

    @Test
    fun promotionDecisionIsIdenticalAcrossTargetRetentions() {
        // Same memory state reviewed at 0.80 / 0.90 / 0.95 must produce the same
        // promotion decision even though the scheduled due dates differ, because
        // promotion keys off the fixed-0.90 memory-strength interval, not the
        // retention-scaled scheduled interval.
        val now = 200L * BridgeScheduler.DAY
        val rungs = HashSet<RecordsBase.LadderRung>()
        val dueDates = HashSet<Long>()
        for (retention in doubleArrayOf(0.80, 0.90, 0.95)) {
            // High stability so the 0.90-equivalent interval clears the 21-day
            // promotion threshold; primed streak so a single pass can promote.
            val item = RecordsStudyModels.StudyItem(
                    "裂", "review", now, 60.0, 5.0, 1, 0, 2, 1, 0, 0, 0L, false, null, 0L)
                    .withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW)
                    .copyBuilder()
                    .stability(60.0)
                    .difficulty(5.0)
                    .matureIntervalDays(60)
                    .realPassStreak(1)
                    .build()
            val result = BridgeScheduler().applyReview(
                    item.withToken("r"), passRequest("裂", "r"), HashSet<String>(), now,
                    RecordsSchedulerModels.SchedulerParameters(retention))
            rungs.add(result.item.rung)
            dueDates.add(result.item.dueAtMillis)
        }
        assertEquals("All retentions promote to the same rung", 1, rungs.size)
        assertEquals("Promotion lands on FONT_MEANING at every retention",
                RecordsBase.LadderRung.FONT_MEANING, rungs.first())
        // The capped promotion due (7 days) is retention-independent too, so
        // due dates coincide; the un-promoted path would differ, so this also
        // guards that all three actually promoted.
        assertEquals("Capped promoted due is identical across retentions",
                1, dueDates.size)
    }

    @Test
    fun lowRetentionDoesNotPromoteWeakMemoryThatNinetyWouldReject() {
        // A memory whose 0.90-equivalent interval is at/below threshold must not
        // promote even at a low retention that inflates the scheduled interval
        // past 21 days. This is the core of D4: retention cannot buy promotion.
        val now = 200L * BridgeScheduler.DAY
        val weak = RecordsStudyModels.StudyItem(
                "裂", "review", now, 12.0, 5.0, 1, 0, 2, 1, 0, 0, 0L, false, null, 0L)
                .withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW)
                .copyBuilder()
                .stability(12.0)
                .difficulty(5.0)
                .matureIntervalDays(12)
                .realPassStreak(1)
                .build()
        val lowRetention = BridgeScheduler().applyReview(
                weak.withToken("low"), passRequest("裂", "low"), HashSet<String>(), now,
                RecordsSchedulerModels.SchedulerParameters(0.70))

        assertEquals("A weak memory does not promote even at low retention",
                RecordsBase.LadderRung.KANJI_MEANING, lowRetention.item.rung)
    }

    private fun schedulerWithReviewIntervalDays(intervalDays: Long): BridgeScheduler {
        return schedulerWithReviewIntervalMillis(intervalDays * BridgeScheduler.DAY);
    }

    private fun schedulerWithReviewIntervalMillis(intervalMillis: Long): BridgeScheduler {
        return BridgeScheduler(FixedIntervalFsrsAdapter(intervalMillis));
    }

    private fun settingsWithMinPasses(minPasses: Int): RecordsSyncModels.Settings {
        return settingsWithLadderThresholds(21, 3, minPasses)
    }

    private fun settingsWithLadderThresholds(promotionDays: Int, failStreak: Int): RecordsSyncModels.Settings {
        return settingsWithLadderThresholds(promotionDays, failStreak, RecordsBase.DEFAULT_LADDER_PROMOTION_MIN_PASSES)
    }

    private fun settingsWithLadderThresholds(promotionDays: Int, failStreak: Int, minPasses: Int): RecordsSyncModels.Settings {
        return RecordsSyncModels.Settings(
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
                100,
                3000,
                24,
                3,
                RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS,
                RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES,
                RecordsBase.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE,
                true,
                true,
                false,
                emptyList<String>(),
                false,
                RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
                RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
                RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
                false,
                "",
                RecordsBase.DEFAULT_NEW_CARD_SORT_MODE,
                promotionDays,
                failStreak,
                minPasses
        );
    }

    private class FixedIntervalFsrsAdapter(private val reviewIntervalMillis: Long) : KaniFsrsAdapter {
        override fun initialReview(
                rating: String?,
                currentStability: Double,
                currentDifficulty: Double,
                targetRetention: Double,
                isNewLearning: Boolean
        ): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(currentStability, currentDifficulty, BridgeScheduler.DAY)
        }

        override fun review(
                stability: Double,
                difficulty: Double,
                rating: String?,
                elapsedDays: Int,
                targetRetention: Double
        ): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis)
        }
    }
}
