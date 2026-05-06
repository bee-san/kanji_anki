package dev.bee.kanjianki.data;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.sync.SyncSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class LocalStoreInstrumentedTest {
    private Context context;
    private LocalStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void testSyncPersistsActiveMirrorSuspendedArchiveAndDerivedRows() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note active = note(1L, "確認", "かくにん", "confirmation", "確認した。");
        Records.Note suspended = note(2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。");
        Records.Card activeCard = new Records.Card(10L, 1L, 0, "例文マイニング", 2, 2, 0, 45, 12, 1, false, 18.5, 7.0, 0.48);
        Records.Card suspendedCard = new Records.Card(20L, 2L, 0, "例文マイニング", -1, 0, 0, 0, 0, 0, true);
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(active, suspended),
                Arrays.asList(activeCard, suspendedCard)
        );
        Records.SuspendedSource source = new Records.SuspendedSource("拉", 20L, 2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。");
        Records.SuspendedImport imported = new Records.SuspendedImport("拉", 3401, true, 3000, Collections.singletonList(source));
        Records.Example example = new Records.Example("suspended", 20L, 2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。", false, 0, 10, 5, 18.5, 7.0, 0.48);
        Records.DashboardRow row = new Records.DashboardRow(
                "拉",
                3401,
                "ramen",
                "らーめん",
                "deck:例文マイニング 拉",
                93,
                "suspended_archive",
                "Imported from the local suspended archive.",
                0,
                1,
                0,
                Collections.singletonList(example)
        );

        long syncId = store.saveSuccessfulSync(
                snapshot,
                Collections.singletonList(imported),
                Collections.singletonList(row),
                settings,
                1000L,
                2000L,
                null
        );
        store.updateSyncRemovalMessage(syncId, "Archived locally before provider cleanup.");

        LocalStore.SyncStatus status = store.latestSync();
        assertNotNull(status);
        assertEquals("success", status.status);
        assertEquals(1, status.activeCards);
        assertEquals(1, status.suspendedCards);
        assertEquals(1, status.importedKanji);
        assertEquals("Archived locally before provider cleanup.", status.removalMessage);
        assertEquals(1, count("source_cards"));
        assertEquals(1, count("source_notes"));
        assertEquals(1, count("suspended_archive"));
        assertEquals(1, count("suspended_imports"));
        assertEquals(1, count("suspended_sources"));
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("kanji_examples"));
        assertSourceCardFsrs(18.5, 7.0, 0.48);
        assertEquals("拉", store.dashboardRows().get(0).kanji);
        assertEquals(18.5, store.dashboardRows().get(0).examples.get(0).fsrsStability, 0.001);
        assertTrue(store.hasSuccessfulSyncSince(1500L));
        assertFalse(store.hasSuccessfulSyncSince(2500L));
        List<Records.SuspendedImport> storedImports = store.suspendedImports();
        assertEquals(1, storedImports.size());
        assertEquals("拉", storedImports.get(0).kanji);
        assertEquals(1, storedImports.get(0).sources.size());
    }

    @Test
    public void testTimelineRecordsSuspendedImportOnceAcrossRepeatedSync() {
        Records.DashboardRow row = row("拉", 0);
        Records.SuspendedImport imported = suspendedImport("拉");

        saveSingleRowSync(row, Collections.singletonList(imported), 2000L);
        assertEquals(1, countTimelineType("拉", "first_seen"));
        assertEquals(1, countTimelineType("拉", "suspended_imported"));
        assertEquals(1, countTimelineType("拉", "weak_support_seen"));

        saveSingleRowSync(row, Collections.singletonList(imported), 3000L);
        assertEquals(1, countTimelineType("拉", "first_seen"));
        assertEquals(1, countTimelineType("拉", "suspended_imported"));
        assertEquals(1, countTimelineType("拉", "weak_support_seen"));
    }

    @Test
    public void testTimelineRecordsSupportRetirementAndReopen() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        long firstSync = saveSingleRowSync(row("拉", 0), Collections.emptyList(), 2000L);
        assertTrue(firstSync > 0L);
        Records.StudyItem active = new Records.StudyItem("拉", "review", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L);
        store.replaceStudyItems(Collections.singletonList(active));

        long retireSync = saveSingleRowSync(row("拉", settings.matureSupportThreshold), Collections.emptyList(), 3000L);
        Records.StudyItem retired = new Records.StudyItem("拉", "retired", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L);
        store.replaceStudyItems(Collections.singletonList(retired), retireSync, 3000L, settings);

        long reopenSync = saveSingleRowSync(row("拉", 0), Collections.emptyList(), 4000L);
        Records.StudyItem reopened = new Records.StudyItem("拉", "review", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L);
        store.replaceStudyItems(Collections.singletonList(reopened), reopenSync, 4000L, settings);

        Records.KanjiRecoveryTimeline timeline = store.timelineForKanji("拉");
        assertNotNull(timeline.currentRow);
        assertNotNull(timeline.currentStudyItem);
        assertEquals("review", timeline.currentStudyItem.state);
        assertTrue(hasTimelineType(timeline, "support_improved"));
        assertTrue(hasTimelineType(timeline, "retired"));
        assertTrue(hasTimelineType(timeline, "support_dropped"));
        assertTrue(hasTimelineType(timeline, "reopened"));
    }

    @Test
    public void testTimelineReviewEventsMapPassFailAndManualOverride() {
        store.saveReview(new Records.ReviewRequest("拉", "pass-token", "good", true, true, false, 0), "good", 1000L);
        store.saveReview(new Records.ReviewRequest("拉", "fail-token", "good", true, false, false, 0), "again", 2000L);
        store.saveReview(new Records.ReviewRequest("拉", "override-token", "good", true, false, true, 0), "good", 3000L);

        Records.KanjiRecoveryTimeline timeline = store.timelineForKanji("拉");
        assertEquals(1, countTimelineType(timeline, "review_passed"));
        assertEquals(1, countTimelineType(timeline, "review_failed"));
        assertEquals(1, countTimelineType(timeline, "manual_override"));
    }

    @Test
    public void testVersionFourMigrationPreservesV1DataBackfillsTimelineAndAddsStudyLadderColumns() {
        store.close();
        context.deleteDatabase("kanji_anki_simple.db");
        SQLiteDatabase db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null);
        try {
            createLegacyV1Schema(db);
            ContentValuesBuilder.insert(db, "dashboard_rows")
                    .put("kanji", "拉")
                    .put("jiten_rank", 3401)
                    .put("primary_meaning", "ramen radical gap")
                    .put("reading", "ら")
                    .put("browser_search", "deck:Kiku 拉")
                    .put("weakness_score", 88)
                    .put("reason_code", "suspended_archive")
                    .put("reason_text", "Imported from suspended cards")
                    .put("active_example_count", 1)
                    .put("suspended_example_count", 1)
                    .put("mature_support_count", 0)
                    .put("rebuilt_at", 2000L)
                    .commit();
            ContentValuesBuilder.insert(db, "kanji_examples")
                    .put("kanji", "拉")
                    .put("source_type", "active")
                    .put("card_id", 10L)
                    .put("note_id", 1L)
                    .put("expression", "拉麺")
                    .put("reading", "らーめん")
                    .put("meaning", "ramen")
                    .put("sentence", "拉麺を食べた。")
                    .put("mature", 0)
                    .put("lapses", 1)
                    .commit();
            ContentValuesBuilder.insert(db, "study_items")
                    .put("kanji", "拉")
                    .put("state", "review")
                    .put("due_at", 0L)
                    .put("stability", 1.8)
                    .put("difficulty", 4.8)
                    .put("total_reviews", 1)
                    .put("lapses", 0)
                    .put("learning_step", 2)
                    .put("writing_level", 1)
                    .put("active_token", "")
                    .put("created_at", 1500L)
                    .commit();
            ContentValuesBuilder.insert(db, "review_log")
                    .put("kanji", "拉")
                    .put("token", "legacy-token")
                    .put("rating", "good")
                    .put("writing_required", 1)
                    .put("writing_passed", 1)
                    .put("manual_override", 0)
                    .put("reviewed_at", 2500L)
                    .commit();
            db.setVersion(1);
        } finally {
            db.close();
        }

        store = new LocalStore(context);
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("review_log"));
        assertTrue(hasColumn("source_cards", "fsrs_stability"));
        assertTrue(hasColumn("source_cards", "fsrs_difficulty"));
        assertTrue(hasColumn("source_cards", "fsrs_retrievability"));
        assertTrue(hasColumn("kanji_examples", "fsrs_stability"));
        assertTrue(hasColumn("study_items", "recognition_stage"));
        assertTrue(hasColumn("study_items", "consecutive_failed_recognition_days"));
        assertTrue(hasColumn("study_items", "last_failed_recognition_day"));
        assertTrue(hasColumn("study_items", "writing_remediation_pending"));
        assertTrue(hasColumn("study_items", "suppressed_by_task_type"));
        assertTrue(hasColumn("study_items", "suppressed_at"));
        assertTrue(hasColumn("study_items", "mature_interval_days"));
        assertTrue(hasColumn("study_items", "answer_signature"));
        assertTrue(hasColumn("study_items", "kanji_meaning_memory"));
        assertTrue(hasColumn("study_items", "font_meaning_memory"));
        assertTrue(hasColumn("study_items", "word_reading_memory"));
        assertTrue(hasColumn("study_items", "writing_remediation_memory"));
        assertTrue(count("kanji_timeline_events") >= 3);
        Records.KanjiRecoveryTimeline timeline = store.timelineForKanji("拉");
        assertNotNull(timeline.currentRow);
        assertNotNull(timeline.currentStudyItem);
        assertEquals(0, timeline.currentStudyItem.recognitionStage);
        assertEquals(0, timeline.currentStudyItem.consecutiveFailedRecognitionDays);
        assertEquals(0L, timeline.currentStudyItem.lastFailedRecognitionDayMillis);
        assertFalse(timeline.currentStudyItem.writingRemediationPending);
        assertEquals("", timeline.currentStudyItem.suppressedByTaskType);
        assertEquals(0L, timeline.currentStudyItem.suppressedAtMillis);
        assertEquals(0, timeline.currentStudyItem.matureIntervalDays);
        assertEquals("", timeline.currentStudyItem.answerSignature);
        assertEquals(1, timeline.currentStudyItem.kanjiMeaningMemory.totalReviews);
        assertEquals(0, timeline.currentStudyItem.fontMeaningMemory.totalReviews);
        assertEquals(0, timeline.currentStudyItem.wordReadingMemory.totalReviews);
        assertEquals(0, timeline.currentStudyItem.writingRemediationMemory.totalReviews);
        assertTrue(hasTimelineType(timeline, "first_seen"));
        assertTrue(hasTimelineType(timeline, "weak_support_seen"));
        assertTrue(hasTimelineType(timeline, "review_passed"));
    }

    @Test
    public void testSettingsAndReviewTokensPersistAcrossStoreInstances() {
        store.putIntSetting("suspended_rank_cutoff", 3000);
        Records.ReviewRequest request = new Records.ReviewRequest("拉", "token-1", "good", true, true, false, 0);
        store.saveReview(request, "good", 3000L);
        store.close();

        store = new LocalStore(context);
        assertEquals(3000, store.getIntSetting("suspended_rank_cutoff", 1000));
        assertEquals(Records.DEFAULT_WRITING_TRIGGER_MISS_DAYS, SyncSettings.fromStore(store).writingTriggerMissDays);
        store.putIntSetting("writing_trigger_miss_days", 4);
        assertEquals(4, SyncSettings.fromStore(store).writingTriggerMissDays);
        LocalStore.ReminderSettings defaults = store.reminderSettings();
        assertFalse(defaults.enabled);
        assertEquals(19, defaults.hour);
        assertEquals(0, defaults.minute);
        assertEquals(20, store.adaptiveLoadWorkPercent());
        store.saveAdaptiveLoadWorkPercent(23);
        assertEquals(25, store.adaptiveLoadWorkPercent());

        store.saveReminderSettings(new LocalStore.ReminderSettings(true, 8, 30));
        LocalStore.ReminderSettings reminder = store.reminderSettings();
        assertTrue(reminder.enabled);
        assertEquals(8, reminder.hour);
        assertEquals(30, reminder.minute);
        assertEquals("08:30", reminder.displayTime());

        LocalStore.AutoSyncSettings autoDefaults = store.autoSyncSettings();
        assertFalse(autoDefaults.configured);
        assertFalse(autoDefaults.enabled);
        assertEquals(19, autoDefaults.hour);
        assertEquals(0, autoDefaults.minute);
        assertTrue(store.activateAutoSyncAfterFirstSuccess());
        assertFalse(store.activateAutoSyncAfterFirstSuccess());
        LocalStore.AutoSyncSettings activeAuto = store.autoSyncSettings();
        assertTrue(activeAuto.configured);
        assertTrue(activeAuto.enabled);
        assertEquals("19:00", activeAuto.displayTime());
        store.recordAutoSyncAttempt(5000L, false);
        LocalStore.AutoSyncSettings failedAuto = store.autoSyncSettings();
        assertEquals(5000L, failedAuto.lastAttemptAt);
        assertEquals(0L, failedAuto.lastSuccessAt);
        store.recordAutoSyncAttempt(6000L, true);
        store.markAutoSyncScheduled(9000L);
        LocalStore.AutoSyncSettings successfulAuto = store.autoSyncSettings();
        assertEquals(6000L, successfulAuto.lastAttemptAt);
        assertEquals(6000L, successfulAuto.lastSuccessAt);
        assertEquals(9000L, successfulAuto.nextRunAt);
        store.setAutoSyncEnabled(false);
        LocalStore.AutoSyncSettings disabledAuto = store.autoSyncSettings();
        assertTrue(disabledAuto.configured);
        assertFalse(disabledAuto.enabled);

        List<String> tokens = store.consumedTokens();
        assertEquals(1, tokens.size());
        assertEquals("token-1", tokens.get(0));

        store.saveReview(request, "easy", 4000L);
        assertEquals(1, store.consumedTokens().size());
        assertEquals(1, store.studiedKanjiSince(0L).size());
    }

    @Test
    public void testReviewStatsAndSchedulerParametersPersist() {
        store.saveReview(new Records.ReviewRequest("拉", "token-a", "again", true, false, false, 0), "again", 1000L);
        store.saveReview(new Records.ReviewRequest("麺", "token-b", "good", true, true, false, 0), "good", 2000L);
        Records.ReviewStats stats = store.reviewStatsSince(0L);
        assertEquals(2, stats.total);
        assertEquals(1, stats.again);
        assertEquals(1, stats.good);
        assertEquals(2, stats.writingRequired);
        assertEquals(1, stats.writingFailed);
        LocalStore.StudyImpactStats impact = store.studyImpactStats();
        assertEquals(2, impact.totalReviews);
        assertEquals(2, impact.distinctReviewedKanji);
        assertEquals(2, impact.writingRequired);
        assertEquals(1, impact.writingPassed);
        assertEquals(1, impact.writingFailed);
        assertEquals(0, impact.manualOverrides);

        Records.SchedulerParameters tuned = Records.SchedulerParameters.defaults()
                .withAdjustment(0.40, 1.10, 1.80, 2.80, 5000L, 30);
        store.saveSchedulerParameters(tuned);
        Records.SchedulerParameters loaded = store.schedulerParameters();
        assertEquals(0.40, loaded.againMultiplier, 0.001);
        assertEquals(1.80, loaded.goodMultiplier, 0.001);
        assertEquals(5000L, loaded.lastAdjustedAtMillis);
        assertEquals(30, loaded.lastAdjustmentReviewCount);
    }

    @Test
    public void testStudyStreakCountsConsecutiveLocalReviewDays() {
        long today = localDayStart(System.currentTimeMillis());
        long yesterday = moveLocalDays(today, -1);
        long twoDaysAgo = moveLocalDays(today, -2);
        long fiveDaysAgo = moveLocalDays(today, -5);

        store.saveReview(review("拉", "token-five"), "good", fiveDaysAgo + 60_000L);
        store.saveReview(review("提", "token-two"), "good", twoDaysAgo + 60_000L);
        store.saveReview(review("謎", "token-yesterday"), "hard", yesterday + 60_000L);
        store.saveReview(review("麺", "token-today-a"), "good", today + 60_000L);
        store.saveReview(review("確", "token-today-b"), "easy", today + 120_000L);
        store.saveReview(review("確", "token-today-c"), "hard", today + 180_000L);

        LocalStore.StudyStreak streak = store.studyStreak(today + 3_600_000L);
        assertEquals(3, streak.currentDays);
        assertEquals(3, streak.bestDays);
        assertTrue(streak.studiedToday);
        assertEquals(3, streak.reviewsToday);
        assertEquals(today + 180_000L, streak.lastStudyAtMillis);
        assertEquals(2, store.studiedKanjiSince(today).size());

        LocalStore.StudyStreak tomorrow = store.studyStreak(moveLocalDays(today, 1) + 3_600_000L);
        assertEquals(3, tomorrow.currentDays);
        assertEquals(3, tomorrow.bestDays);
        assertFalse(tomorrow.studiedToday);
        assertEquals(0, tomorrow.reviewsToday);

        LocalStore.StudyStreak afterMiss = store.studyStreak(moveLocalDays(today, 2) + 3_600_000L);
        assertEquals(0, afterMiss.currentDays);
        assertEquals(3, afterMiss.bestDays);
        assertFalse(afterMiss.studiedToday);
    }

    private Records.ReviewRequest review(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "good", true, true, false, 0);
    }

    private long saveSingleRowSync(Records.DashboardRow row, List<Records.SuspendedImport> imports, long finishedAt) {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note note = note(1L, row.kanji + "語", row.reading, row.primaryMeaning, row.kanji + "を見た。");
        Records.Card card = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        return store.saveSuccessfulSync(
                new Records.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card)),
                imports,
                Collections.singletonList(row),
                settings,
                finishedAt - 500L,
                finishedAt,
                null
        );
    }

    private Records.DashboardRow row(String kanji, int matureSupportCount) {
        Records.Example active = new Records.Example("active", 10L, 1L, kanji + "語", "ら", "example", kanji + "を見た。", matureSupportCount > 0, 0);
        return new Records.DashboardRow(
                kanji,
                3401,
                "ramen radical gap",
                "ら",
                "deck:Kiku " + kanji,
                88,
                "suspended_archive",
                "Imported from suspended cards",
                1,
                0,
                matureSupportCount,
                Collections.singletonList(active)
        );
    }

    private Records.SuspendedImport suspendedImport(String kanji) {
        Records.SuspendedSource source = new Records.SuspendedSource(kanji, 20L, 2L, kanji + "例", "ら", "archive example", kanji + "を練習した。");
        return new Records.SuspendedImport(kanji, 3401, true, 3000, Collections.singletonList(source));
    }

    private int countTimelineType(String kanji, String eventType) {
        return countTimelineType(store.timelineForKanji(kanji), eventType);
    }

    private int countTimelineType(Records.KanjiRecoveryTimeline timeline, String eventType) {
        int count = 0;
        for (Records.KanjiTimelineEvent event : timeline.events) {
            if (eventType.equals(event.eventType)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasTimelineType(Records.KanjiRecoveryTimeline timeline, String eventType) {
        return countTimelineType(timeline, eventType) > 0;
    }

    private void createLegacyV1Schema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, active_notes_count INTEGER NOT NULL, active_cards_count INTEGER NOT NULL, suspended_cards_archived_count INTEGER NOT NULL, suspended_kanji_imported_count INTEGER NOT NULL, deleted_notes_count INTEGER NOT NULL, deleted_cards_count INTEGER NOT NULL, error_code TEXT, error_message TEXT, removal_message TEXT)");
        db.execSQL("CREATE TABLE source_notes (note_id INTEGER PRIMARY KEY, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, tags TEXT NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE source_cards (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_archive (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, archived_at INTEGER NOT NULL, archived_sync_id INTEGER NOT NULL, restored_at INTEGER)");
        db.execSQL("CREATE TABLE suspended_imports (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, rank_known INTEGER NOT NULL, cutoff_used INTEGER NOT NULL, first_imported_at INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_sources (kanji TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, sync_id INTEGER NOT NULL, PRIMARY KEY (kanji, card_id))");
        db.execSQL("CREATE TABLE dashboard_rows (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, primary_meaning TEXT NOT NULL, reading TEXT NOT NULL, browser_search TEXT NOT NULL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE kanji_examples (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, mature INTEGER NOT NULL, lapses INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE study_items (kanji TEXT PRIMARY KEY, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, active_token TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_examples_kanji ON kanji_examples(kanji)");
        db.execSQL("CREATE INDEX idx_study_due ON study_items(state, due_at)");
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

    private int count(String table) {
        SQLiteDatabase db = store.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private void assertSourceCardFsrs(double stability, double difficulty, double retrievability) {
        SQLiteDatabase db = store.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT fsrs_stability, fsrs_difficulty, fsrs_retrievability FROM source_cards WHERE card_id=10", null);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(stability, cursor.getDouble(0), 0.001);
            assertEquals(difficulty, cursor.getDouble(1), 0.001);
            assertEquals(retrievability, cursor.getDouble(2), 0.001);
        } finally {
            cursor.close();
        }
    }

    private boolean hasColumn(String table, String column) {
        SQLiteDatabase db = store.getReadableDatabase();
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
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

    private static final class ContentValuesBuilder {
        private final SQLiteDatabase db;
        private final String table;
        private final ContentValues values = new ContentValues();

        private ContentValuesBuilder(SQLiteDatabase db, String table) {
            this.db = db;
            this.table = table;
        }

        private static ContentValuesBuilder insert(SQLiteDatabase db, String table) {
            return new ContentValuesBuilder(db, table);
        }

        private ContentValuesBuilder put(String key, String value) {
            values.put(key, value);
            return this;
        }

        private ContentValuesBuilder put(String key, int value) {
            values.put(key, value);
            return this;
        }

        private ContentValuesBuilder put(String key, long value) {
            values.put(key, value);
            return this;
        }

        private ContentValuesBuilder put(String key, double value) {
            values.put(key, value);
            return this;
        }

        private void commit() {
            db.insertOrThrow(table, null, values);
        }
    }
}
