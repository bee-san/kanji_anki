package dev.bee.kanjianki;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.SyncSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end instrumented tests for the ladder scheduler. These tests exercise
 * the full lifecycle: sync → LocalStore persistence → BridgeScheduler state
 * transitions → review persistence → rung promotion/demotion → DB round-trip.
 *
 * Unlike the pure-JVM LadderSchedulerTest, these verify that ladder state
 * (rung, phase, realPassStreak, realAgainStreak, lastRealReviewDueAtMillis,
 * hasSimilarKanji) survives SQLite round-trips correctly.
 */
@RunWith(AndroidJUnit4.class)
public final class LadderSchedulerEndToEndTest {

    private Context context;
    private LocalStore store;
    private BridgeScheduler scheduler;
    private int helperTokenCounter;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
        scheduler = new BridgeScheduler();
        helperTokenCounter = 0;
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        context.deleteDatabase("kanji_anki_simple.db");
    }

    // ---- Learning graduation persists ladder state ----

    @Test
    public void newCardLearningGraduationPersistsRungAndPhaseToDb() {
        seedSyncWithKanji("裂");
        List<Records.StudyItem> items = store.studyItems();
        assertEquals(1, items.size());
        Records.StudyItem item = items.get(0);
        assertEquals(Records.LadderRung.KANJI_MEANING, item.rung);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, item.phase);

        // Graduate through 2 default new-learning steps (1m, 10m)
        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        item = item.withToken("g1");
        Records.ReviewResult r1 = scheduler.applyReview(
                item, passRequest("裂", "g1"), consumed, now);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, r1.item.phase);
        assertEquals(1, r1.item.learningStep);

        Records.ReviewResult r2 = scheduler.applyReview(
                r1.item.withToken("g2"),
                passRequest("裂", "g2"), consumed, now + 60_000L);
        assertEquals(Records.SchedulerPhase.REVIEW, r2.item.phase);
        assertEquals(Records.LadderRung.KANJI_MEANING, r2.item.rung);
        assertEquals("review", r2.item.state);

        // Persist and reload from DB
        store.saveStudyItem(r2.item);
        List<Records.StudyItem> reloaded = store.studyItems();
        assertEquals(1, reloaded.size());
        Records.StudyItem persisted = reloaded.get(0);
        assertEquals(Records.SchedulerPhase.REVIEW, persisted.phase);
        assertEquals(Records.LadderRung.KANJI_MEANING, persisted.rung);
        assertEquals("review", persisted.state);
        assertTrue(persisted.dueAtMillis > now);
    }

    // ---- Due-review passes promote rung ----

    @Test
    public void threeConsecutiveDueReviewPassesPromoteRung() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // 3 passes in a row should promote from KANJI_MEANING -> FONT_MEANING
        for (int i = 0; i < 3; i++) {
            String token = "pass" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + i * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }

        // Verify promotion persisted
        List<Records.StudyItem> reloaded = store.studyItems();
        assertEquals(1, reloaded.size());
        Records.StudyItem promoted = reloaded.get(0);
        assertEquals(Records.LadderRung.FONT_MEANING, promoted.rung);
        assertEquals(Records.SchedulerPhase.REVIEW, promoted.phase);
        assertEquals(0, promoted.realPassStreak); // reset after promotion
        assertEquals(0, promoted.realAgainStreak);
    }

    // ---- Due-review Agains demote rung ----

    @Test
    public void threeConsecutiveDueReviewAgainsDemoteRung() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // 3 Agains. Each Again enters relearning; need to graduate back out.
        for (int i = 0; i < 3; i++) {
            String token = "fail" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, failRequest("裂", token), consumed, now + i * 60_000L);
            current = result.item;

            // If entered relearning, graduate back to review for the next due attempt
            if (current.phase == Records.SchedulerPhase.RELEARNING) {
                current = graduateFromRelearning(current, consumed, now + i * 60_000L + 30_000L);
            }
            // Make it due again for the next iteration
            current = current.copyBuilder().dueAtMillis(now + (i + 1) * 60_000L - 500L).build();
            store.saveStudyItem(current);
        }

        // Verify demotion persisted (KANJI_MEANING -> TYPE_MEANING, skipping similar_kanji since hasSimilarKanji=false)
        List<Records.StudyItem> reloaded = store.studyItems();
        assertEquals(1, reloaded.size());
        Records.StudyItem demoted = reloaded.get(0);
        assertEquals(Records.LadderRung.TYPE_MEANING, demoted.rung);
    }

    // ---- Similar kanji rung skipped when hasSimilarKanji is false ----

    @Test
    public void promotionSkipsSimilarKanjiRungWhenFlagIsFalse() {
        long dueAt = System.currentTimeMillis() - 1000L;
        // Start on TYPE_MEANING, hasSimilarKanji=false
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.TYPE_MEANING, dueAt)
                .copyBuilder().hasSimilarKanji(false).build();
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);
        assertFalse(current.hasSimilarKanji);

        // 3 passes should promote TYPE_MEANING -> KANJI_MEANING (skipping SIMILAR_KANJI)
        for (int i = 0; i < 3; i++) {
            String token = "p" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + i * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }

        List<Records.StudyItem> reloaded = store.studyItems();
        Records.StudyItem promoted = reloaded.get(0);
        assertEquals(Records.LadderRung.KANJI_MEANING, promoted.rung);
    }

    // ---- Similar kanji rung included when hasSimilarKanji is true ----

    @Test
    public void promotionIncludesSimilarKanjiRungWhenFlagIsTrue() throws Exception {
        // Seed with similar_kanji_pairs so hasSimilarKanji is true
        seedSyncWithSimilarPairs("裂", "烈");
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.TYPE_MEANING, dueAt)
                .copyBuilder().hasSimilarKanji(true).build();
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);
        assertTrue(current.hasSimilarKanji);

        // 3 passes should promote TYPE_MEANING -> SIMILAR_KANJI (not skipping)
        for (int i = 0; i < 3; i++) {
            String token = "p" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + i * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }

        List<Records.StudyItem> reloaded = store.studyItems();
        Records.StudyItem promoted = reloaded.get(0);
        assertEquals(Records.LadderRung.SIMILAR_KANJI, promoted.rung);
    }

    // ---- Relearning is practice-only ----

    @Test
    public void relearningPhaseDoesNotAdvanceLadderStreaks() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder().realPassStreak(2).build();
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // One Again on a due review enters relearning
        current = current.withToken("lapse1");
        Records.ReviewResult lapseResult = scheduler.applyReview(
                current, failRequest("裂", "lapse1"), consumed, now);
        Records.StudyItem inRelearning = lapseResult.item;
        assertEquals(Records.SchedulerPhase.RELEARNING, inRelearning.phase);
        store.saveStudyItem(inRelearning);

        // Relearning Good (practice-only, should not affect pass streak)
        inRelearning = inRelearning.withToken("rl1");
        Records.ReviewResult rlResult = scheduler.applyReview(
                inRelearning, passRequest("裂", "rl1"), consumed, now + 5000L);
        store.saveStudyItem(rlResult.item);

        // Reload and verify streaks not advanced toward promotion
        List<Records.StudyItem> reloaded = store.studyItems();
        Records.StudyItem afterRelearning = reloaded.get(0);
        assertEquals(Records.LadderRung.KANJI_MEANING, afterRelearning.rung);
        // The pass streak was 2 before the lapse. Lapse resets it.
        assertEquals(0, afterRelearning.realPassStreak);
        // The again streak incremented from the due-review Again, but relearning Good did NOT increment pass streak
        // After graduating relearning, the card is back in review phase
    }

    // ---- Ladder floor (WRITE_KANJI) prevents demotion ----

    @Test
    public void ladderFloorPreventsDemotionBelowWriteKanji() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.WRITE_KANJI, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // 3+ Agains at WRITE_KANJI should keep it there
        for (int i = 0; i < 5; i++) {
            String token = "floor" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, failRequest("裂", token), consumed, now + i * 60_000L);
            current = result.item;
            if (current.phase == Records.SchedulerPhase.RELEARNING) {
                current = graduateFromRelearning(current, consumed, now + i * 60_000L + 30_000L);
            }
            current = current.copyBuilder().dueAtMillis(now + (i + 1) * 60_000L - 500L).build();
            store.saveStudyItem(current);
        }

        List<Records.StudyItem> reloaded = store.studyItems();
        Records.StudyItem floored = reloaded.get(0);
        assertEquals(Records.LadderRung.WRITE_KANJI, floored.rung);
    }

    // ---- Ladder ceiling (WORD_READING) prevents promotion ----

    @Test
    public void ladderCeilingPreventsPromotionAboveWordReading() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.WORD_READING, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // 5 consecutive passes at the ceiling
        for (int i = 0; i < 5; i++) {
            String token = "ceiling" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + i * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }

        List<Records.StudyItem> reloaded = store.studyItems();
        Records.StudyItem capped = reloaded.get(0);
        assertEquals(Records.LadderRung.WORD_READING, capped.rung);
    }

    // ---- Custom realDueReviewsToMove setting ----

    @Test
    public void customRealDueReviewsToMoveHonorsSettingFromSyncSettings() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        // Store custom threshold of 2 (instead of default 3)
        persistSetting("real_due_reviews_to_move", "2");
        Records.Settings settings = SyncSettings.fromStore(store);
        assertEquals(2, settings.realDueReviewsToMove);

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // 2 passes with threshold=2 should promote
        for (int i = 0; i < 2; i++) {
            String token = "custom" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + i * 1000L,
                    Records.SchedulerParameters.defaults(), settings);
            current = result.item;
            store.saveStudyItem(current);
        }

        List<Records.StudyItem> reloaded = store.studyItems();
        Records.StudyItem promoted = reloaded.get(0);
        assertEquals(Records.LadderRung.FONT_MEANING, promoted.rung);
    }

    // ---- Full multi-rung E2E lifecycle ----

    @Test
    public void fullLifecycleSyncGraduatesAndPromotesThroughMultipleRungs() {
        // Sync seeds a new card
        seedSyncWithKanji("裂");
        List<Records.StudyItem> initial = store.studyItems();
        assertEquals(1, initial.size());
        Records.StudyItem item = initial.get(0);
        assertEquals(Records.LadderRung.KANJI_MEANING, item.rung);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, item.phase);

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        int tokenCounter = 0;

        // Phase 1: Graduate through new-learning (2 Goods)
        Records.StudyItem current = item;
        for (int i = 0; i < 2; i++) {
            String token = "learn" + (tokenCounter++);
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + tokenCounter * 1000L);
            current = result.item;
        }
        assertEquals(Records.SchedulerPhase.REVIEW, current.phase);
        assertEquals(Records.LadderRung.KANJI_MEANING, current.rung);
        store.saveStudyItem(current);

        // Phase 2: 3 due-review passes -> promote to FONT_MEANING
        current = current.copyBuilder().dueAtMillis(now).build();
        store.saveStudyItem(current);
        for (int i = 0; i < 3; i++) {
            current = store.studyItems().get(0);
            String token = "promo1_" + (tokenCounter++);
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + tokenCounter * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }
        assertEquals(Records.LadderRung.FONT_MEANING, store.studyItems().get(0).rung);

        // Phase 3: 3 more due-review passes -> promote to WORD_READING
        current = store.studyItems().get(0).copyBuilder().dueAtMillis(now).build();
        store.saveStudyItem(current);
        for (int i = 0; i < 3; i++) {
            current = store.studyItems().get(0);
            String token = "promo2_" + (tokenCounter++);
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + tokenCounter * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }
        assertEquals(Records.LadderRung.WORD_READING, store.studyItems().get(0).rung);

        // Phase 4: At ceiling, 3 more passes still stay at WORD_READING
        current = store.studyItems().get(0).copyBuilder().dueAtMillis(now).build();
        store.saveStudyItem(current);
        for (int i = 0; i < 3; i++) {
            current = store.studyItems().get(0);
            String token = "ceil_" + (tokenCounter++);
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + tokenCounter * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }
        assertEquals(Records.LadderRung.WORD_READING, store.studyItems().get(0).rung);
    }

    @Test
    public void seedQueueAdmitsNewItemWithCorrectLadderDefaults() {
        Records.DashboardRow row = dashboardRow("裂", "split");
        long now = System.currentTimeMillis();
        long startOfDay = now - (now % 86_400_000L);
        List<Records.StudyItem> seeded = scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.emptyList(),
                Records.Settings.kikuDefaults(),
                now,
                startOfDay
        );
        assertEquals(1, seeded.size());
        Records.StudyItem admitted = seeded.get(0);
        assertEquals("裂", admitted.kanji);
        assertEquals(Records.LadderRung.KANJI_MEANING, admitted.rung);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, admitted.phase);
        assertEquals(0, admitted.realPassStreak);
        assertEquals(0, admitted.realAgainStreak);

        // Persist and round-trip
        store.replaceStudyItems(seeded);
        Records.StudyItem reloaded = store.studyItems().get(0);
        assertEquals(Records.LadderRung.KANJI_MEANING, reloaded.rung);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, reloaded.phase);
    }

    @Test
    public void hasSimilarKanjiAnnotatedFromSimilarPairsOnRead() throws Exception {
        seedSyncWithSimilarPairs("裂", "烈");
        Records.StudyItem item = new Records.StudyItem("裂", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, 0, null, System.currentTimeMillis());
        store.replaceStudyItems(Collections.singletonList(item));

        // On read, hasSimilarKanji should be true because similar_kanji_pairs has (裂, 烈)
        Records.StudyItem reloaded = store.studyItems().get(0);
        assertTrue("hasSimilarKanji should be true when pairs exist", reloaded.hasSimilarKanji);
    }

    @Test
    public void hasSimilarKanjiFalseWhenNoPairsExist() {
        seedSyncWithKanji("裂");
        Records.StudyItem reloaded = store.studyItems().get(0);
        assertFalse("hasSimilarKanji should be false when no pairs exist", reloaded.hasSimilarKanji);
    }

    @Test
    public void annotateSimilarKanjiAvailabilityWorksAfterSeedQueue() throws Exception {
        seedSyncWithSimilarPairs("裂", "烈");
        Records.DashboardRow row = dashboardRow("裂", "split");
        long now = System.currentTimeMillis();
        long startOfDay = now - (now % 86_400_000L);

        List<Records.StudyItem> seeded = scheduler.seedQueue(
                Collections.singletonList(row),
                Collections.emptyList(),
                Records.Settings.kikuDefaults(),
                now,
                startOfDay
        );
        // Before annotation, hasSimilarKanji defaults to false
        assertFalse(seeded.get(0).hasSimilarKanji);

        // Annotate from DB
        List<Records.StudyItem> annotated = store.annotateSimilarKanjiAvailability(seeded);
        assertTrue(annotated.get(0).hasSimilarKanji);

        // Persist annotated, reload, confirm
        store.replaceStudyItems(annotated);
        assertTrue(store.studyItems().get(0).hasSimilarKanji);
    }

    @Test
    public void demotionFromKanjiMeaningIncludesSimilarKanjiWhenAvailable() throws Exception {
        seedSyncWithSimilarPairs("裂", "烈");
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder().hasSimilarKanji(true).build();
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);
        assertTrue(current.hasSimilarKanji);

        // 3 Agains with relearning graduation between each
        for (int i = 0; i < 3; i++) {
            String token = "demote" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, failRequest("裂", token), consumed, now + i * 60_000L);
            current = result.item;
            if (current.phase == Records.SchedulerPhase.RELEARNING) {
                current = graduateFromRelearning(current, consumed, now + i * 60_000L + 30_000L);
            }
            current = current.copyBuilder().dueAtMillis(now + (i + 1) * 60_000L - 500L).build();
            store.saveStudyItem(current);
        }

        // With hasSimilarKanji=true, demotion should land on SIMILAR_KANJI (not TYPE_MEANING)
        List<Records.StudyItem> reloaded = store.studyItems();
        assertEquals(Records.LadderRung.SIMILAR_KANJI, reloaded.get(0).rung);
    }

    @Test
    public void passStreakResetsAfterPromotion() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // Accumulate 2 passes (streak = 2)
        for (int i = 0; i < 2; i++) {
            String token = "streak" + i;
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest("裂", token), consumed, now + i * 1000L);
            current = result.item;
            store.saveStudyItem(current);
        }

        Records.StudyItem beforePromotion = store.studyItems().get(0);
        assertEquals(2, beforePromotion.realPassStreak);
        assertEquals(Records.LadderRung.KANJI_MEANING, beforePromotion.rung);

        // Third pass triggers promotion and resets streak
        current = store.studyItems().get(0).withToken("streak2");
        Records.ReviewResult promoResult = scheduler.applyReview(
                current, passRequest("裂", "streak2"), consumed, now + 3000L);
        store.saveStudyItem(promoResult.item);

        Records.StudyItem afterPromotion = store.studyItems().get(0);
        assertEquals(Records.LadderRung.FONT_MEANING, afterPromotion.rung);
        assertEquals(0, afterPromotion.realPassStreak);
    }

    @Test
    public void failResetsPassStreakWithoutDemotion() {
        long dueAt = System.currentTimeMillis() - 1000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.FONT_MEANING, dueAt)
                .copyBuilder().realPassStreak(2).build();
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);
        assertEquals(2, current.realPassStreak);

        // One Again resets the pass streak but doesn't demote (need 3 Agains)
        current = current.withToken("break");
        Records.ReviewResult result = scheduler.applyReview(
                current, failRequest("裂", "break"), consumed, now);
        store.saveStudyItem(result.item);

        Records.StudyItem after = store.studyItems().get(0);
        assertEquals(Records.LadderRung.FONT_MEANING, after.rung); // no demotion yet
        assertEquals(0, after.realPassStreak); // pass streak reset
        assertEquals(1, after.realAgainStreak); // again streak started
    }

    @Test
    public void lastRealReviewDueAtMillisPersistsThroughDbRoundTrip() {
        long dueAt = System.currentTimeMillis() - 5000L;
        Records.StudyItem item = reviewItemOnRung("裂", Records.LadderRung.KANJI_MEANING, dueAt);
        store.replaceStudyItems(Collections.singletonList(item));

        Set<String> consumed = new HashSet<>();
        long now = System.currentTimeMillis();
        Records.StudyItem current = store.studyItems().get(0);

        // Apply a due review so the scheduler records lastRealReviewDueAtMillis
        current = current.withToken("due1");
        Records.ReviewResult result = scheduler.applyReview(
                current, passRequest("裂", "due1"), consumed, now);
        store.saveStudyItem(result.item);

        Records.StudyItem reloaded = store.studyItems().get(0);
        assertTrue("lastRealReviewDueAtMillis should be set after a due review",
                reloaded.lastRealReviewDueAtMillis > 0L);
        assertEquals(1, reloaded.realPassStreak);
    }

    // ---- Helpers ----

    private void seedSyncWithKanji(String kanji) {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note note = note(1L, kanji + "語", "reading", "meaning", kanji + "の例文。");
        Records.Card card = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        Records.DashboardRow row = dashboardRow(kanji, "meaning");
        store.saveSuccessfulSync(
                new Records.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card)),
                Collections.emptyList(),
                Collections.singletonList(row),
                settings,
                System.currentTimeMillis() - 1000L,
                System.currentTimeMillis(),
                null
        );
        long now = System.currentTimeMillis();
        store.replaceStudyItems(Collections.singletonList(
                new Records.StudyItem(kanji, "new", now, 0.4, 5.0, 0, 0, 0, 0, 0, null, now)
        ));
    }

    private void seedSyncWithSimilarPairs(String kanjiA, String kanjiB) throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note noteA = note(1L, kanjiA + "語", "reading", "meaning", kanjiA + "の例文。");
        Records.Note noteB = note(2L, kanjiB + "語", "reading2", "meaning2", kanjiB + "の例文。");
        Records.Card cardA = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        Records.Card cardB = new Records.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        Records.DashboardRow rowA = dashboardRow(kanjiA, "meaning");
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader(kanjiA + "\t" + kanjiB + "\tfixture\n"));
        store.saveSuccessfulSync(
                new Records.CollectionSnapshot(Arrays.asList(noteA, noteB), Arrays.asList(cardA, cardB)),
                Collections.emptyList(),
                Collections.singletonList(rowA),
                settings,
                new LocalStore.SyncTiming(System.currentTimeMillis() - 1000L, System.currentTimeMillis()),
                null,
                index
        );
    }

    private Records.StudyItem reviewItemOnRung(String kanji, Records.LadderRung rung, long dueAtMillis) {
        return new Records.StudyItem(
                kanji,
                "review",
                dueAtMillis,
                1.2,
                5.0,
                1,
                0,
                2,
                rung == Records.LadderRung.WRITE_KANJI ? 1 : 0,
                0,
                0,
                0L,
                rung == Records.LadderRung.WRITE_KANJI,
                null,
                0L
        ).withRungAndPhase(rung, Records.SchedulerPhase.REVIEW);
    }

    private Records.StudyItem graduateFromRelearning(Records.StudyItem item, Set<String> consumed, long nowMillis) {
        // Graduate relearning with repeated Goods until back in review phase
        Records.StudyItem current = item;
        for (int i = 0; i < 5 && current.phase == Records.SchedulerPhase.RELEARNING; i++) {
            String token = "rl_grad_" + (helperTokenCounter++);
            current = current.withToken(token);
            Records.ReviewResult result = scheduler.applyReview(
                    current, passRequest(current.kanji, token), consumed, nowMillis + i * 1000L);
            current = result.item;
        }
        return current;
    }

    private Records.DashboardRow dashboardRow(String kanji, String meaning) {
        Records.Example example = new Records.Example(
                "active", 10L, 1L, kanji + "語", "reading", meaning,
                kanji + "の例文。", false, 1);
        return new Records.DashboardRow(
                kanji,
                3401,
                meaning,
                "reading",
                "deck:Kiku " + kanji,
                88,
                "suspended_archive",
                "Imported from suspended cards.",
                1,
                1,
                0,
                Collections.singletonList(example)
        );
    }

    private Records.Note note(long id, String expression, String reading, String meaning, String sentence) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new Records.Note(id, "Kiku", fields, Collections.emptyList());
    }

    private static Records.ReviewRequest passRequest(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "good", false, false, false, 0);
    }

    private static Records.ReviewRequest failRequest(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "again", false, false, false, 0);
    }

    private void persistSetting(String key, String value) {
        android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase();
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertWithOnConflict("settings", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }
}
