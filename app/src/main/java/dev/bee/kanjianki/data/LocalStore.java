package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.KanjiImpactAnalyzer;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LocalStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "kanji_anki_simple.db";
    private static final int DB_VERSION = 14;
    private static final String STUDY_ITEMS_TABLE_SQL = "CREATE TABLE study_items (kanji TEXT NOT NULL, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, recognition_stage INTEGER NOT NULL DEFAULT 0, consecutive_failed_recognition_days INTEGER NOT NULL DEFAULT 0, last_failed_recognition_day INTEGER NOT NULL DEFAULT 0, writing_remediation_pending INTEGER NOT NULL DEFAULT 0, suppressed_by_task_type TEXT NOT NULL DEFAULT '', suppressed_at INTEGER NOT NULL DEFAULT 0, mature_interval_days INTEGER NOT NULL DEFAULT 0, answer_signature TEXT NOT NULL DEFAULT '', typing_meaning_memory TEXT NOT NULL DEFAULT '', kanji_meaning_memory TEXT NOT NULL DEFAULT '', font_meaning_memory TEXT NOT NULL DEFAULT '', word_reading_memory TEXT NOT NULL DEFAULT '', writing_remediation_memory TEXT NOT NULL DEFAULT '', active_token TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature))";
    private static final String LEARNING_REPEATS_TABLE_SQL = "CREATE TABLE learning_repeats (kanji TEXT NOT NULL, answer_signature TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL, repeat_type TEXT NOT NULL, step_index INTEGER NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature, task_type))";
    private static final String REVIEW_LOG_TABLE_SQL = "CREATE TABLE review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL, task_type TEXT NOT NULL DEFAULT '', answer_signature TEXT NOT NULL DEFAULT '', prompt TEXT NOT NULL DEFAULT '', hints_used INTEGER NOT NULL DEFAULT 0, writing_clean INTEGER NOT NULL DEFAULT 0, memory_before TEXT NOT NULL DEFAULT '', memory_after TEXT NOT NULL DEFAULT '', scheduler_state_after_json TEXT NOT NULL DEFAULT '')";
    private static final String STATUS_SUCCESS = "success";
    private static final String COLUMN_FIRST_IMPORTED_AT = "first_imported_at";
    private static final int DEFAULT_REMINDER_HOUR = 19;
    private static final int DEFAULT_REMINDER_MINUTE = 0;
    private static final int DEFAULT_AUTO_SYNC_HOUR = DEFAULT_REMINDER_HOUR;
    private static final int DEFAULT_AUTO_SYNC_MINUTE = DEFAULT_REMINDER_MINUTE;
    private static final String KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled";
    private static final String KEY_AUTO_UPDATE_LAST_CHECK_AT = "auto_update_last_check_at";
    private static final String KEY_AUTO_UPDATE_LAST_RESULT = "auto_update_last_result";
    private static final String KEY_AUTO_UPDATE_LAST_VERSION = "auto_update_last_version";
    private static final String KEY_AUTO_UPDATE_PENDING_APK = "auto_update_pending_apk";
    private static final String KEY_AUTO_UPDATE_PENDING_MESSAGE = "auto_update_pending_message";

    public LocalStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, active_notes_count INTEGER NOT NULL, active_cards_count INTEGER NOT NULL, suspended_cards_archived_count INTEGER NOT NULL, suspended_kanji_imported_count INTEGER NOT NULL, deleted_notes_count INTEGER NOT NULL, deleted_cards_count INTEGER NOT NULL, error_code TEXT, error_message TEXT, removal_message TEXT)");
        db.execSQL("CREATE TABLE source_notes (note_id INTEGER PRIMARY KEY, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, tags TEXT NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE source_cards (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_archive (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, archived_at INTEGER NOT NULL, archived_sync_id INTEGER NOT NULL, restored_at INTEGER)");
        db.execSQL("CREATE TABLE suspended_imports (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, rank_known INTEGER NOT NULL, cutoff_used INTEGER NOT NULL, first_imported_at INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_sources (kanji TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, sync_id INTEGER NOT NULL, PRIMARY KEY (kanji, card_id))");
        db.execSQL("CREATE TABLE dashboard_rows (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, primary_meaning TEXT NOT NULL, reading TEXT NOT NULL, browser_search TEXT NOT NULL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE kanji_examples (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, mature INTEGER NOT NULL, lapses INTEGER NOT NULL, interval_days INTEGER NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL)");
        createKanjiInventoryTables(db);
        createSimilarKanjiTables(db);
        createSimilarKanjiPracticeTables(db);
        db.execSQL(STUDY_ITEMS_TABLE_SQL);
        db.execSQL(LEARNING_REPEATS_TABLE_SQL);
        db.execSQL(REVIEW_LOG_TABLE_SQL);
        db.execSQL("CREATE INDEX idx_examples_kanji ON kanji_examples(kanji)");
        db.execSQL("CREATE INDEX idx_study_due ON study_items(state, due_at)");
        db.execSQL("CREATE INDEX idx_learning_repeats_due ON learning_repeats(due_at)");
        createTimelineTables(db);
        createHistoricalSyncTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createTimelineTables(db);
            backfillTimelineEvents(db);
        }
        if (oldVersion < 3) {
            addNullableColumn(db, "source_cards", "fsrs_stability", "REAL");
            addNullableColumn(db, "source_cards", "fsrs_difficulty", "REAL");
            addNullableColumn(db, "source_cards", "fsrs_retrievability", "REAL");
            addNullableColumn(db, "kanji_examples", "interval_days", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "kanji_examples", "reps", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "kanji_examples", "fsrs_stability", "REAL");
            addNullableColumn(db, "kanji_examples", "fsrs_difficulty", "REAL");
            addNullableColumn(db, "kanji_examples", "fsrs_retrievability", "REAL");
        }
        if (oldVersion < 4) {
            addNullableColumn(db, "study_items", "recognition_stage", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "study_items", "consecutive_failed_recognition_days", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "study_items", "last_failed_recognition_day", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "study_items", "writing_remediation_pending", "INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            addNullableColumn(db, "study_items", "suppressed_by_task_type", "TEXT NOT NULL DEFAULT ''");
            addNullableColumn(db, "study_items", "suppressed_at", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "study_items", "mature_interval_days", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "study_items", "answer_signature", "TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 6) {
            addNullableColumn(db, "study_items", "kanji_meaning_memory", "TEXT NOT NULL DEFAULT ''");
            addNullableColumn(db, "study_items", "font_meaning_memory", "TEXT NOT NULL DEFAULT ''");
            addNullableColumn(db, "study_items", "word_reading_memory", "TEXT NOT NULL DEFAULT ''");
            addNullableColumn(db, "study_items", "writing_remediation_memory", "TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 7) {
            rebuildStudyItemsWithAnswerSignatureKey(db);
        }
        if (oldVersion < 8) {
            db.execSQL(LEARNING_REPEATS_TABLE_SQL);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learning_repeats_due ON learning_repeats(due_at)");
        }
        if (oldVersion < 9) {
            createKanjiInventoryTables(db);
            backfillKanjiInventory(db, System.currentTimeMillis(), Records.Settings.kikuDefaults());
        }
        if (oldVersion < 10) {
            createSimilarKanjiTables(db);
        }
        if (oldVersion < 11) {
            createSimilarKanjiPracticeTables(db);
            rebuildSimilarKanjiChoiceStates(db, System.currentTimeMillis());
        }
        if (oldVersion < 12) {
            createHistoricalSyncTables(db);
            addRichReviewColumns(db);
            addHistoricalIdentityColumns(db);
            backfillLatestHistoricalSync(db);
        }
        if (oldVersion < 13) {
            createHistoricalSyncTables(db);
            addHistoricalIdentityColumns(db);
        }
        if (oldVersion < 14) {
            addNullableColumn(db, "study_items", "typing_meaning_memory", "TEXT NOT NULL DEFAULT ''");
        }
    }

    private void createKanjiInventoryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kanji_inventory (kanji TEXT PRIMARY KEY, primary_meaning TEXT NOT NULL, readings TEXT NOT NULL, browser_search TEXT NOT NULL, search_text TEXT NOT NULL, source_count INTEGER NOT NULL, example_count INTEGER NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS local_kanji_suspensions (kanji TEXT PRIMARY KEY, suspended_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kanji_inventory_search ON kanji_inventory(search_text)");
    }

    private void createSimilarKanjiTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS similar_kanji_pairs (kanji_a TEXT NOT NULL, kanji_b TEXT NOT NULL, source TEXT NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (kanji_a, kanji_b, source))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_a ON similar_kanji_pairs(kanji_a)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_b ON similar_kanji_pairs(kanji_b)");
    }

    private void createSimilarKanjiPracticeTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS similar_kanji_choice_state (target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, primary_meaning TEXT NOT NULL, choices TEXT NOT NULL, due_at INTEGER NOT NULL, passed_at INTEGER NOT NULL DEFAULT 0, last_reviewed_at INTEGER NOT NULL DEFAULT 0, correct_count INTEGER NOT NULL DEFAULT 0, wrong_count INTEGER NOT NULL DEFAULT 0, active_token TEXT NOT NULL DEFAULT '', first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (target_kanji, choice_signature))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_choice_due ON similar_kanji_choice_state(passed_at, due_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS similar_kanji_repair_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, repair_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, wrong_selection TEXT NOT NULL, prompt_meaning TEXT NOT NULL, status TEXT NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', attempts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_repair_due ON similar_kanji_repair_queue(status, due_at, created_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS similar_kanji_review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, selected_kanji TEXT NOT NULL, correct INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_review_log_target ON similar_kanji_review_log(target_kanji, reviewed_at)");
    }

    private void createHistoricalSyncTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_card_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, sync_id INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, deck_id TEXT NOT NULL DEFAULT '', deck_name TEXT NOT NULL, model_id INTEGER NOT NULL DEFAULT 0, model_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, suspended INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, mature INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_card_snapshots_sync_card ON sync_card_snapshots(sync_id, card_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_card_snapshots_note ON sync_card_snapshots(sync_id, note_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_note_snapshots (sync_id INTEGER NOT NULL, finished_at INTEGER NOT NULL, note_id INTEGER NOT NULL, model_id INTEGER NOT NULL DEFAULT 0, model_name TEXT NOT NULL, deck_ids TEXT NOT NULL DEFAULT '', deck_names TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, tags TEXT NOT NULL, fields_json TEXT NOT NULL, extracted_kanji TEXT NOT NULL, PRIMARY KEY (sync_id, note_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_note_snapshots_kanji ON sync_note_snapshots(sync_id, extracted_kanji)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_kanji_snapshots (sync_id INTEGER NOT NULL, finished_at INTEGER NOT NULL, kanji TEXT NOT NULL, active_cards INTEGER NOT NULL, suspended_cards INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, average_interval_days REAL NOT NULL, total_lapses INTEGER NOT NULL, total_reps INTEGER NOT NULL, fsrs_stability_avg REAL, fsrs_difficulty_avg REAL, fsrs_retrievability_avg REAL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, PRIMARY KEY (sync_id, kanji))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_kanji_snapshots_kanji_sync ON sync_kanji_snapshots(kanji, sync_id)");
    }

    private void addHistoricalIdentityColumns(SQLiteDatabase db) {
        addNullableColumn(db, "sync_card_snapshots", "deck_id", "TEXT NOT NULL DEFAULT ''");
        addNullableColumn(db, "sync_card_snapshots", "model_id", "INTEGER NOT NULL DEFAULT 0");
        addNullableColumn(db, "sync_note_snapshots", "model_id", "INTEGER NOT NULL DEFAULT 0");
        addNullableColumn(db, "sync_note_snapshots", "deck_ids", "TEXT NOT NULL DEFAULT ''");
        db.execSQL("UPDATE sync_card_snapshots SET deck_id=deck_name WHERE deck_id=''");
        db.execSQL("UPDATE sync_note_snapshots SET deck_ids=deck_names WHERE deck_ids=''");
    }

    private void addRichReviewColumns(SQLiteDatabase db) {
        addNullableColumn(db, "review_log", "task_type", "TEXT NOT NULL DEFAULT ''");
        addNullableColumn(db, "review_log", "answer_signature", "TEXT NOT NULL DEFAULT ''");
        addNullableColumn(db, "review_log", "prompt", "TEXT NOT NULL DEFAULT ''");
        addNullableColumn(db, "review_log", "hints_used", "INTEGER NOT NULL DEFAULT 0");
        addNullableColumn(db, "review_log", "writing_clean", "INTEGER NOT NULL DEFAULT 0");
        addNullableColumn(db, "review_log", "memory_before", "TEXT NOT NULL DEFAULT ''");
        addNullableColumn(db, "review_log", "memory_after", "TEXT NOT NULL DEFAULT ''");
        addNullableColumn(db, "review_log", "scheduler_state_after_json", "TEXT NOT NULL DEFAULT ''");
    }

    private void rebuildStudyItemsWithAnswerSignatureKey(SQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS idx_study_due");
        db.execSQL("ALTER TABLE study_items RENAME TO study_items_old");
        db.execSQL(STUDY_ITEMS_TABLE_SQL);
        db.execSQL("INSERT OR REPLACE INTO study_items (kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, recognition_stage, consecutive_failed_recognition_days, last_failed_recognition_day, writing_remediation_pending, suppressed_by_task_type, suppressed_at, mature_interval_days, answer_signature, kanji_meaning_memory, font_meaning_memory, word_reading_memory, writing_remediation_memory, active_token, created_at) SELECT kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, recognition_stage, consecutive_failed_recognition_days, last_failed_recognition_day, writing_remediation_pending, suppressed_by_task_type, suppressed_at, mature_interval_days, COALESCE(answer_signature, ''), kanji_meaning_memory, font_meaning_memory, word_reading_memory, writing_remediation_memory, active_token, created_at FROM study_items_old");
        db.execSQL("DROP TABLE study_items_old");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_due ON study_items(state, due_at)");
    }

    public static final class SyncTiming {
        public final long startedAt;
        public final long finishedAt;

        public SyncTiming(long startedAt, long finishedAt) {
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }
    }

    public long saveSuccessfulSync(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> imports,
            List<Records.DashboardRow> rows,
            Records.Settings settings,
            long startedAt,
            long finishedAt,
            AnkiDroidGateway.RemovalSummary removal
    ) {
        return saveSuccessfulSync(snapshot, imports, rows, settings, new SyncTiming(startedAt, finishedAt), removal, null);
    }

    public long saveSuccessfulSync(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> imports,
            List<Records.DashboardRow> rows,
            Records.Settings settings,
            SyncTiming timing,
            AnkiDroidGateway.RemovalSummary removal,
            SimilarKanjiIndex similarIndex
    ) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, RowSnapshot> previousRows = rowSnapshots(db);
            ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
            int deletedNotes = countDeletedExisting(db, "source_notes", "note_id", activeIndex.noteIds);
            int deletedCards = countDeletedExisting(db, "source_cards", "card_id", activeIndex.cardIds);
            long syncId = insertSyncRun(db, timing.startedAt, timing.finishedAt, STATUS_SUCCESS, activeIndex, imports.size(), null, null, removal == null ? "" : removal.message, deletedNotes, deletedCards);
            Map<Long, Records.Note> notesById = snapshot.notesById();
            appendHistoricalSyncSnapshots(db, snapshot, notesById, rows, settings, syncId, timing);
            db.delete("source_cards", null, null);
            db.delete("source_notes", null, null);
            db.delete("dashboard_rows", null, null);
            db.delete("kanji_examples", null, null);

            for (Records.Note note : snapshot.notes) {
                if (activeIndex.noteIds.contains(note.noteId)) {
                    ContentValues values = new ContentValues();
                    values.put("note_id", note.noteId);
                    values.put("model_name", note.modelName);
                    values.put("expression", TextUtil.normalizeJapanese(note.expression(settings)));
                    values.put("reading", TextUtil.normalizeJapanese(note.reading(settings)));
                    values.put("meaning", TextUtil.firstMeaningLine(note.meaning(settings)));
                    values.put("sentence", TextUtil.normalizeJapanese(note.sentence(settings)));
                    values.put("fields_json", fieldsJson(note.fields));
                    values.put("tags", String.join(" ", note.tags));
                    values.put("last_seen_sync_id", syncId);
                    db.insertWithOnConflict("source_notes", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            for (Records.Card card : snapshot.cards) {
                Records.Note note = notesById.get(card.noteId);
                if (note == null) {
                    continue;
                }
                if (card.suspended) {
                    ContentValues values = new ContentValues();
                    values.put("card_id", card.cardId);
                    values.put("note_id", card.noteId);
                    values.put("deck_name", card.deckName);
                    values.put("model_name", note.modelName);
                    values.put("expression", TextUtil.normalizeJapanese(note.expression(settings)));
                    values.put("reading", TextUtil.normalizeJapanese(note.reading(settings)));
                    values.put("meaning", TextUtil.firstMeaningLine(note.meaning(settings)));
                    values.put("sentence", TextUtil.normalizeJapanese(note.sentence(settings)));
                    values.put("fields_json", fieldsJson(note.fields));
                    values.put("archived_at", timing.finishedAt);
                    values.put("archived_sync_id", syncId);
                    db.insertWithOnConflict("suspended_archive", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                } else {
                    ContentValues values = new ContentValues();
                    values.put("card_id", card.cardId);
                    values.put("note_id", card.noteId);
                    values.put("deck_name", card.deckName);
                    values.put("ord", card.ord);
                    values.put("queue", card.queue);
                    values.put("type", card.type);
                    values.put("due", card.due);
                    values.put("interval_days", card.intervalDays);
                    values.put("reps", card.reps);
                    values.put("lapses", card.lapses);
                    putNullableDouble(values, "fsrs_stability", card.fsrsStability);
                    putNullableDouble(values, "fsrs_difficulty", card.fsrsDifficulty);
                    putNullableDouble(values, "fsrs_retrievability", card.fsrsRetrievability);
                    values.put("last_seen_sync_id", syncId);
                    db.insertWithOnConflict("source_cards", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            for (Records.SuspendedImport imported : imports) {
                ContentValues values = new ContentValues();
                values.put("kanji", imported.kanji);
                if (imported.jitenRank != null) {
                    values.put("jiten_rank", imported.jitenRank);
                }
                values.put("rank_known", imported.rankKnown ? 1 : 0);
                values.put("cutoff_used", imported.cutoffUsed);
                values.put(COLUMN_FIRST_IMPORTED_AT, firstImportedAt(db, imported.kanji, timing.finishedAt));
                values.put("last_seen_sync_id", syncId);
                db.insertWithOnConflict("suspended_imports", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                for (Records.SuspendedSource source : imported.sources) {
                    ContentValues sourceValues = new ContentValues();
                    sourceValues.put("kanji", imported.kanji);
                    sourceValues.put("card_id", source.cardId);
                    sourceValues.put("note_id", source.noteId);
                    sourceValues.put("expression", source.expression);
                    sourceValues.put("reading", source.reading);
                    sourceValues.put("meaning", source.meaning);
                    sourceValues.put("sentence", source.sentence);
                    sourceValues.put("sync_id", syncId);
                    db.insertWithOnConflict("suspended_sources", null, sourceValues, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            saveRows(db, rows, timing.finishedAt);
            rebuildKanjiInventory(db, snapshot, imports, rows, timing.finishedAt, settings);
            if (similarIndex != null) {
                rebuildSimilarKanjiPairs(db, similarIndex, timing.finishedAt);
            }
            rebuildSimilarKanjiChoiceStates(db, timing.finishedAt);
            appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, timing.finishedAt, settings);
            db.setTransactionSuccessful();
            return syncId;
        } finally {
            db.endTransaction();
        }
    }

    public void saveFailedSync(long startedAt, long finishedAt, String status, String errorCode, String errorMessage) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("started_at", startedAt);
        values.put("finished_at", finishedAt);
        values.put("status", status);
        values.put("active_notes_count", 0);
        values.put("active_cards_count", 0);
        values.put("suspended_cards_archived_count", 0);
        values.put("suspended_kanji_imported_count", 0);
        values.put("deleted_notes_count", 0);
        values.put("deleted_cards_count", 0);
        values.put("error_code", errorCode);
        values.put("error_message", errorMessage);
        values.put("removal_message", "");
        db.insert("sync_runs", null, values);
    }

    public void updateSyncRemovalMessage(long syncId, String message) {
        ContentValues values = new ContentValues();
        values.put("removal_message", message == null ? "" : message);
        getWritableDatabase().update("sync_runs", values, "id=?", new String[]{Long.toString(syncId)});
    }

    public List<Records.DashboardRow> dashboardRows() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.DashboardRow> rows = new ArrayList<>();
        Cursor cursor = db.query("dashboard_rows", null, null, null, null, null, "weakness_score DESC, suspended_example_count DESC, kanji ASC", "120");
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, "kanji");
                rows.add(new Records.DashboardRow(
                        kanji,
                        nullableInt(cursor, "jiten_rank"),
                        string(cursor, "primary_meaning"),
                        string(cursor, "reading"),
                        string(cursor, "browser_search"),
                        integer(cursor, "weakness_score"),
                        string(cursor, "reason_code"),
                        string(cursor, "reason_text"),
                        integer(cursor, "active_example_count"),
                        integer(cursor, "suspended_example_count"),
                        integer(cursor, "mature_support_count"),
                        examplesForKanji(db, kanji)
                ));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    public List<Records.DashboardRow> activeDashboardRows() {
        Set<String> suspended = locallySuspendedKanji();
        if (suspended.isEmpty()) {
            return dashboardRows();
        }
        List<Records.DashboardRow> out = new ArrayList<>();
        for (Records.DashboardRow row : dashboardRows()) {
            if (!suspended.contains(row.kanji)) {
                out.add(row);
            }
        }
        return out;
    }

    public Records.DashboardRow rowForKanji(String kanji) {
        return readDashboardRow(getReadableDatabase(), kanji);
    }

    public Records.KanjiInventoryItem inventoryItemForKanji(String kanji) {
        return readInventoryItem(getReadableDatabase(), kanji);
    }

    public List<Records.KanjiInventoryItem> searchKanjiInventory(String query) {
        SQLiteDatabase db = getReadableDatabase();
        String normalized = TextUtil.normalizeJapanese(query == null ? "" : query).toLowerCase(Locale.ROOT);
        List<Records.KanjiInventoryItem> out = new ArrayList<>();
        String selection = null;
        String[] args = null;
        if (!normalized.isEmpty()) {
            selection = "search_text LIKE ?";
            args = new String[]{"%" + normalized + "%"};
        }
        Cursor cursor = db.query(
                "kanji_inventory",
                null,
                selection,
                args,
                null,
                null,
                "kanji ASC",
                "300"
        );
        try {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    public void rebuildSimilarKanjiPairs(SimilarKanjiIndex similarIndex, long nowMillis) {
        if (similarIndex == null) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            rebuildSimilarKanjiPairs(db, similarIndex, nowMillis);
            rebuildSimilarKanjiChoiceStates(db, nowMillis);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Records.SimilarKanjiPair> allLocalSimilarPairs() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiPair> out = new ArrayList<>();
        Cursor cursor = db.query("similar_kanji_pairs", null, null, null, null, null, "kanji_a ASC, kanji_b ASC, source ASC");
        try {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    public List<Records.SimilarKanjiPair> similarPairsForKanji(String kanji) {
        String normalized = normalizeSingleKanji(kanji);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiPair> out = new ArrayList<>();
        Cursor cursor = db.query(
                "similar_kanji_pairs",
                null,
                "kanji_a=? OR kanji_b=?",
                new String[]{normalized, normalized},
                null,
                null,
                "kanji_a ASC, kanji_b ASC, source ASC"
        );
        try {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    public boolean hasSimilarLocalPair(String first, String second) {
        String kanjiA = normalizeSingleKanji(first);
        String kanjiB = normalizeSingleKanji(second);
        if (kanjiA.isEmpty() || kanjiB.isEmpty() || kanjiA.equals(kanjiB)) {
            return false;
        }
        String[] pair = canonicalSimilarPair(kanjiA, kanjiB);
        Cursor cursor = getReadableDatabase().query(
                "similar_kanji_pairs",
                new String[]{"kanji_a"},
                "kanji_a=? AND kanji_b=?",
                pair,
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public List<Records.SimilarKanjiChoiceCard> allSimilarChoiceCards() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiChoiceCard> out = new ArrayList<>();
        Cursor cursor = db.query(
                "similar_kanji_choice_state",
                null,
                null,
                null,
                null,
                null,
                "target_kanji ASC, choice_signature ASC"
        );
        try {
            while (cursor.moveToNext()) {
                out.add(readSimilarChoiceCard(cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    public Records.SimilarKanjiChoiceCard dueSimilarChoiceForActiveTarget(String kanji, long nowMillis) {
        String target = normalizeSingleKanji(kanji);
        if (target.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                "similar_kanji_choice_state",
                null,
                "target_kanji=? AND passed_at=0 AND due_at<=?",
                new String[]{target, Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, first_seen_at ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Records.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
            return hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature) ? null : card;
        } finally {
            cursor.close();
        }
    }

    public Records.SimilarKanjiChoiceCard nextDueInventorySimilarChoice(Set<String> activeTargets, long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                "similar_kanji_choice_state",
                null,
                "passed_at=0 AND due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, last_reviewed_at ASC, target_kanji ASC"
        );
        try {
            while (cursor.moveToNext()) {
                Records.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
                if (activeTargets != null && activeTargets.contains(card.targetKanji)) {
                    continue;
                }
                if (!hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature)) {
                    return card;
                }
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    public Records.SimilarKanjiChoiceResult submitSimilarChoice(
            Records.SimilarKanjiChoiceCard submitted,
            String selectedKanji,
            long nowMillis
    ) {
        if (submitted == null) {
            return new Records.SimilarKanjiChoiceResult(null, selectedKanji, false, Collections.emptyList());
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Records.SimilarKanjiChoiceCard card = similarChoiceCard(db, submitted.targetKanji, submitted.choiceSignature);
            if (card == null) {
                card = submitted;
            }
            SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
            Records.SimilarKanjiChoiceResult result = planner.evaluateSelection(card, normalizeSingleKanji(selectedKanji));

            ContentValues values = new ContentValues();
            values.put("last_reviewed_at", nowMillis);
            if (result.correct) {
                values.put("passed_at", nowMillis);
                values.put("correct_count", card.correctCount + 1);
            } else {
                values.put("passed_at", 0L);
                values.put("due_at", nowMillis);
                values.put("wrong_count", card.wrongCount + 1);
            }
            db.update(
                    "similar_kanji_choice_state",
                    values,
                    "target_kanji=? AND choice_signature=?",
                    new String[]{card.targetKanji, card.choiceSignature}
            );

            ContentValues log = new ContentValues();
            log.put("target_kanji", card.targetKanji);
            log.put("choice_signature", card.choiceSignature);
            log.put("selected_kanji", result.selectedKanji);
            log.put("correct", result.correct ? 1 : 0);
            log.put("reviewed_at", nowMillis);
            db.insert("similar_kanji_review_log", null, log);

            if (!result.correct) {
                for (String repairKanji : result.repairKanji) {
                    enqueueSimilarWritingRepair(db, card, repairKanji, result.selectedKanji, nowMillis);
                }
            }
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    public Records.SimilarKanjiWritingRepair nextDueSimilarWritingRepair(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                "similar_kanji_repair_queue",
                null,
                "status=? AND due_at<=?",
                new String[]{"pending", Long.toString(nowMillis)},
                null,
                null,
                "created_at ASC, id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readSimilarWritingRepair(cursor);
        } finally {
            cursor.close();
        }
    }

    public void saveSimilarWritingRepair(Records.SimilarKanjiWritingRepair repair) {
        if (repair == null || repair.id <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("active_token", repair.activeToken);
        values.put("updated_at", repair.updatedAtMillis);
        getWritableDatabase().update(
                "similar_kanji_repair_queue",
                values,
                "id=? AND status=?",
                new String[]{Long.toString(repair.id), "pending"}
        );
    }

    public boolean finishSimilarWritingRepair(long repairId, String token, boolean passed, long nowMillis) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Records.SimilarKanjiWritingRepair current = similarWritingRepair(db, repairId);
            if (current == null || !"pending".equals(current.status)) {
                return false;
            }
            if (!current.activeToken.isEmpty() && !current.activeToken.equals(token == null ? "" : token)) {
                return false;
            }
            ContentValues values = new ContentValues();
            values.put("active_token", "");
            values.put("updated_at", nowMillis);
            if (passed) {
                values.put("status", "complete");
                values.put("completed_at", nowMillis);
            } else {
                values.put("attempts", current.attempts + 1);
                values.put("due_at", nowMillis);
            }
            db.update("similar_kanji_repair_queue", values, "id=?", new String[]{Long.toString(repairId)});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public Set<String> locallySuspendedKanji() {
        Set<String> out = new HashSet<>();
        Cursor cursor = getReadableDatabase().query("local_kanji_suspensions", new String[]{"kanji"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                out.add(string(cursor, "kanji"));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    public boolean isKanjiLocallySuspended(String kanji) {
        Cursor cursor = getReadableDatabase().query("local_kanji_suspensions", new String[]{"kanji"}, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public void setKanjiLocallySuspended(String kanji, boolean suspended, long nowMillis) {
        if (kanji == null || kanji.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (suspended) {
                ContentValues values = new ContentValues();
                values.put("kanji", kanji);
                values.put("suspended_at", nowMillis);
                db.insertWithOnConflict("local_kanji_suspensions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                db.delete("learning_repeats", "kanji=?", new String[]{kanji});
            } else {
                db.delete("local_kanji_suspensions", "kanji=?", new String[]{kanji});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.KanjiRecoveryTimeline timelineForKanji(String kanji) {
        SQLiteDatabase db = getReadableDatabase();
        Records.KanjiInventoryItem inventoryItem = readInventoryItem(db, kanji);
        Records.DashboardRow row = readDashboardRow(db, kanji);
        Records.StudyItem item = studyItemForKanji(db, kanji);
        List<Records.KanjiTimelineEvent> events = new ArrayList<>();
        Cursor cursor = db.query(
                "kanji_timeline_events",
                null,
                "kanji=?",
                new String[]{kanji},
                null,
                null,
                "occurred_at DESC, id DESC",
                "50"
        );
        try {
            while (cursor.moveToNext()) {
                events.add(readTimelineEvent(cursor));
            }
        } finally {
            cursor.close();
        }
        Collections.reverse(events);
        return new Records.KanjiRecoveryTimeline(inventoryItem, row, item, events);
    }

    public List<Records.StudyItem> studyItems() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.StudyItem> items = new ArrayList<>();
        Cursor cursor = db.query("study_items", null, null, null, null, null, "due_at ASC");
        try {
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<Records.SuspendedImport> suspendedImports() {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        Cursor cursor = db.query("suspended_imports", null, null, null, null, null, "jiten_rank ASC, kanji ASC");
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, "kanji");
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, "jiten_rank"),
                        integer(cursor, "rank_known") == 1,
                        integer(cursor, "cutoff_used")
                ));
            }
        } finally {
            cursor.close();
        }

        Cursor sources = db.query("suspended_sources", null, null, null, null, null, "kanji ASC, card_id ASC");
        try {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, "kanji"));
                if (imported == null) {
                    continue;
                }
                imported.sources.add(new Records.SuspendedSource(
                        imported.kanji,
                        longValue(sources, "card_id"),
                        longValue(sources, "note_id"),
                        string(sources, "expression"),
                        string(sources, "reading"),
                        string(sources, "meaning"),
                        string(sources, "sentence")
                ));
            }
        } finally {
            sources.close();
        }

        List<Records.SuspendedImport> out = new ArrayList<>();
        for (MutableSuspendedImport imported : imports.values()) {
            out.add(imported.build());
        }
        return out;
    }

    public void replaceStudyItems(List<Records.StudyItem> items) {
        replaceStudyItems(items, null, 0L, null);
    }

    public void replaceStudyItems(List<Records.StudyItem> items, Long syncId, long occurredAt, Records.Settings settings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, StudySnapshot> previous = syncId == null ? Collections.emptyMap() : studySnapshots(db);
            db.delete("study_items", null, null);
            for (Records.StudyItem item : items) {
                upsertStudyItem(db, item);
            }
            if (syncId != null) {
                appendStudyStateTimelineEvents(db, previous, items, syncId, occurredAt, settings);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveStudyItem(Records.StudyItem item) {
        upsertStudyItem(getWritableDatabase(), item);
    }

    public void saveReview(Records.ReviewRequest request, String appliedRating, long reviewedAt) {
        saveReview(request, appliedRating, reviewedAt, null, null);
    }

    public void saveReview(Records.ReviewRequest request, String appliedRating, long reviewedAt, Records.StudyItem beforeReview, Records.StudyItem afterReview) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long inserted = insertReview(db, request, appliedRating, reviewedAt, beforeReview, afterReview);
            if (inserted != -1L) {
                appendReviewTimelineEvent(db, request, appliedRating, reviewedAt, "review:" + request.token);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long insertReview(SQLiteDatabase db, Records.ReviewRequest request, String appliedRating, long reviewedAt, Records.StudyItem beforeReview, Records.StudyItem afterReview) {
        ContentValues values = new ContentValues();
        values.put("kanji", request.kanji);
        values.put("token", request.token);
        values.put("rating", appliedRating);
        values.put("writing_required", request.writingRequired ? 1 : 0);
        values.put("writing_passed", request.writingPassed ? 1 : 0);
        values.put("manual_override", request.manualOverride ? 1 : 0);
        values.put("reviewed_at", reviewedAt);
        values.put("task_type", request.taskType);
        values.put("answer_signature", request.answerSignature);
        values.put("prompt", request.prompt);
        values.put("hints_used", request.hintsUsed);
        values.put("writing_clean", request.writingClean ? 1 : 0);
        values.put("memory_before", taskMemoryText(beforeReview, request.taskType));
        values.put("memory_after", taskMemoryText(afterReview, request.taskType));
        values.put("scheduler_state_after_json", studyItemSchedulerJson(afterReview));
        return db.insertWithOnConflict("review_log", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private String taskMemoryText(Records.StudyItem item, String taskType) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return "";
        }
        return item.memoryForTaskType(taskType).encode();
    }

    private String studyItemSchedulerJson(Records.StudyItem item) {
        if (item == null) {
            return "";
        }
        return "{"
                + "\"state\":" + TextUtil.jsonQuote(item.state)
                + ",\"due_at\":" + item.dueAtMillis
                + ",\"stability\":" + item.stability
                + ",\"difficulty\":" + item.difficulty
                + ",\"total_reviews\":" + item.totalReviews
                + ",\"lapses\":" + item.lapses
                + ",\"learning_step\":" + item.learningStep
                + ",\"writing_level\":" + item.writingLevel
                + ",\"recognition_stage\":" + item.recognitionStage
                + ",\"writing_remediation_pending\":" + (item.writingRemediationPending ? "true" : "false")
                + ",\"mature_interval_days\":" + item.matureIntervalDays
                + "}";
    }

    public List<String> consumedTokens() {
        List<String> tokens = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("review_log", new String[]{"token"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                tokens.add(string(cursor, "token"));
            }
        } finally {
            cursor.close();
        }
        return tokens;
    }

    public SyncStatus latestSync() {
        Cursor cursor = getReadableDatabase().query("sync_runs", null, null, null, null, null, "id DESC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new SyncStatus(
                    string(cursor, "status"),
                    integer(cursor, "active_notes_count"),
                    integer(cursor, "active_cards_count"),
                    integer(cursor, "suspended_cards_archived_count"),
                    integer(cursor, "suspended_kanji_imported_count"),
                    longValue(cursor, "finished_at"),
                    string(cursor, "error_message"),
                    string(cursor, "removal_message")
            );
        } finally {
            cursor.close();
        }
    }

    public boolean hasSuccessfulSyncSince(long finishedAtMillis) {
        Cursor cursor = getReadableDatabase().query(
                "sync_runs",
                new String[]{"id"},
                "status=? AND finished_at>=?",
                new String[]{STATUS_SUCCESS, Long.toString(finishedAtMillis)},
                null,
                null,
                "id DESC",
                "1"
        );
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public int getIntSetting(String key, int fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Integer.parseInt(string(cursor, "value"));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } finally {
            cursor.close();
        }
    }

    public long getLongSetting(String key, long fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Long.parseLong(string(cursor, "value"));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } finally {
            cursor.close();
        }
    }

    public String getStringSetting(String key, String fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            String value = string(cursor, "value");
            return value == null ? fallback : value;
        } finally {
            cursor.close();
        }
    }

    public double getDoubleSetting(String key, double fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Double.parseDouble(string(cursor, "value"));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } finally {
            cursor.close();
        }
    }

    public void putIntSetting(String key, int value) {
        putSetting(key, Integer.toString(value));
    }

    public void putLongSetting(String key, long value) {
        putSetting(key, Long.toString(value));
    }

    public void putStringSetting(String key, String value) {
        putSetting(key, value == null ? "" : value);
    }

    public void putDoubleSetting(String key, double value) {
        putSetting(key, String.format(Locale.ROOT, "%.4f", value));
    }

    public int adaptiveLoadWorkPercent() {
        return AdaptiveLoadPlanner.snapWorkloadPercent(getIntSetting(
                AdaptiveLoadPlanner.SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT
        ));
    }

    public void saveAdaptiveLoadWorkPercent(int percent) {
        putIntSetting(AdaptiveLoadPlanner.SETTING_KEY, AdaptiveLoadPlanner.snapWorkloadPercent(percent));
    }

    public String adaptiveLoadMode() {
        return AdaptiveLoadPlanner.normalizeWorkloadMode(getStringSetting(
                AdaptiveLoadPlanner.MODE_SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE
        ));
    }

    public void saveAdaptiveLoadMode(String mode) {
        putStringSetting(AdaptiveLoadPlanner.MODE_SETTING_KEY, AdaptiveLoadPlanner.normalizeWorkloadMode(mode));
    }

    public ReminderSettings reminderSettings() {
        return new ReminderSettings(
                getIntSetting("reminder_enabled", 0) == 1,
                getIntSetting("reminder_hour", DEFAULT_REMINDER_HOUR),
                getIntSetting("reminder_minute", DEFAULT_REMINDER_MINUTE)
        ).normalized();
    }

    public void saveReminderSettings(ReminderSettings settings) {
        ReminderSettings normalized = settings.normalized();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("reminder_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("reminder_hour", normalized.hour);
            putIntSetting("reminder_minute", normalized.minute);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public AutoSyncSettings autoSyncSettings() {
        return new AutoSyncSettings(
                getIntSetting("auto_sync_configured", 0) == 1,
                getIntSetting("auto_sync_enabled", 0) == 1,
                getIntSetting("auto_sync_hour", DEFAULT_AUTO_SYNC_HOUR),
                getIntSetting("auto_sync_minute", DEFAULT_AUTO_SYNC_MINUTE),
                getLongSetting("auto_sync_last_attempt_at", 0L),
                getLongSetting("auto_sync_last_success_at", 0L),
                getLongSetting("auto_sync_next_run_at", 0L)
        ).normalized();
    }

    public boolean activateAutoSyncAfterFirstSuccess() {
        AutoSyncSettings current = autoSyncSettings();
        if (current.configured) {
            return false;
        }
        saveAutoSyncSettings(new AutoSyncSettings(true, true, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
        return true;
    }

    public void setAutoSyncEnabled(boolean enabled) {
        AutoSyncSettings current = autoSyncSettings();
        saveAutoSyncSettings(new AutoSyncSettings(true, enabled, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
    }

    public void markAutoSyncScheduled(long nextRunAt) {
        putLongSetting("auto_sync_next_run_at", nextRunAt);
    }

    public void recordAutoSyncAttempt(long attemptedAt, boolean success) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting("auto_sync_last_attempt_at", attemptedAt);
            if (success) {
                putLongSetting("auto_sync_last_success_at", attemptedAt);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveAutoSyncSettings(AutoSyncSettings settings) {
        AutoSyncSettings normalized = settings.normalized();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("auto_sync_configured", normalized.configured ? 1 : 0);
            putIntSetting("auto_sync_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("auto_sync_hour", normalized.hour);
            putIntSetting("auto_sync_minute", normalized.minute);
            putLongSetting("auto_sync_last_attempt_at", normalized.lastAttemptAt);
            putLongSetting("auto_sync_last_success_at", normalized.lastSuccessAt);
            putLongSetting("auto_sync_next_run_at", normalized.nextRunAt);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public AutoUpdateStatus autoUpdateStatus() {
        return new AutoUpdateStatus(
                getIntSetting(KEY_AUTO_UPDATE_ENABLED, 1) == 1,
                getLongSetting(KEY_AUTO_UPDATE_LAST_CHECK_AT, 0L),
                getStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, "No automatic update check has run yet."),
                getStringSetting(KEY_AUTO_UPDATE_LAST_VERSION, ""),
                getStringSetting(KEY_AUTO_UPDATE_PENDING_APK, ""),
                getStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, "")
        );
    }

    public void saveAutoUpdateEnabled(boolean enabled) {
        putIntSetting(KEY_AUTO_UPDATE_ENABLED, enabled ? 1 : 0);
    }

    public void recordAutoUpdateResult(long checkedAt, String result, String version, String pendingApkName, String pendingMessage) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(KEY_AUTO_UPDATE_LAST_CHECK_AT, checkedAt);
            putStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, result == null ? "" : result);
            putStringSetting(KEY_AUTO_UPDATE_LAST_VERSION, version == null ? "" : version);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_APK, pendingApkName == null ? "" : pendingApkName);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, pendingMessage == null ? "" : pendingMessage);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearPendingAutoUpdate(String result) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, result == null ? "" : result);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_APK, "");
            putStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, "");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.SchedulerParameters schedulerParameters() {
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        return new Records.SchedulerParameters(
                getDoubleSetting("scheduler_target_retention", defaults.targetRetention),
                getDoubleSetting("scheduler_again_multiplier", defaults.againMultiplier),
                getDoubleSetting("scheduler_hard_multiplier", defaults.hardMultiplier),
                getDoubleSetting("scheduler_good_multiplier", defaults.goodMultiplier),
                getDoubleSetting("scheduler_easy_multiplier", defaults.easyMultiplier),
                getLongSetting("scheduler_last_adjusted_at", defaults.lastAdjustedAtMillis),
                getIntSetting("scheduler_last_adjustment_review_count", defaults.lastAdjustmentReviewCount)
        );
    }

    public void saveSchedulerParameters(Records.SchedulerParameters parameters) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putDoubleSetting("scheduler_target_retention", parameters.targetRetention);
            putDoubleSetting("scheduler_again_multiplier", parameters.againMultiplier);
            putDoubleSetting("scheduler_hard_multiplier", parameters.hardMultiplier);
            putDoubleSetting("scheduler_good_multiplier", parameters.goodMultiplier);
            putDoubleSetting("scheduler_easy_multiplier", parameters.easyMultiplier);
            putLongSetting("scheduler_last_adjusted_at", parameters.lastAdjustedAtMillis);
            putIntSetting("scheduler_last_adjustment_review_count", parameters.lastAdjustmentReviewCount);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.LearningStepSettings learningStepSettings() {
        Records.LearningStepSettings defaults = Records.LearningStepSettings.defaults();
        List<Integer> newSteps = Records.LearningStepSettings.parseSteps(
                getStringSetting("new_learning_steps_minutes", defaults.newStepsText()),
                defaults.newStepsMinutes
        );
        List<Integer> reviewSteps = Records.LearningStepSettings.parseSteps(
                getStringSetting("review_relearning_steps_minutes", defaults.reviewStepsText()),
                defaults.reviewStepsMinutes
        );
        return new Records.LearningStepSettings(newSteps, reviewSteps);
    }

    public void saveLearningStepSettings(Records.LearningStepSettings settings) {
        Records.LearningStepSettings normalized = settings == null ? Records.LearningStepSettings.defaults() : settings;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting("new_learning_steps_minutes", normalized.newStepsText());
            putStringSetting("review_relearning_steps_minutes", normalized.reviewStepsText());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveLearningRepeat(Records.LearningRepeat repeat) {
        if (repeat == null || repeat.kanji.isEmpty() || repeat.taskType.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("kanji", repeat.kanji);
        values.put("answer_signature", repeat.answerSignature);
        values.put("task_type", repeat.taskType);
        values.put("repeat_type", repeat.repeatType);
        values.put("step_index", repeat.stepIndex);
        values.put("due_at", repeat.dueAtMillis);
        values.put("active_token", repeat.activeToken);
        values.put("created_at", repeat.createdAtMillis);
        values.put("updated_at", repeat.updatedAtMillis);
        getWritableDatabase().insertWithOnConflict("learning_repeats", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void enqueueLearningRepeat(Records.StudyItem item, String taskType, String repeatType, int stepIndex, long dueAtMillis, long nowMillis) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return;
        }
        saveLearningRepeat(new Records.LearningRepeat(
                item.kanji,
                item.answerSignature,
                taskType,
                repeatType,
                stepIndex,
                dueAtMillis,
                "",
                nowMillis,
                nowMillis
        ));
    }

    public void clearLearningRepeat(Records.LearningRepeat repeat) {
        if (repeat == null) {
            return;
        }
        getWritableDatabase().delete(
                "learning_repeats",
                "kanji=? AND answer_signature=? AND task_type=?",
                new String[]{repeat.kanji, repeat.answerSignature, repeat.taskType}
        );
    }

    public List<Records.LearningRepeat> dueLearningRepeats(long nowMillis) {
        List<Records.LearningRepeat> repeats = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                "learning_repeats",
                null,
                "due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, updated_at ASC"
        );
        try {
            while (cursor.moveToNext()) {
                repeats.add(readLearningRepeat(cursor));
            }
        } finally {
            cursor.close();
        }
        return repeats;
    }

    public Records.ReviewStats reviewStatsSince(long sinceMillis) {
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"rating", "writing_required", "writing_passed", "manual_override"},
                "reviewed_at>=?",
                new String[]{Long.toString(sinceMillis)},
                null,
                null,
                null
        );
        int total = 0;
        int again = 0;
        int hard = 0;
        int good = 0;
        int easy = 0;
        int writingRequired = 0;
        int writingFailed = 0;
        try {
            while (cursor.moveToNext()) {
                total++;
                String rating = string(cursor, "rating");
                if ("again".equals(rating)) {
                    again++;
                } else if ("hard".equals(rating)) {
                    hard++;
                } else if ("easy".equals(rating)) {
                    easy++;
                } else {
                    good++;
                }
                boolean required = integer(cursor, "writing_required") == 1;
                boolean passed = integer(cursor, "writing_passed") == 1;
                boolean override = integer(cursor, "manual_override") == 1;
                if (required) {
                    writingRequired++;
                    if (!passed && !override) {
                        writingFailed++;
                    }
                }
            }
        } finally {
            cursor.close();
        }
        return new Records.ReviewStats(total, again, hard, good, easy, writingRequired, writingFailed);
    }

    public List<RecentMistake> recentMistakes(int limit) {
        int boundedLimit = Math.max(1, limit);
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"kanji", "rating", "reviewed_at"},
                "rating IN (?, ?)",
                new String[]{"again", "hard"},
                null,
                null,
                "reviewed_at DESC, id DESC",
                Integer.toString(boundedLimit)
        );
        List<RecentMistake> mistakes = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                mistakes.add(new RecentMistake(
                        string(cursor, "kanji"),
                        string(cursor, "rating"),
                        longValue(cursor, "reviewed_at")
                ));
            }
        } finally {
            cursor.close();
        }
        return mistakes;
    }

    public StudyStreak studyStreak(long nowMillis) {
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"reviewed_at"},
                null,
                null,
                null,
                null,
                "reviewed_at DESC"
        );
        List<Long> days = new ArrayList<>();
        int reviewsToday = 0;
        long today = localDayStart(nowMillis);
        long tomorrow = moveLocalDays(today, 1);
        long lastStudyAt = 0L;
        try {
            long lastAddedDay = Long.MIN_VALUE;
            while (cursor.moveToNext()) {
                long reviewedAt = cursor.getLong(cursor.getColumnIndexOrThrow("reviewed_at"));
                if (lastStudyAt == 0L) {
                    lastStudyAt = reviewedAt;
                }
                if (reviewedAt >= today && reviewedAt < tomorrow) {
                    reviewsToday++;
                }
                long day = localDayStart(reviewedAt);
                if (day != lastAddedDay) {
                    days.add(day);
                    lastAddedDay = day;
                }
            }
        } finally {
            cursor.close();
        }
        if (days.isEmpty()) {
            return new StudyStreak(0, 0, false, 0, 0L);
        }

        long yesterday = moveLocalDays(today, -1);
        boolean studiedToday = days.get(0) == today;
        int current = 0;
        if (studiedToday || days.get(0) == yesterday) {
            long expected = studiedToday ? today : yesterday;
            for (long day : days) {
                if (day != expected) {
                    break;
                }
                current++;
                expected = moveLocalDays(expected, -1);
            }
        }

        int best = 0;
        int run = 0;
        long expectedPrevious = Long.MIN_VALUE;
        for (int i = days.size() - 1; i >= 0; i--) {
            long day = days.get(i);
            if (run == 0 || day == moveLocalDays(expectedPrevious, 1)) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            expectedPrevious = day;
        }
        return new StudyStreak(current, best, studiedToday, reviewsToday, lastStudyAt);
    }

    public StudyImpactStats studyImpactStats() {
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"kanji", "writing_required", "writing_passed", "manual_override"},
                null,
                null,
                null,
                null,
                null
        );
        Set<String> reviewedKanji = new HashSet<>();
        int total = 0;
        int writingRequired = 0;
        int writingPassed = 0;
        int writingFailed = 0;
        int manualOverrides = 0;
        try {
            while (cursor.moveToNext()) {
                total++;
                reviewedKanji.add(string(cursor, "kanji"));
                boolean required = integer(cursor, "writing_required") == 1;
                boolean passed = integer(cursor, "writing_passed") == 1;
                boolean override = integer(cursor, "manual_override") == 1;
                if (required) {
                    writingRequired++;
                    if (passed) {
                        writingPassed++;
                    } else if (!override) {
                        writingFailed++;
                    }
                }
                if (override) {
                    manualOverrides++;
                }
            }
        } finally {
            cursor.close();
        }
        return new StudyImpactStats(total, reviewedKanji.size(), writingRequired, writingPassed, writingFailed, manualOverrides);
    }

    public KanjiImpactAnalyzer.Report kanjiImpactReport() {
        SQLiteDatabase db = getReadableDatabase();
        long latestSyncId = latestSuccessfulSyncId(db);
        if (latestSyncId == 0L) {
            return new KanjiImpactAnalyzer.Report(0, 0, 0, Collections.emptyList());
        }
        Map<String, KanjiImpactAnalyzer.MetricSnapshot> currentByKanji = kanjiMetricsForSync(db, latestSyncId);
        Map<String, Integer> reviewCounts = reviewCountsByKanji(db);
        Set<String> candidates = impactCandidateKanji(db, latestSyncId);
        candidates.addAll(reviewCounts.keySet());
        List<KanjiImpactAnalyzer.KanjiHistory> histories = new ArrayList<>();
        for (String kanji : candidates) {
            HistoricalKanjiSnapshot baseline = baselineKanjiSnapshot(db, kanji);
            KanjiImpactAnalyzer.MetricSnapshot current = currentByKanji.get(kanji);
            SameCardMetrics sameCards = baseline == null || baseline.syncId == latestSyncId
                    ? SameCardMetrics.EMPTY
                    : sameCardMetrics(db, kanji, baseline.syncId, latestSyncId);
            int commonCards = sameCards.current == null ? 0 : sameCards.current.totalCards();
            int currentCards = current == null ? 0 : current.totalCards();
            histories.add(new KanjiImpactAnalyzer.KanjiHistory(
                    kanji,
                    baseline == null ? null : baseline.metrics,
                    current,
                    sameCards.baseline,
                    sameCards.current,
                    commonCards,
                    Math.max(0, currentCards - commonCards),
                    reviewCounts.getOrDefault(kanji, 0)
            ));
        }
        return new KanjiImpactAnalyzer().analyze(histories);
    }

    public Set<String> studiedKanjiSince(long sinceMillis) {
        Cursor cursor = getReadableDatabase().query(
                true,
                "review_log",
                new String[]{"kanji"},
                "reviewed_at>=?",
                new String[]{Long.toString(sinceMillis)},
                null,
                null,
                null,
                null
        );
        Set<String> kanji = new HashSet<>();
        try {
            while (cursor.moveToNext()) {
                kanji.add(string(cursor, "kanji"));
            }
        } finally {
            cursor.close();
        }
        return kanji;
    }

    private void createTimelineTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kanji_timeline_events (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, occurred_at INTEGER NOT NULL, event_type TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, source_expression TEXT NOT NULL, source_reading TEXT NOT NULL, rating TEXT NOT NULL, writing_required INTEGER NOT NULL DEFAULT 0, writing_passed INTEGER NOT NULL DEFAULT 0, manual_override INTEGER NOT NULL DEFAULT 0, weakness_score INTEGER, mature_support_count INTEGER, sync_id INTEGER, dedupe_key TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_timeline_dedupe ON kanji_timeline_events(dedupe_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timeline_kanji_time ON kanji_timeline_events(kanji, occurred_at, id)");
    }

    private void addNullableColumn(SQLiteDatabase db, String table, String column, String type) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (RuntimeException error) {
            if (error.getMessage() == null || !error.getMessage().contains("duplicate column")) {
                throw error;
            }
        }
    }

    private void backfillTimelineEvents(SQLiteDatabase db) {
        Map<String, RowSnapshot> rows = rowSnapshots(db);

        Cursor imports = db.query("suspended_imports", null, null, null, null, null, "first_imported_at ASC, kanji ASC");
        try {
            while (imports.moveToNext()) {
                String kanji = string(imports, "kanji");
                SourceSnapshot source = firstSuspendedSourceForKanji(db, kanji);
                long importedAt = longValue(imports, COLUMN_FIRST_IMPORTED_AT);
                insertTimelineEvent(
                        db,
                        kanji,
                        importedAt == 0L ? System.currentTimeMillis() : importedAt,
                        "suspended_imported",
                        "Imported from suspended Anki",
                        "Kani recovered this kanji from a suspended AnkiDroid card.",
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        null,
                        null,
                        longValue(imports, "last_seen_sync_id"),
                        "suspended_imported:" + kanji
                );
            }
        } finally {
            imports.close();
        }

        for (RowSnapshot row : rows.values()) {
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    "first_seen",
                    "Kani started watching",
                    "This kanji entered Kani from local AnkiDroid evidence.",
                    row.source.expression,
                    row.source.reading,
                    "",
                    false,
                    false,
                    false,
                    row.weaknessScore,
                    row.matureSupportCount,
                    null,
                    "first_seen:" + row.kanji
            );
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    "weak_support_seen",
                    "Weak support seen",
                    supportDetail("Anki evidence still needs repair", row.matureSupportCount, Records.Settings.kikuDefaults().matureSupportThreshold),
                    row.source.expression,
                    row.source.reading,
                    "",
                    false,
                    false,
                    false,
                    row.weaknessScore,
                    row.matureSupportCount,
                    null,
                    "weak_support_seen:" + row.kanji + ":backfill"
            );
        }

        Cursor study = db.query("study_items", null, null, null, null, null, "created_at ASC, kanji ASC");
        try {
            while (study.moveToNext()) {
                String kanji = string(study, "kanji");
                long createdAt = longValue(study, "created_at");
                RowSnapshot row = rows.get(kanji);
                SourceSnapshot source = row == null ? firstExampleForKanji(db, kanji) : row.source;
                if (row == null) {
                    insertTimelineEvent(
                            db,
                            kanji,
                            createdAt == 0L ? System.currentTimeMillis() : createdAt,
                            "first_seen",
                            "Kani started watching",
                            "This kanji has historical Kani study state.",
                            source.expression,
                            source.reading,
                            "",
                            false,
                            false,
                            false,
                            null,
                            null,
                            null,
                            "first_seen:" + kanji
                    );
                }
                if ("retired".equals(string(study, "state"))) {
                    Integer mature = row == null ? null : row.matureSupportCount;
                    insertTimelineEvent(
                            db,
                            kanji,
                            createdAt == 0L ? System.currentTimeMillis() : createdAt,
                            "retired",
                            "Retired by Anki support",
                            mature == null
                                    ? "Kani had already retired this repair before timeline tracking was added."
                                    : supportDetail("Mature Anki support met the target", mature, Records.Settings.kikuDefaults().matureSupportThreshold),
                            source.expression,
                            source.reading,
                            "",
                            false,
                            false,
                            false,
                            row == null ? null : row.weaknessScore,
                            mature,
                            null,
                            "retired:" + kanji + ":backfill"
                    );
                }
            }
        } finally {
            study.close();
        }

        Cursor reviews = db.query("review_log", null, null, null, null, null, "reviewed_at ASC, id ASC");
        try {
            while (reviews.moveToNext()) {
                Records.ReviewRequest request = new Records.ReviewRequest(
                        string(reviews, "kanji"),
                        string(reviews, "token"),
                        string(reviews, "rating"),
                        integer(reviews, "writing_required") == 1,
                        integer(reviews, "writing_passed") == 1,
                        integer(reviews, "manual_override") == 1,
                        0
                );
                appendReviewTimelineEvent(db, request, string(reviews, "rating"), longValue(reviews, "reviewed_at"), "review:" + request.token);
            }
        } finally {
            reviews.close();
        }
    }

    private void appendSyncTimelineEvents(
            SQLiteDatabase db,
            Map<String, RowSnapshot> previousRows,
            List<Records.SuspendedImport> imports,
            List<Records.DashboardRow> rows,
            long syncId,
            long occurredAt,
            Records.Settings settings
    ) {
        int target = settings == null ? Records.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (Records.SuspendedImport imported : imports) {
            SourceSnapshot source = sourceFromImport(imported);
            insertTimelineEvent(
                    db,
                    imported.kanji,
                    occurredAt,
                    "suspended_imported",
                    "Imported from suspended Anki",
                    "Kani recovered this kanji from a suspended AnkiDroid card.",
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    null,
                    null,
                    syncId,
                    "suspended_imported:" + imported.kanji
            );
        }

        for (Records.DashboardRow row : rows) {
            RowSnapshot previous = previousRows.get(row.kanji);
            SourceSnapshot source = sourceForRow(row);
            insertTimelineEvent(
                    db,
                    row.kanji,
                    occurredAt,
                    "first_seen",
                    "Kani started watching",
                    "This kanji entered Kani from local AnkiDroid evidence.",
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    row.weaknessScore,
                    row.matureSupportCount,
                    syncId,
                    "first_seen:" + row.kanji
            );
            if (previous == null) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "weak_support_seen",
                        "Weak support seen",
                        supportDetail("Anki evidence still needs repair", row.matureSupportCount, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "weak_support_seen:" + row.kanji + ":" + syncId
                );
            } else if (row.matureSupportCount > previous.matureSupportCount) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "support_improved",
                        "Anki support improved",
                        "Mature support rose from " + previous.matureSupportCount + " to " + row.matureSupportCount + ".",
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "support_improved:" + row.kanji + ":" + syncId + ":" + previous.matureSupportCount + "-" + row.matureSupportCount
                );
            } else if (row.matureSupportCount < previous.matureSupportCount) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "support_dropped",
                        "Anki support dropped",
                        "Mature support fell from " + previous.matureSupportCount + " to " + row.matureSupportCount + ".",
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "support_dropped:" + row.kanji + ":" + syncId + ":" + previous.matureSupportCount + "-" + row.matureSupportCount
                );
            }
        }
    }

    private void appendStudyStateTimelineEvents(
            SQLiteDatabase db,
            Map<String, StudySnapshot> previousItems,
            List<Records.StudyItem> currentItems,
            long syncId,
            long occurredAt,
            Records.Settings settings
    ) {
        int target = settings == null ? Records.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (Records.StudyItem item : currentItems) {
            StudySnapshot previous = previousItems.get(studyFamilyKey(item.kanji, item.answerSignature));
            if (previous == null) {
                continue;
            }
            RowSnapshot row = rowSnapshot(db, item.kanji);
            SourceSnapshot source = row == null ? firstExampleForKanji(db, item.kanji) : row.source;
            if (!"retired".equals(previous.state) && "retired".equals(item.state)) {
                Integer mature = row == null ? null : row.matureSupportCount;
                insertTimelineEvent(
                        db,
                        item.kanji,
                        occurredAt,
                        "retired",
                        "Retired by Anki support",
                        mature == null
                                ? "No weak Anki evidence remained after sync, so Kani retired this repair."
                                : supportDetail("Mature Anki support met the target", mature, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row == null ? null : row.weaknessScore,
                        mature,
                        syncId,
                        "retired:" + studyTimelineKey(item) + ":" + syncId
                );
            } else if ("retired".equals(previous.state) && !"retired".equals(item.state)) {
                Integer mature = row == null ? null : row.matureSupportCount;
                insertTimelineEvent(
                        db,
                        item.kanji,
                        occurredAt,
                        "reopened",
                        "Repair reopened",
                        mature == null
                                ? "Kani reopened this kanji after sync found weak evidence again."
                                : supportDetail("Mature Anki support fell below target", mature, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row == null ? null : row.weaknessScore,
                        mature,
                        syncId,
                        "reopened:" + studyTimelineKey(item) + ":" + syncId
                );
            }
        }
    }

    private void appendReviewTimelineEvent(SQLiteDatabase db, Records.ReviewRequest request, String appliedRating, long reviewedAt, String dedupeKey) {
        String eventType;
        String title;
        if (request.manualOverride) {
            eventType = "manual_override";
            title = "Manual override";
        } else if ("again".equals(appliedRating) || (request.writingRequired && !request.writingPassed)) {
            eventType = "review_failed";
            title = "Review failed";
        } else {
            eventType = "review_passed";
            title = "Review passed";
        }
        SourceSnapshot source = firstExampleForKanji(db, request.kanji);
        RowSnapshot row = rowSnapshot(db, request.kanji);
        insertTimelineEvent(
                db,
                request.kanji,
                reviewedAt,
                eventType,
                title,
                reviewDetail(request, appliedRating),
                source.expression,
                source.reading,
                appliedRating,
                request.writingRequired,
                request.writingPassed,
                request.manualOverride,
                row == null ? null : row.weaknessScore,
                row == null ? null : row.matureSupportCount,
                null,
                dedupeKey
        );
    }

    private String reviewDetail(Records.ReviewRequest request, String appliedRating) {
        if (request.manualOverride) {
            return "Saved as " + appliedRating + " after manual confirmation.";
        }
        if ("again".equals(appliedRating)) {
            return request.writingRequired
                    ? "Writing missed; Kani scheduled another try."
                    : "Recall missed; Kani scheduled another try.";
        }
        if (request.writingRequired) {
            return request.writingPassed
                    ? "Writing passed and was rated " + appliedRating + "."
                    : "Writing was not passed and was rated " + appliedRating + ".";
        }
        return "Recall review was rated " + appliedRating + ".";
    }

    private String supportDetail(String prefix, int matureSupportCount, int target) {
        return prefix + ": mature support " + matureSupportCount + " / target " + target + ".";
    }

    private void backfillKanjiInventory(SQLiteDatabase db, long nowMillis, Records.Settings settings) {
        rebuildKanjiInventory(db, null, suspendedImportsFromDb(db), dashboardRowsFromDb(db), nowMillis, settings);
    }

    private List<Records.DashboardRow> dashboardRowsFromDb(SQLiteDatabase db) {
        List<Records.DashboardRow> rows = new ArrayList<>();
        Cursor cursor = db.query("dashboard_rows", null, null, null, null, null, "kanji ASC");
        try {
            while (cursor.moveToNext()) {
                rows.add(readDashboardRow(db, cursor));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    private List<Records.SuspendedImport> suspendedImportsFromDb(SQLiteDatabase db) {
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        Cursor cursor = db.query("suspended_imports", null, null, null, null, null, "jiten_rank ASC, kanji ASC");
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, "kanji");
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, "jiten_rank"),
                        integer(cursor, "rank_known") == 1,
                        integer(cursor, "cutoff_used")
                ));
            }
        } finally {
            cursor.close();
        }
        Cursor sources = db.query("suspended_sources", null, null, null, null, null, "kanji ASC, card_id ASC");
        try {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, "kanji"));
                if (imported != null) {
                    imported.sources.add(new Records.SuspendedSource(
                            imported.kanji,
                            longValue(sources, "card_id"),
                            longValue(sources, "note_id"),
                            string(sources, "expression"),
                            string(sources, "reading"),
                            string(sources, "meaning"),
                            string(sources, "sentence")
                    ));
                }
            }
        } finally {
            sources.close();
        }
        List<Records.SuspendedImport> out = new ArrayList<>();
        for (MutableSuspendedImport imported : imports.values()) {
            out.add(imported.build());
        }
        return out;
    }

    private void rebuildKanjiInventory(
            SQLiteDatabase db,
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> imports,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Records.Settings settings
    ) {
        Map<String, MutableKanjiInventoryItem> inventory = new LinkedHashMap<>();
        if (snapshot != null) {
            ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
            for (Records.Note note : snapshot.notes) {
                if (!activeIndex.noteIds.contains(note.noteId)) {
                    continue;
                }
                String expression = TextUtil.normalizeJapanese(note.expression(settings));
                String reading = TextUtil.normalizeJapanese(note.reading(settings));
                String meaning = TextUtil.firstMeaningLine(note.meaning(settings));
                String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
                addInventoryText(inventory, TextUtil.extractKanji(expression + " " + sentence), meaning, reading, expression, sentence);
            }
        }
        for (Records.SuspendedImport imported : imports) {
            MutableKanjiInventoryItem item = inventoryItem(inventory, imported.kanji);
            for (Records.SuspendedSource source : imported.sources) {
                item.add(source.meaning, source.reading, source.expression, source.sentence);
            }
        }
        for (Records.DashboardRow row : rows) {
            MutableKanjiInventoryItem item = inventoryItem(inventory, row.kanji);
            item.add(row.primaryMeaning, row.reading, row.reasonText, row.browserSearch);
            item.browserSearch = row.browserSearch;
            for (Records.Example example : row.examples) {
                item.exampleCount++;
                item.add(example.meaning, example.reading, example.expression, example.sentence);
            }
        }
        Cursor study = db.query("study_items", new String[]{"kanji"}, null, null, null, null, null);
        try {
            while (study.moveToNext()) {
                inventoryItem(inventory, string(study, "kanji"));
            }
        } finally {
            study.close();
        }
        Cursor reviews = db.query(true, "review_log", new String[]{"kanji"}, null, null, null, null, null, null);
        try {
            while (reviews.moveToNext()) {
                inventoryItem(inventory, string(reviews, "kanji"));
            }
        } finally {
            reviews.close();
        }
        Cursor timeline = db.query(true, "kanji_timeline_events", new String[]{"kanji"}, null, null, null, null, null, null);
        try {
            while (timeline.moveToNext()) {
                inventoryItem(inventory, string(timeline, "kanji"));
            }
        } finally {
            timeline.close();
        }
        for (MutableKanjiInventoryItem item : inventory.values()) {
            if (item.kanji.isEmpty()) {
                continue;
            }
            Records.KanjiInventoryItem previous = readInventoryItem(db, item.kanji);
            ContentValues values = new ContentValues();
            values.put("kanji", item.kanji);
            values.put("primary_meaning", firstNonEmpty(item.primaryMeaning, previous == null ? "" : previous.primaryMeaning));
            values.put("readings", item.readingsText(previous == null ? "" : previous.readings));
            values.put("browser_search", firstNonEmpty(item.browserSearch, previous == null ? TextUtil.browserSearchForKanji(item.kanji, settings) : previous.browserSearch));
            values.put("search_text", item.searchText(previous));
            values.put("source_count", Math.max(item.sourceCount, previous == null ? 0 : previous.sourceCount));
            values.put("example_count", Math.max(item.exampleCount, previous == null ? 0 : previous.exampleCount));
            values.put("first_seen_at", previous == null ? nowMillis : previous.lastSeenAtMillis);
            values.put("last_seen_at", nowMillis);
            db.insertWithOnConflict("kanji_inventory", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void rebuildSimilarKanjiPairs(SQLiteDatabase db, SimilarKanjiIndex similarIndex, long nowMillis) {
        Map<String, Long> firstSeenByPair = similarPairFirstSeen(db);
        List<SimilarKanjiIndex.Pair> localPairs = similarIndex.pairsWithin(localInventoryKanji(db));
        db.delete("similar_kanji_pairs", null, null);
        for (SimilarKanjiIndex.Pair pair : localPairs) {
            ContentValues values = new ContentValues();
            values.put("kanji_a", pair.kanjiA);
            values.put("kanji_b", pair.kanjiB);
            values.put("source", pair.source);
            values.put("first_seen_at", firstSeenByPair.getOrDefault(similarKey(pair.kanjiA, pair.kanjiB, pair.source), nowMillis));
            values.put("last_seen_at", nowMillis);
            db.insertWithOnConflict("similar_kanji_pairs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void rebuildSimilarKanjiChoiceStates(SQLiteDatabase db, long nowMillis) {
        createSimilarKanjiPracticeTables(db);
        Map<String, SimilarChoiceSnapshot> previous = similarChoiceSnapshots(db);
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
        List<Records.SimilarKanjiChoiceCard> candidates = planner.buildCandidates(
                allInventoryItems(db),
                allSimilarPairs(db)
        );
        Set<String> currentKeys = new HashSet<>();
        for (Records.SimilarKanjiChoiceCard card : candidates) {
            String key = similarChoiceKey(card.targetKanji, card.choiceSignature);
            currentKeys.add(key);
            SimilarChoiceSnapshot old = previous.get(key);
            ContentValues values = new ContentValues();
            values.put("target_kanji", card.targetKanji);
            values.put("choice_signature", card.choiceSignature);
            values.put("primary_meaning", card.primaryMeaning);
            values.put("choices", serializeChoices(card.choices));
            values.put("due_at", old == null ? 0L : old.dueAtMillis);
            values.put("passed_at", old == null ? 0L : old.passedAtMillis);
            values.put("last_reviewed_at", old == null ? 0L : old.lastReviewedAtMillis);
            values.put("correct_count", old == null ? 0 : old.correctCount);
            values.put("wrong_count", old == null ? 0 : old.wrongCount);
            values.put("active_token", "");
            values.put("first_seen_at", old == null ? nowMillis : old.firstSeenAtMillis);
            values.put("last_seen_at", nowMillis);
            db.insertWithOnConflict("similar_kanji_choice_state", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        for (String key : previous.keySet()) {
            if (currentKeys.contains(key)) {
                continue;
            }
            String[] parts = key.split("\u0001", 2);
            if (parts.length != 2) {
                continue;
            }
            db.delete(
                    "similar_kanji_choice_state",
                    "target_kanji=? AND choice_signature=?",
                    new String[]{parts[0], parts[1]}
            );
            db.delete(
                    "similar_kanji_repair_queue",
                    "status=? AND target_kanji=? AND choice_signature=?",
                    new String[]{"pending", parts[0], parts[1]}
            );
        }
    }

    private Map<String, Long> similarPairFirstSeen(SQLiteDatabase db) {
        Map<String, Long> out = new HashMap<>();
        Cursor cursor = db.query("similar_kanji_pairs", new String[]{"kanji_a", "kanji_b", "source", "first_seen_at"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                out.put(
                        similarKey(string(cursor, "kanji_a"), string(cursor, "kanji_b"), string(cursor, "source")),
                        longValue(cursor, "first_seen_at")
                );
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private Set<String> localInventoryKanji(SQLiteDatabase db) {
        Set<String> out = new HashSet<>();
        Cursor cursor = db.query("kanji_inventory", new String[]{"kanji"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String kanji = normalizeSingleKanji(string(cursor, "kanji"));
                if (!kanji.isEmpty()) {
                    out.add(kanji);
                }
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private Records.SimilarKanjiPair readSimilarPair(Cursor cursor) {
        return new Records.SimilarKanjiPair(
                string(cursor, "kanji_a"),
                string(cursor, "kanji_b"),
                string(cursor, "source"),
                longValue(cursor, "first_seen_at"),
                longValue(cursor, "last_seen_at")
        );
    }

    private List<Records.SimilarKanjiPair> allSimilarPairs(SQLiteDatabase db) {
        List<Records.SimilarKanjiPair> out = new ArrayList<>();
        Cursor cursor = db.query("similar_kanji_pairs", null, null, null, null, null, "kanji_a ASC, kanji_b ASC, source ASC");
        try {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private List<Records.KanjiInventoryItem> allInventoryItems(SQLiteDatabase db) {
        List<Records.KanjiInventoryItem> out = new ArrayList<>();
        Cursor cursor = db.query("kanji_inventory", null, null, null, null, null, "kanji ASC");
        try {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private Map<String, SimilarChoiceSnapshot> similarChoiceSnapshots(SQLiteDatabase db) {
        Map<String, SimilarChoiceSnapshot> out = new HashMap<>();
        Cursor cursor = db.query("similar_kanji_choice_state", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String target = string(cursor, "target_kanji");
                String signature = string(cursor, "choice_signature");
                out.put(
                        similarChoiceKey(target, signature),
                        new SimilarChoiceSnapshot(
                                longValue(cursor, "due_at"),
                                longValue(cursor, "passed_at"),
                                longValue(cursor, "last_reviewed_at"),
                                integer(cursor, "correct_count"),
                                integer(cursor, "wrong_count"),
                                longValue(cursor, "first_seen_at")
                        )
                );
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private Records.SimilarKanjiChoiceCard similarChoiceCard(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        Cursor cursor = db.query(
                "similar_kanji_choice_state",
                null,
                "target_kanji=? AND choice_signature=?",
                new String[]{targetKanji, choiceSignature},
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst() ? readSimilarChoiceCard(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.SimilarKanjiChoiceCard readSimilarChoiceCard(Cursor cursor) {
        return new Records.SimilarKanjiChoiceCard(
                string(cursor, "target_kanji"),
                string(cursor, "primary_meaning"),
                deserializeChoices(string(cursor, "choices")),
                string(cursor, "choice_signature"),
                longValue(cursor, "due_at"),
                longValue(cursor, "passed_at"),
                longValue(cursor, "last_reviewed_at"),
                integer(cursor, "correct_count"),
                integer(cursor, "wrong_count")
        );
    }

    private boolean hasPendingSimilarRepairs(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        Cursor cursor = db.query(
                "similar_kanji_repair_queue",
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=?",
                new String[]{"pending", targetKanji, choiceSignature},
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private void enqueueSimilarWritingRepair(
            SQLiteDatabase db,
            Records.SimilarKanjiChoiceCard card,
            String repairKanji,
            String wrongSelection,
            long nowMillis
    ) {
        String normalized = normalizeSingleKanji(repairKanji);
        if (normalized.isEmpty()) {
            return;
        }
        Cursor pending = db.query(
                "similar_kanji_repair_queue",
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=? AND repair_kanji=?",
                new String[]{"pending", card.targetKanji, card.choiceSignature, normalized},
                null,
                null,
                null,
                "1"
        );
        try {
            if (pending.moveToFirst()) {
                return;
            }
        } finally {
            pending.close();
        }
        ContentValues values = new ContentValues();
        values.put("target_kanji", card.targetKanji);
        values.put("repair_kanji", normalized);
        values.put("choice_signature", card.choiceSignature);
        values.put("wrong_selection", wrongSelection == null ? "" : wrongSelection);
        values.put("prompt_meaning", card.primaryMeaning);
        values.put("status", "pending");
        values.put("due_at", nowMillis);
        values.put("active_token", "");
        values.put("attempts", 0);
        values.put("created_at", nowMillis);
        values.put("updated_at", nowMillis);
        values.put("completed_at", 0L);
        db.insert("similar_kanji_repair_queue", null, values);
    }

    private Records.SimilarKanjiWritingRepair similarWritingRepair(SQLiteDatabase db, long repairId) {
        Cursor cursor = db.query(
                "similar_kanji_repair_queue",
                null,
                "id=?",
                new String[]{Long.toString(repairId)},
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst() ? readSimilarWritingRepair(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.SimilarKanjiWritingRepair readSimilarWritingRepair(Cursor cursor) {
        return new Records.SimilarKanjiWritingRepair(
                longValue(cursor, "id"),
                string(cursor, "target_kanji"),
                string(cursor, "repair_kanji"),
                string(cursor, "choice_signature"),
                string(cursor, "wrong_selection"),
                string(cursor, "prompt_meaning"),
                string(cursor, "status"),
                longValue(cursor, "due_at"),
                string(cursor, "active_token"),
                integer(cursor, "attempts"),
                longValue(cursor, "created_at"),
                longValue(cursor, "updated_at"),
                longValue(cursor, "completed_at")
        );
    }

    private static String serializeChoices(List<String> choices) {
        return String.join("\t", choices == null ? Collections.emptyList() : choices);
    }

    private static List<String> deserializeChoices(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        String[] parts = encoded.split("\t", -1);
        for (String part : parts) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private void addInventoryText(Map<String, MutableKanjiInventoryItem> inventory, List<String> kanji, String meaning, String reading, String expression, String sentence) {
        for (String glyph : kanji) {
            inventoryItem(inventory, glyph).add(meaning, reading, expression, sentence);
        }
    }

    private MutableKanjiInventoryItem inventoryItem(Map<String, MutableKanjiInventoryItem> inventory, String kanji) {
        MutableKanjiInventoryItem item = inventory.get(kanji);
        if (item == null) {
            item = new MutableKanjiInventoryItem(kanji);
            inventory.put(kanji, item);
        }
        return item;
    }

    private Records.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("kanji_inventory", null, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? readInventoryItem(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, "kanji");
        return new Records.KanjiInventoryItem(
                kanji,
                string(cursor, "primary_meaning"),
                string(cursor, "readings"),
                string(cursor, "browser_search"),
                integer(cursor, "source_count"),
                integer(cursor, "example_count"),
                isKanjiSuspended(db, kanji),
                longValue(cursor, "last_seen_at")
        );
    }

    private boolean isKanjiSuspended(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("local_kanji_suspensions", new String[]{"kanji"}, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }

    private static String normalizeSingleKanji(String value) {
        String normalized = TextUtil.normalizeJapanese(value);
        if (normalized.codePointCount(0, normalized.length()) != 1) {
            return "";
        }
        return TextUtil.isKanji(normalized.codePointAt(0)) ? normalized : "";
    }

    private static String[] canonicalSimilarPair(String first, String second) {
        if (first.compareTo(second) <= 0) {
            return new String[]{first, second};
        }
        return new String[]{second, first};
    }

    private static String similarKey(String first, String second, String source) {
        return first + "\u0000" + second + "\u0000" + source;
    }

    private static String similarChoiceKey(String targetKanji, String choiceSignature) {
        return targetKanji + "\u0001" + (choiceSignature == null ? "" : choiceSignature);
    }

    private Records.DashboardRow readDashboardRow(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("dashboard_rows", null, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readDashboardRow(db, cursor);
        } finally {
            cursor.close();
        }
    }

    private Records.DashboardRow readDashboardRow(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, "kanji");
        return new Records.DashboardRow(
                kanji,
                nullableInt(cursor, "jiten_rank"),
                string(cursor, "primary_meaning"),
                string(cursor, "reading"),
                string(cursor, "browser_search"),
                integer(cursor, "weakness_score"),
                string(cursor, "reason_code"),
                string(cursor, "reason_text"),
                integer(cursor, "active_example_count"),
                integer(cursor, "suspended_example_count"),
                integer(cursor, "mature_support_count"),
                examplesForKanji(db, kanji)
        );
    }

    private Records.StudyItem studyItemForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("study_items", null, "kanji=?", new String[]{kanji}, null, null, "state='retired' ASC, due_at ASC", "1");
        try {
            return cursor.moveToFirst() ? readStudyItem(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.KanjiTimelineEvent readTimelineEvent(Cursor cursor) {
        return new Records.KanjiTimelineEvent(
                longValue(cursor, "id"),
                string(cursor, "kanji"),
                longValue(cursor, "occurred_at"),
                string(cursor, "event_type"),
                string(cursor, "title"),
                string(cursor, "detail"),
                string(cursor, "source_expression"),
                string(cursor, "source_reading"),
                string(cursor, "rating"),
                integer(cursor, "writing_required") == 1,
                integer(cursor, "writing_passed") == 1,
                integer(cursor, "manual_override") == 1,
                nullableInt(cursor, "weakness_score"),
                nullableInt(cursor, "mature_support_count"),
                nullableLong(cursor, "sync_id"),
                string(cursor, "dedupe_key")
        );
    }

    private void insertTimelineEvent(
            SQLiteDatabase db,
            String kanji,
            long occurredAt,
            String eventType,
            String title,
            String detail,
            String sourceExpression,
            String sourceReading,
            String rating,
            boolean writingRequired,
            boolean writingPassed,
            boolean manualOverride,
            Integer weaknessScore,
            Integer matureSupportCount,
            Long syncId,
            String dedupeKey
    ) {
        ContentValues values = new ContentValues();
        values.put("kanji", kanji);
        values.put("occurred_at", occurredAt);
        values.put("event_type", eventType == null ? "" : eventType);
        values.put("title", title == null ? "" : title);
        values.put("detail", detail == null ? "" : detail);
        values.put("source_expression", sourceExpression == null ? "" : sourceExpression);
        values.put("source_reading", sourceReading == null ? "" : sourceReading);
        values.put("rating", rating == null ? "" : rating);
        values.put("writing_required", writingRequired ? 1 : 0);
        values.put("writing_passed", writingPassed ? 1 : 0);
        values.put("manual_override", manualOverride ? 1 : 0);
        if (weaknessScore == null) {
            values.putNull("weakness_score");
        } else {
            values.put("weakness_score", weaknessScore);
        }
        if (matureSupportCount == null) {
            values.putNull("mature_support_count");
        } else {
            values.put("mature_support_count", matureSupportCount);
        }
        if (syncId == null) {
            values.putNull("sync_id");
        } else {
            values.put("sync_id", syncId);
        }
        values.put("dedupe_key", dedupeKey);
        db.insertWithOnConflict("kanji_timeline_events", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private Map<String, RowSnapshot> rowSnapshots(SQLiteDatabase db) {
        Map<String, RowSnapshot> rows = new LinkedHashMap<>();
        Cursor cursor = db.query("dashboard_rows", null, null, null, null, null, "kanji ASC");
        try {
            while (cursor.moveToNext()) {
                RowSnapshot row = rowSnapshotFromCursor(db, cursor);
                rows.put(row.kanji, row);
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    private RowSnapshot rowSnapshot(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("dashboard_rows", null, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? rowSnapshotFromCursor(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private RowSnapshot rowSnapshotFromCursor(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, "kanji");
        return new RowSnapshot(
                kanji,
                integer(cursor, "weakness_score"),
                integer(cursor, "mature_support_count"),
                longValue(cursor, "rebuilt_at"),
                firstExampleForKanji(db, kanji)
        );
    }

    private Map<String, StudySnapshot> studySnapshots(SQLiteDatabase db) {
        Map<String, StudySnapshot> items = new HashMap<>();
        Cursor cursor = db.query("study_items", new String[]{"kanji", "answer_signature", "state"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, "kanji");
                String answerSignature = string(cursor, "answer_signature");
                items.put(studyFamilyKey(kanji, answerSignature), new StudySnapshot(string(cursor, "state")));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private SourceSnapshot firstExampleForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("kanji_examples", new String[]{"expression", "reading"}, "kanji=?", new String[]{kanji}, null, null, "source_type ASC, id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, "expression"), string(cursor, "reading"));
        } finally {
            cursor.close();
        }
    }

    private SourceSnapshot firstSuspendedSourceForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("suspended_sources", new String[]{"expression", "reading"}, "kanji=?", new String[]{kanji}, null, null, "card_id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, "expression"), string(cursor, "reading"));
        } finally {
            cursor.close();
        }
    }

    private SourceSnapshot sourceFromImport(Records.SuspendedImport imported) {
        if (imported.sources.isEmpty()) {
            return SourceSnapshot.EMPTY;
        }
        Records.SuspendedSource source = imported.sources.get(0);
        return new SourceSnapshot(source.expression, source.reading);
    }

    private SourceSnapshot sourceForRow(Records.DashboardRow row) {
        Records.Example fallback = null;
        for (Records.Example example : row.examples) {
            if ("active".equals(example.sourceType)) {
                return new SourceSnapshot(example.expression, example.reading);
            }
            if (fallback == null) {
                fallback = example;
            }
        }
        return fallback == null ? SourceSnapshot.EMPTY : new SourceSnapshot(fallback.expression, fallback.reading);
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
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void putSetting(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private long insertSyncRun(SQLiteDatabase db, long startedAt, long finishedAt, String status, ActiveCardIndex activeIndex, int importCount, String errorCode, String errorMessage, String removalMessage, int deletedNotes, int deletedCards) {
        ContentValues values = new ContentValues();
        values.put("started_at", startedAt);
        values.put("finished_at", finishedAt);
        values.put("status", status);
        values.put("active_notes_count", activeIndex.noteIds.size());
        values.put("active_cards_count", activeIndex.activeCardCount);
        values.put("suspended_cards_archived_count", activeIndex.suspendedCardCount);
        values.put("suspended_kanji_imported_count", importCount);
        values.put("deleted_notes_count", deletedNotes);
        values.put("deleted_cards_count", deletedCards);
        values.put("error_code", errorCode);
        values.put("error_message", errorMessage);
        values.put("removal_message", removalMessage);
        return db.insert("sync_runs", null, values);
    }

    private void saveRows(SQLiteDatabase db, List<Records.DashboardRow> rows, long rebuiltAt) {
        for (Records.DashboardRow row : rows) {
            ContentValues values = new ContentValues();
            values.put("kanji", row.kanji);
            if (row.jitenRank != null) {
                values.put("jiten_rank", row.jitenRank);
            }
            values.put("primary_meaning", row.primaryMeaning);
            values.put("reading", row.reading);
            values.put("browser_search", row.browserSearch);
            values.put("weakness_score", row.weaknessScore);
            values.put("reason_code", row.reasonCode);
            values.put("reason_text", row.reasonText);
            values.put("active_example_count", row.activeExampleCount);
            values.put("suspended_example_count", row.suspendedExampleCount);
            values.put("mature_support_count", row.matureSupportCount);
            values.put("rebuilt_at", rebuiltAt);
            db.insertWithOnConflict("dashboard_rows", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            for (Records.Example example : row.examples) {
                ContentValues ex = new ContentValues();
                ex.put("kanji", row.kanji);
                ex.put("source_type", example.sourceType);
                ex.put("card_id", example.cardId);
                ex.put("note_id", example.noteId);
                ex.put("expression", example.expression);
                ex.put("reading", example.reading);
                ex.put("meaning", example.meaning);
                ex.put("sentence", example.sentence);
                ex.put("mature", example.mature ? 1 : 0);
                ex.put("lapses", example.lapses);
                ex.put("interval_days", example.intervalDays);
                ex.put("reps", example.reps);
                putNullableDouble(ex, "fsrs_stability", example.fsrsStability);
                putNullableDouble(ex, "fsrs_difficulty", example.fsrsDifficulty);
                putNullableDouble(ex, "fsrs_retrievability", example.fsrsRetrievability);
                db.insert("kanji_examples", null, ex);
            }
        }
    }

    private void appendHistoricalSyncSnapshots(
            SQLiteDatabase db,
            Records.CollectionSnapshot snapshot,
            Map<Long, Records.Note> notesById,
            List<Records.DashboardRow> rows,
            Records.Settings settings,
            long syncId,
            SyncTiming timing
    ) {
        createHistoricalSyncTables(db);
        Map<Long, LinkedHashSet<String>> deckIdsByNote = deckIdsByNote(snapshot.cards);
        Map<Long, LinkedHashSet<String>> deckNamesByNote = deckNamesByNote(snapshot.cards);
        Map<String, HistoricalKanjiAggregate> aggregates = new LinkedHashMap<>();

        for (Records.Card card : snapshot.cards) {
            Records.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            ContentValues cardValues = new ContentValues();
            cardValues.put("sync_id", syncId);
            cardValues.put("started_at", timing.startedAt);
            cardValues.put("finished_at", timing.finishedAt);
            cardValues.put("card_id", card.cardId);
            cardValues.put("note_id", card.noteId);
            cardValues.put("deck_id", card.deckId);
            cardValues.put("deck_name", card.deckName);
            cardValues.put("model_id", note.modelId);
            cardValues.put("model_name", note.modelName);
            cardValues.put("ord", card.ord);
            cardValues.put("queue", card.queue);
            cardValues.put("type", card.type);
            cardValues.put("due", card.due);
            cardValues.put("interval_days", card.intervalDays);
            cardValues.put("reps", card.reps);
            cardValues.put("lapses", card.lapses);
            cardValues.put("suspended", card.suspended ? 1 : 0);
            putNullableDouble(cardValues, "fsrs_stability", card.fsrsStability);
            putNullableDouble(cardValues, "fsrs_difficulty", card.fsrsDifficulty);
            putNullableDouble(cardValues, "fsrs_retrievability", card.fsrsRetrievability);
            cardValues.put("mature", card.mature(settings.matureDays) ? 1 : 0);
            db.insertWithOnConflict("sync_card_snapshots", null, cardValues, SQLiteDatabase.CONFLICT_REPLACE);

            for (String kanji : extractedKanji(note, settings)) {
                aggregateFor(aggregates, kanji).add(card, settings.matureDays);
            }
        }

        for (Records.Note note : snapshot.notes) {
            LinkedHashSet<String> deckIds = deckIdsByNote.get(note.noteId);
            LinkedHashSet<String> decks = deckNamesByNote.get(note.noteId);
            if (decks == null || decks.isEmpty()) {
                continue;
            }
            String expression = TextUtil.normalizeJapanese(note.expression(settings));
            String reading = TextUtil.normalizeJapanese(note.reading(settings));
            String meaning = TextUtil.firstMeaningLine(note.meaning(settings));
            String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
            ContentValues noteValues = new ContentValues();
            noteValues.put("sync_id", syncId);
            noteValues.put("finished_at", timing.finishedAt);
            noteValues.put("note_id", note.noteId);
            noteValues.put("model_id", note.modelId);
            noteValues.put("model_name", note.modelName);
            noteValues.put("deck_ids", deckIds == null ? "" : String.join(" ", deckIds));
            noteValues.put("deck_names", String.join(" ", decks));
            noteValues.put("expression", expression);
            noteValues.put("reading", reading);
            noteValues.put("meaning", meaning);
            noteValues.put("sentence", sentence);
            noteValues.put("tags", String.join(" ", note.tags));
            noteValues.put("fields_json", fieldsJson(note.fields));
            noteValues.put("extracted_kanji", String.join("", TextUtil.extractKanji(expression + " " + sentence)));
            db.insertWithOnConflict("sync_note_snapshots", null, noteValues, SQLiteDatabase.CONFLICT_REPLACE);
        }

        overlayDashboardRows(aggregates, rows);
        insertHistoricalKanjiAggregates(db, syncId, timing.finishedAt, aggregates);
    }

    private void backfillLatestHistoricalSync(SQLiteDatabase db) {
        if (tableHasRows(db, "sync_kanji_snapshots")) {
            return;
        }
        HistoricalSyncRun sync = latestSuccessfulSyncRun(db);
        if (sync == null) {
            return;
        }
        Records.Settings settings = Records.Settings.kikuDefaults();
        Map<Long, HistoricalNoteSnapshot> notes = currentSourceNotes(db);
        if (notes.isEmpty()) {
            return;
        }
        Map<Long, LinkedHashSet<String>> deckIdsByNote = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<String>> deckNamesByNote = new LinkedHashMap<>();
        Map<String, HistoricalKanjiAggregate> aggregates = new LinkedHashMap<>();
        Cursor cards = db.query("source_cards", null, null, null, null, null, "card_id ASC");
        try {
            while (cards.moveToNext()) {
                long noteId = longValue(cards, "note_id");
                HistoricalNoteSnapshot note = notes.get(noteId);
                if (note == null) {
                    continue;
                }
                long cardId = longValue(cards, "card_id");
                String deck = string(cards, "deck_name");
                linkedSetFor(deckIdsByNote, noteId).add(deck);
                linkedSetFor(deckNamesByNote, noteId).add(deck);
                int intervalDays = integer(cards, "interval_days");
                int reps = integer(cards, "reps");
                int lapses = integer(cards, "lapses");
                boolean mature = intervalDays >= settings.matureDays;

                ContentValues cardValues = new ContentValues();
                cardValues.put("sync_id", sync.id);
                cardValues.put("started_at", sync.startedAt);
                cardValues.put("finished_at", sync.finishedAt);
                cardValues.put("card_id", cardId);
                cardValues.put("note_id", noteId);
                cardValues.put("deck_id", deck);
                cardValues.put("deck_name", deck);
                cardValues.put("model_id", note.modelId);
                cardValues.put("model_name", note.modelName);
                cardValues.put("ord", integer(cards, "ord"));
                cardValues.put("queue", integer(cards, "queue"));
                cardValues.put("type", integer(cards, "type"));
                cardValues.put("due", integer(cards, "due"));
                cardValues.put("interval_days", intervalDays);
                cardValues.put("reps", reps);
                cardValues.put("lapses", lapses);
                cardValues.put("suspended", 0);
                putNullableDouble(cardValues, "fsrs_stability", nullableDouble(cards, "fsrs_stability"));
                putNullableDouble(cardValues, "fsrs_difficulty", nullableDouble(cards, "fsrs_difficulty"));
                putNullableDouble(cardValues, "fsrs_retrievability", nullableDouble(cards, "fsrs_retrievability"));
                cardValues.put("mature", mature ? 1 : 0);
                db.insertWithOnConflict("sync_card_snapshots", null, cardValues, SQLiteDatabase.CONFLICT_REPLACE);

                for (String kanji : TextUtil.extractKanji(note.expression + " " + note.sentence)) {
                    aggregateFor(aggregates, kanji).add(
                            intervalDays,
                            reps,
                            lapses,
                            false,
                            mature,
                            nullableDouble(cards, "fsrs_stability"),
                            nullableDouble(cards, "fsrs_difficulty"),
                            nullableDouble(cards, "fsrs_retrievability")
                    );
                }
            }
        } finally {
            cards.close();
        }
        for (HistoricalNoteSnapshot note : notes.values()) {
            LinkedHashSet<String> deckIds = deckIdsByNote.get(note.noteId);
            LinkedHashSet<String> decks = deckNamesByNote.get(note.noteId);
            if (decks == null || decks.isEmpty()) {
                continue;
            }
            ContentValues noteValues = new ContentValues();
            noteValues.put("sync_id", sync.id);
            noteValues.put("finished_at", sync.finishedAt);
            noteValues.put("note_id", note.noteId);
            noteValues.put("model_id", note.modelId);
            noteValues.put("model_name", note.modelName);
            noteValues.put("deck_ids", deckIds == null ? "" : String.join(" ", deckIds));
            noteValues.put("deck_names", String.join(" ", decks));
            noteValues.put("expression", note.expression);
            noteValues.put("reading", note.reading);
            noteValues.put("meaning", note.meaning);
            noteValues.put("sentence", note.sentence);
            noteValues.put("tags", note.tags);
            noteValues.put("fields_json", note.fieldsJson);
            noteValues.put("extracted_kanji", String.join("", TextUtil.extractKanji(note.expression + " " + note.sentence)));
            db.insertWithOnConflict("sync_note_snapshots", null, noteValues, SQLiteDatabase.CONFLICT_REPLACE);
        }
        overlayDashboardRows(aggregates, currentDashboardRows(db));
        insertHistoricalKanjiAggregates(db, sync.id, sync.finishedAt, aggregates);
    }

    private Map<Long, LinkedHashSet<String>> deckNamesByNote(List<Records.Card> cards) {
        Map<Long, LinkedHashSet<String>> out = new LinkedHashMap<>();
        for (Records.Card card : cards) {
            linkedSetFor(out, card.noteId).add(card.deckName);
        }
        return out;
    }

    private Map<Long, LinkedHashSet<String>> deckIdsByNote(List<Records.Card> cards) {
        Map<Long, LinkedHashSet<String>> out = new LinkedHashMap<>();
        for (Records.Card card : cards) {
            linkedSetFor(out, card.noteId).add(card.deckId);
        }
        return out;
    }

    private LinkedHashSet<String> linkedSetFor(Map<Long, LinkedHashSet<String>> map, long key) {
        LinkedHashSet<String> values = map.get(key);
        if (values == null) {
            values = new LinkedHashSet<>();
            map.put(key, values);
        }
        return values;
    }

    private List<String> extractedKanji(Records.Note note, Records.Settings settings) {
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
        return TextUtil.extractKanji(expression + " " + sentence);
    }

    private HistoricalKanjiAggregate aggregateFor(Map<String, HistoricalKanjiAggregate> aggregates, String kanji) {
        HistoricalKanjiAggregate aggregate = aggregates.get(kanji);
        if (aggregate == null) {
            aggregate = new HistoricalKanjiAggregate(kanji);
            aggregates.put(kanji, aggregate);
        }
        return aggregate;
    }

    private void overlayDashboardRows(Map<String, HistoricalKanjiAggregate> aggregates, List<Records.DashboardRow> rows) {
        for (Records.DashboardRow row : rows) {
            HistoricalKanjiAggregate aggregate = aggregateFor(aggregates, row.kanji);
            aggregate.weaknessScore = row.weaknessScore;
            aggregate.reasonCode = row.reasonCode;
            aggregate.activeExampleCount = Math.max(aggregate.activeExampleCount, row.activeExampleCount);
            aggregate.suspendedExampleCount = Math.max(aggregate.suspendedExampleCount, row.suspendedExampleCount);
            aggregate.matureSupportCount = Math.max(aggregate.matureSupportCount, row.matureSupportCount);
        }
    }

    private void insertHistoricalKanjiAggregates(SQLiteDatabase db, long syncId, long finishedAt, Map<String, HistoricalKanjiAggregate> aggregates) {
        for (HistoricalKanjiAggregate aggregate : aggregates.values()) {
            if (aggregate.kanji.isEmpty()) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("sync_id", syncId);
            values.put("finished_at", finishedAt);
            values.put("kanji", aggregate.kanji);
            values.put("active_cards", aggregate.activeCards);
            values.put("suspended_cards", aggregate.suspendedCards);
            values.put("mature_support_count", aggregate.matureSupportCount);
            values.put("average_interval_days", aggregate.averageIntervalDays());
            values.put("total_lapses", aggregate.totalLapses);
            values.put("total_reps", aggregate.totalReps);
            putNullableDouble(values, "fsrs_stability_avg", aggregate.averageStability());
            putNullableDouble(values, "fsrs_difficulty_avg", aggregate.averageDifficulty());
            putNullableDouble(values, "fsrs_retrievability_avg", aggregate.averageRetrievability());
            values.put("weakness_score", aggregate.weaknessScore);
            values.put("reason_code", aggregate.reasonCode);
            values.put("active_example_count", aggregate.activeExampleCount);
            values.put("suspended_example_count", aggregate.suspendedExampleCount);
            db.insertWithOnConflict("sync_kanji_snapshots", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private boolean tableHasRows(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + table + " LIMIT 1", null);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private HistoricalSyncRun latestSuccessfulSyncRun(SQLiteDatabase db) {
        Cursor cursor = db.query(
                "sync_runs",
                new String[]{"id", "started_at", "finished_at"},
                "status=?",
                new String[]{STATUS_SUCCESS},
                null,
                null,
                "id DESC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalSyncRun(
                    longValue(cursor, "id"),
                    longValue(cursor, "started_at"),
                    longValue(cursor, "finished_at")
            );
        } finally {
            cursor.close();
        }
    }

    private Map<Long, HistoricalNoteSnapshot> currentSourceNotes(SQLiteDatabase db) {
        Map<Long, HistoricalNoteSnapshot> notes = new LinkedHashMap<>();
        Cursor cursor = db.query("source_notes", null, null, null, null, null, "note_id ASC");
        try {
            while (cursor.moveToNext()) {
                long noteId = longValue(cursor, "note_id");
                notes.put(noteId, new HistoricalNoteSnapshot(
                        noteId,
                        0L,
                        string(cursor, "model_name"),
                        string(cursor, "expression"),
                        string(cursor, "reading"),
                        string(cursor, "meaning"),
                        string(cursor, "sentence"),
                        string(cursor, "tags"),
                        string(cursor, "fields_json")
                ));
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    private List<Records.DashboardRow> currentDashboardRows(SQLiteDatabase db) {
        List<Records.DashboardRow> rows = new ArrayList<>();
        Cursor cursor = db.query("dashboard_rows", new String[]{"kanji"}, null, null, null, null, "kanji ASC");
        try {
            while (cursor.moveToNext()) {
                Records.DashboardRow row = readDashboardRow(db, string(cursor, "kanji"));
                if (row != null) {
                    rows.add(row);
                }
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    private long latestSuccessfulSyncId(SQLiteDatabase db) {
        HistoricalSyncRun sync = latestSuccessfulSyncRun(db);
        return sync == null ? 0L : sync.id;
    }

    private Map<String, Integer> reviewCountsByKanji(SQLiteDatabase db) {
        Map<String, Integer> counts = new HashMap<>();
        Cursor cursor = db.rawQuery("SELECT kanji, COUNT(*) AS review_count FROM review_log GROUP BY kanji", null);
        try {
            while (cursor.moveToNext()) {
                counts.put(string(cursor, "kanji"), integer(cursor, "review_count"));
            }
        } finally {
            cursor.close();
        }
        return counts;
    }

    private Set<String> impactCandidateKanji(SQLiteDatabase db, long latestSyncId) {
        Set<String> candidates = new HashSet<>();
        Cursor current = db.query(
                "sync_kanji_snapshots",
                new String[]{"kanji"},
                "sync_id=? AND (weakness_score>0 OR reason_code<>'' OR active_example_count>0 OR suspended_example_count>0)",
                new String[]{Long.toString(latestSyncId)},
                null,
                null,
                null
        );
        try {
            while (current.moveToNext()) {
                candidates.add(string(current, "kanji"));
            }
        } finally {
            current.close();
        }
        Cursor study = db.query(true, "study_items", new String[]{"kanji"}, null, null, null, null, null, null);
        try {
            while (study.moveToNext()) {
                candidates.add(string(study, "kanji"));
            }
        } finally {
            study.close();
        }
        Cursor imports = db.query(true, "suspended_imports", new String[]{"kanji"}, null, null, null, null, null, null);
        try {
            while (imports.moveToNext()) {
                candidates.add(string(imports, "kanji"));
            }
        } finally {
            imports.close();
        }
        return candidates;
    }

    private Map<String, KanjiImpactAnalyzer.MetricSnapshot> kanjiMetricsForSync(SQLiteDatabase db, long syncId) {
        Map<String, KanjiImpactAnalyzer.MetricSnapshot> out = new LinkedHashMap<>();
        Cursor cursor = db.query(
                "sync_kanji_snapshots",
                null,
                "sync_id=?",
                new String[]{Long.toString(syncId)},
                null,
                null,
                "kanji ASC"
        );
        try {
            while (cursor.moveToNext()) {
                out.put(string(cursor, "kanji"), readKanjiImpactMetric(cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private HistoricalKanjiSnapshot baselineKanjiSnapshot(SQLiteDatabase db, String kanji) {
        long startedAt = firstKaniSignalAt(db, kanji);
        if (startedAt <= 0L) {
            return firstKanjiSnapshot(db, kanji);
        }
        HistoricalKanjiSnapshot atOrAfterStart = firstKanjiSnapshotAtOrAfter(db, kanji, startedAt);
        if (atOrAfterStart != null) {
            return atOrAfterStart;
        }
        return latestKanjiSnapshotAtOrBefore(db, kanji, startedAt);
    }

    private long firstKaniSignalAt(SQLiteDatabase db, String kanji) {
        long first = minLongQuery(
                db,
                "SELECT MIN(occurred_at) FROM kanji_timeline_events WHERE kanji=?",
                new String[]{kanji}
        );
        long firstReview = minLongQuery(
                db,
                "SELECT MIN(reviewed_at) FROM review_log WHERE kanji=?",
                new String[]{kanji}
        );
        long firstStudyItem = minLongQuery(
                db,
                "SELECT MIN(created_at) FROM study_items WHERE kanji=?",
                new String[]{kanji}
        );
        long firstSuspendedImport = minLongQuery(
                db,
                "SELECT MIN(first_imported_at) FROM suspended_imports WHERE kanji=?",
                new String[]{kanji}
        );
        first = earliestPositive(first, firstReview);
        first = earliestPositive(first, firstStudyItem);
        return earliestPositive(first, firstSuspendedImport);
    }

    private long minLongQuery(SQLiteDatabase db, String sql, String[] args) {
        Cursor cursor = db.rawQuery(sql, args);
        try {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return 0L;
            }
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private long earliestPositive(long left, long right) {
        if (left <= 0L) {
            return Math.max(0L, right);
        }
        if (right <= 0L) {
            return left;
        }
        return Math.min(left, right);
    }

    private HistoricalKanjiSnapshot firstKanjiSnapshotAtOrAfter(SQLiteDatabase db, String kanji, long startedAt) {
        Cursor cursor = db.query(
                "sync_kanji_snapshots",
                null,
                "kanji=? AND finished_at>=?",
                new String[]{kanji, Long.toString(startedAt)},
                null,
                null,
                "finished_at ASC, sync_id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalKanjiSnapshot(longValue(cursor, "sync_id"), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private HistoricalKanjiSnapshot latestKanjiSnapshotAtOrBefore(SQLiteDatabase db, String kanji, long startedAt) {
        Cursor cursor = db.query(
                "sync_kanji_snapshots",
                null,
                "kanji=? AND finished_at<=?",
                new String[]{kanji, Long.toString(startedAt)},
                null,
                null,
                "finished_at DESC, sync_id DESC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalKanjiSnapshot(longValue(cursor, "sync_id"), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private HistoricalKanjiSnapshot firstKanjiSnapshot(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(
                "sync_kanji_snapshots",
                null,
                "kanji=?",
                new String[]{kanji},
                null,
                null,
                "sync_id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalKanjiSnapshot(longValue(cursor, "sync_id"), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private KanjiImpactAnalyzer.MetricSnapshot readKanjiImpactMetric(Cursor cursor) {
        return new KanjiImpactAnalyzer.MetricSnapshot(
                integer(cursor, "active_cards"),
                integer(cursor, "suspended_cards"),
                integer(cursor, "mature_support_count"),
                cursor.getDouble(cursor.getColumnIndexOrThrow("average_interval_days")),
                integer(cursor, "total_reps"),
                integer(cursor, "total_lapses"),
                nullableDouble(cursor, "fsrs_stability_avg"),
                nullableDouble(cursor, "fsrs_difficulty_avg"),
                nullableDouble(cursor, "fsrs_retrievability_avg")
        );
    }

    private SameCardMetrics sameCardMetrics(SQLiteDatabase db, String kanji, long baselineSyncId, long currentSyncId) {
        ImpactMetricBuilder baseline = new ImpactMetricBuilder();
        ImpactMetricBuilder current = new ImpactMetricBuilder();
        Cursor cursor = db.rawQuery(
                "SELECT "
                        + "b.interval_days AS b_interval_days, b.reps AS b_reps, b.lapses AS b_lapses, b.suspended AS b_suspended, b.mature AS b_mature, b.fsrs_stability AS b_fsrs_stability, b.fsrs_difficulty AS b_fsrs_difficulty, b.fsrs_retrievability AS b_fsrs_retrievability, "
                        + "c.interval_days AS c_interval_days, c.reps AS c_reps, c.lapses AS c_lapses, c.suspended AS c_suspended, c.mature AS c_mature, c.fsrs_stability AS c_fsrs_stability, c.fsrs_difficulty AS c_fsrs_difficulty, c.fsrs_retrievability AS c_fsrs_retrievability "
                        + "FROM sync_card_snapshots b "
                        + "JOIN sync_card_snapshots c ON c.card_id=b.card_id "
                        + "JOIN sync_note_snapshots nb ON nb.sync_id=b.sync_id AND nb.note_id=b.note_id "
                        + "JOIN sync_note_snapshots nc ON nc.sync_id=c.sync_id AND nc.note_id=c.note_id "
                        + "WHERE b.sync_id=? AND c.sync_id=? AND instr(nb.extracted_kanji, ?) > 0 AND instr(nc.extracted_kanji, ?) > 0",
                new String[]{Long.toString(baselineSyncId), Long.toString(currentSyncId), kanji, kanji}
        );
        try {
            while (cursor.moveToNext()) {
                baseline.add(
                        integer(cursor, "b_interval_days"),
                        integer(cursor, "b_reps"),
                        integer(cursor, "b_lapses"),
                        integer(cursor, "b_suspended") == 1,
                        integer(cursor, "b_mature") == 1,
                        nullableDouble(cursor, "b_fsrs_stability"),
                        nullableDouble(cursor, "b_fsrs_difficulty"),
                        nullableDouble(cursor, "b_fsrs_retrievability")
                );
                current.add(
                        integer(cursor, "c_interval_days"),
                        integer(cursor, "c_reps"),
                        integer(cursor, "c_lapses"),
                        integer(cursor, "c_suspended") == 1,
                        integer(cursor, "c_mature") == 1,
                        nullableDouble(cursor, "c_fsrs_stability"),
                        nullableDouble(cursor, "c_fsrs_difficulty"),
                        nullableDouble(cursor, "c_fsrs_retrievability")
                );
            }
        } finally {
            cursor.close();
        }
        if (current.totalCards() == 0) {
            return SameCardMetrics.EMPTY;
        }
        return new SameCardMetrics(baseline.build(), current.build());
    }

    private List<Records.Example> examplesForKanji(SQLiteDatabase db, String kanji) {
        List<Records.Example> examples = new ArrayList<>();
        Cursor cursor = db.query("kanji_examples", null, "kanji=?", new String[]{kanji}, null, null, "source_type DESC, id ASC", "8");
        try {
            while (cursor.moveToNext()) {
                examples.add(new Records.Example(
                        string(cursor, "source_type"),
                        longValue(cursor, "card_id"),
                        longValue(cursor, "note_id"),
                        string(cursor, "expression"),
                        string(cursor, "reading"),
                        string(cursor, "meaning"),
                        string(cursor, "sentence"),
                        integer(cursor, "mature") == 1,
                        integer(cursor, "lapses"),
                        integer(cursor, "interval_days"),
                        integer(cursor, "reps"),
                        nullableDouble(cursor, "fsrs_stability"),
                        nullableDouble(cursor, "fsrs_difficulty"),
                        nullableDouble(cursor, "fsrs_retrievability")
                ));
            }
        } finally {
            cursor.close();
        }
        return examples;
    }

    private void upsertStudyItem(SQLiteDatabase db, Records.StudyItem item) {
        ContentValues values = new ContentValues();
        values.put("kanji", item.kanji);
        values.put("state", item.state);
        values.put("due_at", item.dueAtMillis);
        values.put("stability", item.stability);
        values.put("difficulty", item.difficulty);
        values.put("total_reviews", item.totalReviews);
        values.put("lapses", item.lapses);
        values.put("learning_step", item.learningStep);
        values.put("writing_level", item.writingLevel);
        values.put("recognition_stage", item.recognitionStage);
        values.put("consecutive_failed_recognition_days", item.consecutiveFailedRecognitionDays);
        values.put("last_failed_recognition_day", item.lastFailedRecognitionDayMillis);
        values.put("writing_remediation_pending", item.writingRemediationPending ? 1 : 0);
        values.put("suppressed_by_task_type", item.suppressedByTaskType);
        values.put("suppressed_at", item.suppressedAtMillis);
        values.put("mature_interval_days", item.matureIntervalDays);
        values.put("answer_signature", item.answerSignature);
        values.put("typing_meaning_memory", item.typingMeaningMemory.encode());
        values.put("kanji_meaning_memory", item.kanjiMeaningMemory.encode());
        values.put("font_meaning_memory", item.fontMeaningMemory.encode());
        values.put("word_reading_memory", item.wordReadingMemory.encode());
        values.put("writing_remediation_memory", item.writingRemediationMemory.encode());
        values.put("active_token", item.activeToken);
        values.put("created_at", item.createdAtMillis);
        db.insertWithOnConflict("study_items", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private Records.StudyItem readStudyItem(Cursor cursor) {
        String state = string(cursor, "state");
        long dueAt = longValue(cursor, "due_at");
        double stability = cursor.getDouble(cursor.getColumnIndexOrThrow("stability"));
        double difficulty = cursor.getDouble(cursor.getColumnIndexOrThrow("difficulty"));
        int totalReviews = integer(cursor, "total_reviews");
        int lapses = integer(cursor, "lapses");
        int learningStep = integer(cursor, "learning_step");
        int recognitionStage = integer(cursor, "recognition_stage");
        boolean writingRemediationPending = integer(cursor, "writing_remediation_pending") == 1;
        int matureIntervalDays = integer(cursor, "mature_interval_days");
        Records.TaskMemory typingFallback = taskMemoryFallback(-1, recognitionStage, state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        Records.TaskMemory kanjiFallback = taskMemoryFallback(0, recognitionStage, state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        Records.TaskMemory fontFallback = taskMemoryFallback(1, recognitionStage, state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        Records.TaskMemory wordFallback = taskMemoryFallback(2, recognitionStage, state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        Records.TaskMemory writingFallback = writingRemediationPending
                ? Records.TaskMemory.fromStudyFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays)
                : Records.TaskMemory.initial();
        return new Records.StudyItem(
                string(cursor, "kanji"),
                state,
                dueAt,
                stability,
                difficulty,
                totalReviews,
                lapses,
                learningStep,
                integer(cursor, "writing_level"),
                recognitionStage,
                integer(cursor, "consecutive_failed_recognition_days"),
                longValue(cursor, "last_failed_recognition_day"),
                writingRemediationPending,
                string(cursor, "suppressed_by_task_type"),
                longValue(cursor, "suppressed_at"),
                matureIntervalDays,
                string(cursor, "answer_signature"),
                string(cursor, "active_token"),
                longValue(cursor, "created_at"),
                Records.TaskMemory.decode(string(cursor, "typing_meaning_memory"), typingFallback),
                Records.TaskMemory.decode(string(cursor, "kanji_meaning_memory"), kanjiFallback),
                Records.TaskMemory.decode(string(cursor, "font_meaning_memory"), fontFallback),
                Records.TaskMemory.decode(string(cursor, "word_reading_memory"), wordFallback),
                Records.TaskMemory.decode(string(cursor, "writing_remediation_memory"), writingFallback)
        );
    }

    private Records.LearningRepeat readLearningRepeat(Cursor cursor) {
        return new Records.LearningRepeat(
                string(cursor, "kanji"),
                string(cursor, "answer_signature"),
                string(cursor, "task_type"),
                string(cursor, "repeat_type"),
                integer(cursor, "step_index"),
                longValue(cursor, "due_at"),
                string(cursor, "active_token"),
                longValue(cursor, "created_at"),
                longValue(cursor, "updated_at")
        );
    }

    private Records.TaskMemory taskMemoryFallback(
            int memoryStage,
            int recognitionStage,
            String state,
            long dueAtMillis,
            double stability,
            double difficulty,
            int totalReviews,
            int lapses,
            int learningStep,
            int matureIntervalDays
    ) {
        if (Math.max(-1, Math.min(2, recognitionStage)) == memoryStage) {
            return Records.TaskMemory.fromStudyFields(state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        }
        return Records.TaskMemory.initial();
    }

    private long firstImportedAt(SQLiteDatabase db, String kanji, long fallback) {
        Cursor cursor = db.query("suspended_imports", new String[]{COLUMN_FIRST_IMPORTED_AT}, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? longValue(cursor, COLUMN_FIRST_IMPORTED_AT) : fallback;
        } finally {
            cursor.close();
        }
    }

    private ActiveCardIndex activeCardIndex(List<Records.Card> cards) {
        Set<Long> noteIds = new HashSet<>();
        Set<Long> cardIds = new HashSet<>();
        int activeCardCount = 0;
        int suspendedCardCount = 0;
        for (Records.Card card : cards) {
            if (card.suspended) {
                suspendedCardCount++;
            } else {
                activeCardCount++;
                noteIds.add(card.noteId);
                cardIds.add(card.cardId);
            }
        }
        return new ActiveCardIndex(noteIds, cardIds, activeCardCount, suspendedCardCount);
    }

    private int countDeletedExisting(SQLiteDatabase db, String table, String idColumn, Set<Long> currentIds) {
        int missing = 0;
        Cursor cursor = db.query(table, new String[]{idColumn}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                if (!currentIds.contains(cursor.getLong(0))) {
                    missing++;
                }
            }
        } finally {
            cursor.close();
        }
        return missing;
    }

    private static final class MutableSuspendedImport {
        private final String kanji;
        private final Integer rank;
        private final boolean rankKnown;
        private final int cutoff;
        private final List<Records.SuspendedSource> sources = new ArrayList<>();

        private MutableSuspendedImport(String kanji, Integer rank, boolean rankKnown, int cutoff) {
            this.kanji = kanji;
            this.rank = rank;
            this.rankKnown = rankKnown;
            this.cutoff = cutoff;
        }

        private Records.SuspendedImport build() {
            return new Records.SuspendedImport(kanji, rank, rankKnown, cutoff, sources);
        }
    }

    private static final class ActiveCardIndex {
        private final Set<Long> noteIds;
        private final Set<Long> cardIds;
        private final int activeCardCount;
        private final int suspendedCardCount;

        private ActiveCardIndex(Set<Long> noteIds, Set<Long> cardIds, int activeCardCount, int suspendedCardCount) {
            this.noteIds = noteIds;
            this.cardIds = cardIds;
            this.activeCardCount = activeCardCount;
            this.suspendedCardCount = suspendedCardCount;
        }
    }

    private static final class HistoricalSyncRun {
        private final long id;
        private final long startedAt;
        private final long finishedAt;

        private HistoricalSyncRun(long id, long startedAt, long finishedAt) {
            this.id = id;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }
    }

    private static final class HistoricalNoteSnapshot {
        private final long noteId;
        private final long modelId;
        private final String modelName;
        private final String expression;
        private final String reading;
        private final String meaning;
        private final String sentence;
        private final String tags;
        private final String fieldsJson;

        private HistoricalNoteSnapshot(
                long noteId,
                long modelId,
                String modelName,
                String expression,
                String reading,
                String meaning,
                String sentence,
                String tags,
                String fieldsJson
        ) {
            this.noteId = noteId;
            this.modelId = modelId;
            this.modelName = modelName == null ? "" : modelName;
            this.expression = expression == null ? "" : expression;
            this.reading = reading == null ? "" : reading;
            this.meaning = meaning == null ? "" : meaning;
            this.sentence = sentence == null ? "" : sentence;
            this.tags = tags == null ? "" : tags;
            this.fieldsJson = fieldsJson == null ? "" : fieldsJson;
        }
    }

    private static final class HistoricalKanjiSnapshot {
        private final long syncId;
        private final KanjiImpactAnalyzer.MetricSnapshot metrics;

        private HistoricalKanjiSnapshot(long syncId, KanjiImpactAnalyzer.MetricSnapshot metrics) {
            this.syncId = syncId;
            this.metrics = metrics;
        }
    }

    private static final class SameCardMetrics {
        private static final SameCardMetrics EMPTY = new SameCardMetrics(null, null);

        private final KanjiImpactAnalyzer.MetricSnapshot baseline;
        private final KanjiImpactAnalyzer.MetricSnapshot current;

        private SameCardMetrics(KanjiImpactAnalyzer.MetricSnapshot baseline, KanjiImpactAnalyzer.MetricSnapshot current) {
            this.baseline = baseline;
            this.current = current;
        }
    }

    private static final class ImpactMetricBuilder {
        private final HistoricalKanjiAggregate aggregate = new HistoricalKanjiAggregate("");

        private void add(
                int intervalDays,
                int reps,
                int lapses,
                boolean suspended,
                boolean mature,
                Double fsrsStability,
                Double fsrsDifficulty,
                Double fsrsRetrievability
        ) {
            aggregate.add(intervalDays, reps, lapses, suspended, mature, fsrsStability, fsrsDifficulty, fsrsRetrievability);
        }

        private int totalCards() {
            return aggregate.activeCards + aggregate.suspendedCards;
        }

        private KanjiImpactAnalyzer.MetricSnapshot build() {
            return new KanjiImpactAnalyzer.MetricSnapshot(
                    aggregate.activeCards,
                    aggregate.suspendedCards,
                    aggregate.matureSupportCount,
                    aggregate.averageIntervalDays(),
                    aggregate.totalReps,
                    aggregate.totalLapses,
                    aggregate.averageStability(),
                    aggregate.averageDifficulty(),
                    aggregate.averageRetrievability()
            );
        }
    }

    private static final class HistoricalKanjiAggregate {
        private final String kanji;
        private int activeCards;
        private int suspendedCards;
        private int matureSupportCount;
        private int totalLapses;
        private int totalReps;
        private int intervalCount;
        private double intervalSum;
        private int stabilityCount;
        private double stabilitySum;
        private int difficultyCount;
        private double difficultySum;
        private int retrievabilityCount;
        private double retrievabilitySum;
        private int weaknessScore;
        private String reasonCode = "";
        private int activeExampleCount;
        private int suspendedExampleCount;

        private HistoricalKanjiAggregate(String kanji) {
            this.kanji = kanji == null ? "" : kanji;
        }

        private void add(Records.Card card, int matureDays) {
            add(
                    card.intervalDays,
                    card.reps,
                    card.lapses,
                    card.suspended,
                    card.mature(matureDays),
                    card.fsrsStability,
                    card.fsrsDifficulty,
                    card.fsrsRetrievability
            );
        }

        private void add(
                int intervalDays,
                int reps,
                int lapses,
                boolean suspended,
                boolean mature,
                Double fsrsStability,
                Double fsrsDifficulty,
                Double fsrsRetrievability
        ) {
            if (suspended) {
                suspendedCards++;
            } else {
                activeCards++;
            }
            if (mature) {
                matureSupportCount++;
            }
            totalLapses += Math.max(0, lapses);
            totalReps += Math.max(0, reps);
            intervalSum += Math.max(0, intervalDays);
            intervalCount++;
            if (fsrsStability != null) {
                stabilitySum += fsrsStability;
                stabilityCount++;
            }
            if (fsrsDifficulty != null) {
                difficultySum += fsrsDifficulty;
                difficultyCount++;
            }
            if (fsrsRetrievability != null) {
                retrievabilitySum += fsrsRetrievability;
                retrievabilityCount++;
            }
        }

        private double averageIntervalDays() {
            return intervalCount == 0 ? 0.0 : intervalSum / intervalCount;
        }

        private Double averageStability() {
            return stabilityCount == 0 ? null : stabilitySum / stabilityCount;
        }

        private Double averageDifficulty() {
            return difficultyCount == 0 ? null : difficultySum / difficultyCount;
        }

        private Double averageRetrievability() {
            return retrievabilityCount == 0 ? null : retrievabilitySum / retrievabilityCount;
        }
    }

    private static final class SimilarChoiceSnapshot {
        private final long dueAtMillis;
        private final long passedAtMillis;
        private final long lastReviewedAtMillis;
        private final int correctCount;
        private final int wrongCount;
        private final long firstSeenAtMillis;

        private SimilarChoiceSnapshot(
                long dueAtMillis,
                long passedAtMillis,
                long lastReviewedAtMillis,
                int correctCount,
                int wrongCount,
                long firstSeenAtMillis
        ) {
            this.dueAtMillis = dueAtMillis;
            this.passedAtMillis = passedAtMillis;
            this.lastReviewedAtMillis = lastReviewedAtMillis;
            this.correctCount = correctCount;
            this.wrongCount = wrongCount;
            this.firstSeenAtMillis = firstSeenAtMillis;
        }
    }

    private static final class MutableKanjiInventoryItem {
        private final String kanji;
        private String primaryMeaning = "";
        private String browserSearch = "";
        private int sourceCount = 0;
        private int exampleCount = 0;
        private final Set<String> readings = new HashSet<>();
        private final Set<String> searchParts = new HashSet<>();

        private MutableKanjiInventoryItem(String kanji) {
            this.kanji = kanji == null ? "" : kanji;
            searchParts.add(this.kanji.toLowerCase(Locale.ROOT));
        }

        private void add(String meaning, String reading, String expression, String sentence) {
            sourceCount++;
            if (primaryMeaning.isEmpty() && meaning != null && !meaning.isEmpty()) {
                primaryMeaning = meaning;
            }
            if (reading != null && !reading.isEmpty()) {
                readings.add(reading);
            }
            addSearch(meaning);
            addSearch(reading);
            addSearch(expression);
            addSearch(sentence);
        }

        private void addSearch(String value) {
            String normalized = TextUtil.normalizeJapanese(value);
            if (!normalized.isEmpty()) {
                searchParts.add(normalized.toLowerCase(Locale.ROOT));
            }
        }

        private String readingsText(String previous) {
            if (readings.isEmpty()) {
                return previous == null ? "" : previous;
            }
            return String.join(" / ", readings);
        }

        private String searchText(Records.KanjiInventoryItem previous) {
            if (previous != null) {
                addSearch(previous.primaryMeaning);
                addSearch(previous.readings);
                addSearch(previous.browserSearch);
            }
            return String.join(" ", searchParts);
        }
    }

    private static final class SourceSnapshot {
        private static final SourceSnapshot EMPTY = new SourceSnapshot("", "");

        private final String expression;
        private final String reading;

        private SourceSnapshot(String expression, String reading) {
            this.expression = expression == null ? "" : expression;
            this.reading = reading == null ? "" : reading;
        }
    }

    private static final class RowSnapshot {
        private final String kanji;
        private final int weaknessScore;
        private final int matureSupportCount;
        private final long rebuiltAt;
        private final SourceSnapshot source;

        private RowSnapshot(String kanji, int weaknessScore, int matureSupportCount, long rebuiltAt, SourceSnapshot source) {
            this.kanji = kanji;
            this.weaknessScore = weaknessScore;
            this.matureSupportCount = matureSupportCount;
            this.rebuiltAt = rebuiltAt;
            this.source = source == null ? SourceSnapshot.EMPTY : source;
        }
    }

    private static final class StudySnapshot {
        private final String state;

        private StudySnapshot(String state) {
            this.state = state == null ? "" : state;
        }
    }

    private static String studyFamilyKey(String kanji, String answerSignature) {
        return kanji + "\u0000" + (answerSignature == null ? "" : answerSignature);
    }

    private static String studyTimelineKey(Records.StudyItem item) {
        return item.kanji + ":" + Integer.toHexString((item.answerSignature == null ? "" : item.answerSignature).hashCode());
    }

    private static String fieldsJson(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(TextUtil.jsonQuote(entry.getKey())).append(':').append(TextUtil.jsonQuote(entry.getValue()));
        }
        out.append('}');
        return out.toString();
    }

    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static int integer(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static Integer nullableInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getInt(index);
    }

    private static Long nullableLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getLong(index);
    }

    private static Double nullableDouble(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    private static long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private static void putNullableDouble(ContentValues values, String key, Double value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    public static final class SyncStatus {
        public final String status;
        public final int activeNotes;
        public final int activeCards;
        public final int suspendedCards;
        public final int importedKanji;
        public final long finishedAt;
        public final String errorMessage;
        public final String removalMessage;

        private SyncStatus(String status, int activeNotes, int activeCards, int suspendedCards, int importedKanji, long finishedAt, String errorMessage, String removalMessage) {
            this.status = status;
            this.activeNotes = activeNotes;
            this.activeCards = activeCards;
            this.suspendedCards = suspendedCards;
            this.importedKanji = importedKanji;
            this.finishedAt = finishedAt;
            this.errorMessage = errorMessage;
            this.removalMessage = removalMessage;
        }

        public String headline() {
            if (!STATUS_SUCCESS.equals(status)) {
                return "Sync blocked: " + errorMessage;
            }
            return String.format(Locale.ROOT, "%d active cards checked, %d suspended cards archived, %d rare kanji added", activeCards, suspendedCards, importedKanji);
        }
    }

    public static final class ReminderSettings {
        public final boolean enabled;
        public final int hour;
        public final int minute;

        public ReminderSettings(boolean enabled, int hour, int minute) {
            this.enabled = enabled;
            this.hour = hour;
            this.minute = minute;
        }

        private ReminderSettings normalized() {
            int normalizedHour = Math.max(0, Math.min(23, hour));
            int normalizedMinute = Math.max(0, Math.min(59, minute));
            return new ReminderSettings(enabled, normalizedHour, normalizedMinute);
        }

        public String displayTime() {
            return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
    }

    public static final class AutoSyncSettings {
        public final boolean configured;
        public final boolean enabled;
        public final int hour;
        public final int minute;
        public final long lastAttemptAt;
        public final long lastSuccessAt;
        public final long nextRunAt;

        public AutoSyncSettings(boolean configured, boolean enabled, int hour, int minute, long lastAttemptAt, long lastSuccessAt, long nextRunAt) {
            this.configured = configured;
            this.enabled = enabled;
            this.hour = hour;
            this.minute = minute;
            this.lastAttemptAt = lastAttemptAt;
            this.lastSuccessAt = lastSuccessAt;
            this.nextRunAt = nextRunAt;
        }

        private AutoSyncSettings normalized() {
            int normalizedHour = Math.max(0, Math.min(23, hour));
            int normalizedMinute = Math.max(0, Math.min(59, minute));
            return new AutoSyncSettings(configured, configured && enabled, normalizedHour, normalizedMinute, Math.max(0L, lastAttemptAt), Math.max(0L, lastSuccessAt), Math.max(0L, nextRunAt));
        }

        public String displayTime() {
            return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
    }

    public static final class AutoUpdateStatus {
        public final boolean enabled;
        public final long lastCheckAtMillis;
        public final String lastResult;
        public final String lastVersion;
        public final String pendingApkName;
        public final String pendingMessage;

        private AutoUpdateStatus(boolean enabled, long lastCheckAtMillis, String lastResult, String lastVersion, String pendingApkName, String pendingMessage) {
            this.enabled = enabled;
            this.lastCheckAtMillis = lastCheckAtMillis;
            this.lastResult = lastResult == null ? "" : lastResult;
            this.lastVersion = lastVersion == null ? "" : lastVersion;
            this.pendingApkName = pendingApkName == null ? "" : pendingApkName;
            this.pendingMessage = pendingMessage == null ? "" : pendingMessage;
        }

        public boolean hasPendingUpdate() {
            return !pendingApkName.isEmpty();
        }
    }

    public static final class StudyStreak {
        public final int currentDays;
        public final int bestDays;
        public final boolean studiedToday;
        public final int reviewsToday;
        public final long lastStudyAtMillis;

        public StudyStreak(int currentDays, int bestDays, boolean studiedToday, int reviewsToday, long lastStudyAtMillis) {
            this.currentDays = currentDays;
            this.bestDays = bestDays;
            this.studiedToday = studiedToday;
            this.reviewsToday = reviewsToday;
            this.lastStudyAtMillis = lastStudyAtMillis;
        }
    }

    public static final class StudyImpactStats {
        public final int totalReviews;
        public final int distinctReviewedKanji;
        public final int writingRequired;
        public final int writingPassed;
        public final int writingFailed;
        public final int manualOverrides;

        public StudyImpactStats(int totalReviews, int distinctReviewedKanji, int writingRequired, int writingPassed, int writingFailed, int manualOverrides) {
            this.totalReviews = totalReviews;
            this.distinctReviewedKanji = distinctReviewedKanji;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            this.writingFailed = writingFailed;
            this.manualOverrides = manualOverrides;
        }
    }

    public static final class RecentMistake {
        public final String kanji;
        public final String rating;
        public final long reviewedAtMillis;

        public RecentMistake(String kanji, String rating, long reviewedAtMillis) {
            this.kanji = kanji == null ? "" : kanji;
            this.rating = rating == null ? "" : rating;
            this.reviewedAtMillis = reviewedAtMillis;
        }
    }
}
