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
    private static final int DB_VERSION = 15;
    private static final String TABLE_SETTINGS = "settings";
    private static final String TABLE_SYNC_RUNS = "sync_runs";
    private static final String TABLE_SOURCE_NOTES = "source_notes";
    private static final String TABLE_SOURCE_CARDS = "source_cards";
    private static final String TABLE_SUSPENDED_ARCHIVE = "suspended_archive";
    private static final String TABLE_SUSPENDED_IMPORTS = "suspended_imports";
    private static final String TABLE_SUSPENDED_SOURCES = "suspended_sources";
    private static final String TABLE_DASHBOARD_ROWS = "dashboard_rows";
    private static final String TABLE_KANJI_EXAMPLES = "kanji_examples";
    private static final String TABLE_STUDY_ITEMS = "study_items";
    private static final String TABLE_LEARNING_REPEATS = "learning_repeats";
    private static final String TABLE_REVIEW_LOG = "review_log";
    private static final String TABLE_KANJI_INVENTORY = "kanji_inventory";
    private static final String TABLE_LOCAL_KANJI_SUSPENSIONS = "local_kanji_suspensions";
    private static final String TABLE_SIMILAR_KANJI_PAIRS = "similar_kanji_pairs";
    private static final String TABLE_SIMILAR_KANJI_CHOICE_STATE = "similar_kanji_choice_state";
    private static final String TABLE_SIMILAR_KANJI_REPAIR_QUEUE = "similar_kanji_repair_queue";
    private static final String TABLE_SIMILAR_KANJI_REVIEW_LOG = "similar_kanji_review_log";
    private static final String TABLE_STUDY_TASK_LOG = "study_task_log";
    private static final String TABLE_KANJI_TIMELINE_EVENTS = "kanji_timeline_events";
    private static final String TABLE_SYNC_CARD_SNAPSHOTS = "sync_card_snapshots";
    private static final String TABLE_SYNC_NOTE_SNAPSHOTS = "sync_note_snapshots";
    private static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    private static final String SQL_CREATE_TABLE = "CREATE TABLE ";
    private static final String SQL_CREATE_TABLE_IF_NEEDED = "CREATE TABLE IF NOT EXISTS ";
    private static final String SQL_TEXT_NOT_NULL_DEFAULT_EMPTY = "TEXT NOT NULL DEFAULT ''";
    private static final String SQL_INTEGER_NOT_NULL_DEFAULT_ZERO = "INTEGER NOT NULL DEFAULT 0";
    private static final String WHERE_KANJI = "kanji=?";
    private static final String WHERE_SETTING_KEY = "key=?";
    private static final String WHERE_SIMILAR_CHOICE = "target_kanji=? AND choice_signature=?";
    private static final String ORDER_ID_DESC = "id DESC";
    private static final String ORDER_KANJI_ASC = "kanji ASC";
    private static final String ORDER_SIMILAR_PAIR = "kanji_a ASC, kanji_b ASC, source ASC";
    private static final String COLUMN_ACTIVE_TOKEN = "active_token";
    private static final String COLUMN_ANSWER_SIGNATURE = "answer_signature";
    private static final String COLUMN_ATTEMPTS = "attempts";
    private static final String COLUMN_ACTIVE_CARDS_COUNT = "active_cards_count";
    private static final String COLUMN_ACTIVE_EXAMPLE_COUNT = "active_example_count";
    private static final String COLUMN_ACTIVE_NOTES_COUNT = "active_notes_count";
    private static final String COLUMN_BROWSER_SEARCH = "browser_search";
    private static final String COLUMN_CARD_ID = "card_id";
    private static final String COLUMN_CHOICE_SIGNATURE = "choice_signature";
    private static final String COLUMN_CHOICES = "choices";
    private static final String COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS = "consecutive_failed_recognition_days";
    private static final String COLUMN_COMPLETED_AT = "completed_at";
    private static final String COLUMN_CORRECT_COUNT = "correct_count";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_CUTOFF_USED = "cutoff_used";
    private static final String COLUMN_DECK_ID = "deck_id";
    private static final String COLUMN_DECK_IDS = "deck_ids";
    private static final String COLUMN_DECK_NAME = "deck_name";
    private static final String COLUMN_DECK_NAMES = "deck_names";
    private static final String COLUMN_DEDUPE_KEY = "dedupe_key";
    private static final String COLUMN_DETAIL = "detail";
    private static final String COLUMN_DUE_AT = "due_at";
    private static final String COLUMN_ERROR_MESSAGE = "error_message";
    private static final String COLUMN_EVENT_TYPE = "event_type";
    private static final String COLUMN_EXPRESSION = "expression";
    private static final String COLUMN_FIELDS_JSON = "fields_json";
    private static final String COLUMN_FINISHED_AT = "finished_at";
    private static final String COLUMN_FIRST_SEEN_AT = "first_seen_at";
    private static final String COLUMN_FONT_MEANING_MEMORY = "font_meaning_memory";
    private static final String COLUMN_FSRS_DIFFICULTY = "fsrs_difficulty";
    private static final String COLUMN_FSRS_RETRIEVABILITY = "fsrs_retrievability";
    private static final String COLUMN_FSRS_STABILITY = "fsrs_stability";
    private static final String COLUMN_INTERVAL_DAYS = "interval_days";
    private static final String COLUMN_JITEN_RANK = "jiten_rank";
    private static final String COLUMN_KANJI = "kanji";
    private static final String COLUMN_KANJI_A = "kanji_a";
    private static final String COLUMN_KANJI_B = "kanji_b";
    private static final String COLUMN_KANJI_MEANING_MEMORY = "kanji_meaning_memory";
    private static final String COLUMN_LAST_FAILED_RECOGNITION_DAY = "last_failed_recognition_day";
    private static final String COLUMN_LAST_REVIEWED_AT = "last_reviewed_at";
    private static final String COLUMN_LAST_SEEN_AT = "last_seen_at";
    private static final String COLUMN_LAST_SEEN_SYNC_ID = "last_seen_sync_id";
    private static final String COLUMN_LAPSES = "lapses";
    private static final String COLUMN_MANUAL_OVERRIDE = "manual_override";
    private static final String COLUMN_MATURE_SUPPORT_COUNT = "mature_support_count";
    private static final String COLUMN_MATURE_INTERVAL_DAYS = "mature_interval_days";
    private static final String COLUMN_MEANING = "meaning";
    private static final String COLUMN_MODEL_ID = "model_id";
    private static final String COLUMN_MODEL_NAME = "model_name";
    private static final String COLUMN_NOTE_ID = "note_id";
    private static final String COLUMN_OCCURRED_AT = "occurred_at";
    private static final String COLUMN_PASSED_AT = "passed_at";
    private static final String COLUMN_PRIMARY_MEANING = "primary_meaning";
    private static final String COLUMN_QUEUE = "queue";
    private static final String COLUMN_RANK_KNOWN = "rank_known";
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_READING = "reading";
    private static final String COLUMN_RECOGNITION_STAGE = "recognition_stage";
    private static final String COLUMN_REASON_CODE = "reason_code";
    private static final String COLUMN_REASON_TEXT = "reason_text";
    private static final String COLUMN_REMOVAL_MESSAGE = "removal_message";
    private static final String COLUMN_REPS = "reps";
    private static final String COLUMN_REVIEWED_AT = "reviewed_at";
    private static final String COLUMN_SENTENCE = "sentence";
    private static final String COLUMN_SOURCE = "source";
    private static final String COLUMN_STATE = "state";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_STARTED_AT = "started_at";
    private static final String COLUMN_SYNC_ID = "sync_id";
    private static final String COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT = "suspended_cards_archived_count";
    private static final String COLUMN_SUSPENDED_EXAMPLE_COUNT = "suspended_example_count";
    private static final String COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT = "suspended_kanji_imported_count";
    private static final String COLUMN_SUPPRESSED_AT = "suppressed_at";
    private static final String COLUMN_SUPPRESSED_BY_TASK_TYPE = "suppressed_by_task_type";
    private static final String COLUMN_TARGET_KANJI = "target_kanji";
    private static final String COLUMN_TAGS = "tags";
    private static final String COLUMN_TASK_TYPE = "task_type";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_TYPING_MEANING_MEMORY = "typing_meaning_memory";
    private static final String COLUMN_TOKEN = "token";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String COLUMN_VALUE = "value";
    private static final String COLUMN_WEAKNESS_SCORE = "weakness_score";
    private static final String COLUMN_WORD_READING_MEMORY = "word_reading_memory";
    private static final String COLUMN_WRONG_COUNT = "wrong_count";
    private static final String COLUMN_WRITING_REMEDIATION_MEMORY = "writing_remediation_memory";
    private static final String COLUMN_WRITING_REMEDIATION_PENDING = "writing_remediation_pending";
    private static final String COLUMN_WRITING_PASSED = "writing_passed";
    private static final String COLUMN_WRITING_REQUIRED = "writing_required";
    private static final String STUDY_ITEMS_TABLE_SQL = SQL_CREATE_TABLE + TABLE_STUDY_ITEMS + " (kanji TEXT NOT NULL, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, recognition_stage INTEGER NOT NULL DEFAULT 0, consecutive_failed_recognition_days INTEGER NOT NULL DEFAULT 0, last_failed_recognition_day INTEGER NOT NULL DEFAULT 0, writing_remediation_pending INTEGER NOT NULL DEFAULT 0, suppressed_by_task_type TEXT NOT NULL DEFAULT '', suppressed_at INTEGER NOT NULL DEFAULT 0, mature_interval_days INTEGER NOT NULL DEFAULT 0, answer_signature TEXT NOT NULL DEFAULT '', typing_meaning_memory TEXT NOT NULL DEFAULT '', kanji_meaning_memory TEXT NOT NULL DEFAULT '', font_meaning_memory TEXT NOT NULL DEFAULT '', word_reading_memory TEXT NOT NULL DEFAULT '', writing_remediation_memory TEXT NOT NULL DEFAULT '', active_token TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature))";
    private static final String LEARNING_REPEATS_TABLE_SQL = SQL_CREATE_TABLE + TABLE_LEARNING_REPEATS + " (kanji TEXT NOT NULL, answer_signature TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL, repeat_type TEXT NOT NULL, step_index INTEGER NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature, task_type))";
    private static final String REVIEW_LOG_TABLE_SQL = SQL_CREATE_TABLE + TABLE_REVIEW_LOG + " (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL, task_type TEXT NOT NULL DEFAULT '', answer_signature TEXT NOT NULL DEFAULT '', prompt TEXT NOT NULL DEFAULT '', hints_used INTEGER NOT NULL DEFAULT 0, writing_clean INTEGER NOT NULL DEFAULT 0, memory_before TEXT NOT NULL DEFAULT '', memory_after TEXT NOT NULL DEFAULT '', scheduler_state_after_json TEXT NOT NULL DEFAULT '')";
    private static final String STUDY_TASK_LOG_TABLE_SQL = SQL_CREATE_TABLE_IF_NEEDED + TABLE_STUDY_TASK_LOG + " (id INTEGER PRIMARY KEY AUTOINCREMENT, task_key TEXT NOT NULL UNIQUE, kanji TEXT NOT NULL, task_type TEXT NOT NULL, started_at INTEGER NOT NULL, answered_at INTEGER NOT NULL, active_elapsed_ms INTEGER NOT NULL, outcome TEXT NOT NULL)";
    private static final long MAX_STUDY_TASK_ELAPSED_MS = 30L * 60L * 1000L;
    private static final String RATING_AGAIN = "again";
    private static final String STATE_RETIRED = "retired";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_COMPLETE = "complete";
    private static final String TIMELINE_FIRST_SEEN = "first_seen";
    private static final String TIMELINE_FIRST_SEEN_TITLE = "Kani started watching";
    private static final String TIMELINE_FIRST_SEEN_KEY_PREFIX = "first_seen:";
    private static final String COLUMN_MATURE = "mature";
    private static final String COLUMN_FIRST_IMPORTED_AT = "first_imported_at";
    private static final String KEY_AUTO_SYNC_LAST_ATTEMPT_AT = "auto_sync_last_attempt_at";
    private static final String KEY_AUTO_SYNC_LAST_SUCCESS_AT = "auto_sync_last_success_at";
    private static final String KEY_AUTO_SYNC_NEXT_RUN_AT = "auto_sync_next_run_at";
    private static final String SIMILAR_KEY_DELIMITER = "\u0000";
    private static final String SIMILAR_CHOICE_KEY_DELIMITER = "\u0001";
    private static final int DEFAULT_REMINDER_HOUR = 19;
    private static final int DEFAULT_REMINDER_MINUTE = 0;
    private static final int DEFAULT_AUTO_SYNC_HOUR = DEFAULT_REMINDER_HOUR;
    private static final int DEFAULT_AUTO_SYNC_MINUTE = DEFAULT_REMINDER_MINUTE;
    private static final String KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled";
    private static final String KEY_AUTO_UPDATE_LAST_CHECK_AT = "auto_update_last_check_at";
    private static final String KEY_AUTO_UPDATE_LAST_RESULT = "auto_update_last_result";
    private static final String KEY_AUTO_UPDATE_LAST_VERSION = "auto_update_last_version";
    private static final String KEY_AUTO_UPDATE_PENDING_APK = "auto_update_pending_apk";
    private static final int MAX_DISPLAYED_INVENTORY_READINGS = 3;
    private static final String KEY_AUTO_UPDATE_PENDING_MESSAGE = "auto_update_pending_message";

    public LocalStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE + TABLE_SETTINGS + " (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_SYNC_RUNS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, active_notes_count INTEGER NOT NULL, active_cards_count INTEGER NOT NULL, suspended_cards_archived_count INTEGER NOT NULL, suspended_kanji_imported_count INTEGER NOT NULL, deleted_notes_count INTEGER NOT NULL, deleted_cards_count INTEGER NOT NULL, error_code TEXT, error_message TEXT, removal_message TEXT)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_SOURCE_NOTES + " (note_id INTEGER PRIMARY KEY, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, tags TEXT NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_SOURCE_CARDS + " (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_SUSPENDED_ARCHIVE + " (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, archived_at INTEGER NOT NULL, archived_sync_id INTEGER NOT NULL, restored_at INTEGER)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_SUSPENDED_IMPORTS + " (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, rank_known INTEGER NOT NULL, cutoff_used INTEGER NOT NULL, first_imported_at INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_SUSPENDED_SOURCES + " (kanji TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, sync_id INTEGER NOT NULL, PRIMARY KEY (kanji, card_id))");
        db.execSQL(SQL_CREATE_TABLE + TABLE_DASHBOARD_ROWS + " (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, primary_meaning TEXT NOT NULL, reading TEXT NOT NULL, browser_search TEXT NOT NULL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE + TABLE_KANJI_EXAMPLES + " (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, mature INTEGER NOT NULL, lapses INTEGER NOT NULL, interval_days INTEGER NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL)");
        createKanjiInventoryTables(db);
        createSimilarKanjiTables(db);
        createSimilarKanjiPracticeTables(db);
        db.execSQL(STUDY_ITEMS_TABLE_SQL);
        db.execSQL(LEARNING_REPEATS_TABLE_SQL);
        db.execSQL(REVIEW_LOG_TABLE_SQL);
        createStudyTaskLogTable(db);
        db.execSQL("CREATE INDEX idx_examples_kanji ON " + TABLE_KANJI_EXAMPLES + "(kanji)");
        db.execSQL("CREATE INDEX idx_study_due ON " + TABLE_STUDY_ITEMS + "(state, due_at)");
        db.execSQL("CREATE INDEX idx_learning_repeats_due ON " + TABLE_LEARNING_REPEATS + "(due_at)");
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
            addNullableColumn(db, TABLE_SOURCE_CARDS, COLUMN_FSRS_STABILITY, "REAL");
            addNullableColumn(db, TABLE_SOURCE_CARDS, COLUMN_FSRS_DIFFICULTY, "REAL");
            addNullableColumn(db, TABLE_SOURCE_CARDS, COLUMN_FSRS_RETRIEVABILITY, "REAL");
            addNullableColumn(db, TABLE_KANJI_EXAMPLES, COLUMN_INTERVAL_DAYS, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_KANJI_EXAMPLES, COLUMN_REPS, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_KANJI_EXAMPLES, COLUMN_FSRS_STABILITY, "REAL");
            addNullableColumn(db, TABLE_KANJI_EXAMPLES, COLUMN_FSRS_DIFFICULTY, "REAL");
            addNullableColumn(db, TABLE_KANJI_EXAMPLES, COLUMN_FSRS_RETRIEVABILITY, "REAL");
        }
        if (oldVersion < 4) {
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_RECOGNITION_STAGE, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_LAST_FAILED_RECOGNITION_DAY, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_WRITING_REMEDIATION_PENDING, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        }
        if (oldVersion < 5) {
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_SUPPRESSED_BY_TASK_TYPE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_SUPPRESSED_AT, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_MATURE_INTERVAL_DAYS, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_ANSWER_SIGNATURE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        }
        if (oldVersion < 6) {
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_KANJI_MEANING_MEMORY, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_FONT_MEANING_MEMORY, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_WORD_READING_MEMORY, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_WRITING_REMEDIATION_MEMORY, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        }
        if (oldVersion < 7) {
            rebuildStudyItemsWithAnswerSignatureKey(db);
        }
        if (oldVersion < 8) {
            db.execSQL(LEARNING_REPEATS_TABLE_SQL);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learning_repeats_due ON " + TABLE_LEARNING_REPEATS + "(due_at)");
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
            addNullableColumn(db, TABLE_STUDY_ITEMS, COLUMN_TYPING_MEANING_MEMORY, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        }
        if (oldVersion < 15) {
            createStudyTaskLogTable(db);
        }
    }

    private void createStudyTaskLogTable(SQLiteDatabase db) {
        db.execSQL(STUDY_TASK_LOG_TABLE_SQL);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_task_log_answered ON " + TABLE_STUDY_TASK_LOG + "(answered_at)");
    }

    private void createKanjiInventoryTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_KANJI_INVENTORY + " (kanji TEXT PRIMARY KEY, primary_meaning TEXT NOT NULL, readings TEXT NOT NULL, browser_search TEXT NOT NULL, search_text TEXT NOT NULL, source_count INTEGER NOT NULL, example_count INTEGER NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_LOCAL_KANJI_SUSPENSIONS + " (kanji TEXT PRIMARY KEY, suspended_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kanji_inventory_search ON kanji_inventory(search_text)");
    }

    private void createSimilarKanjiTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_PAIRS + " (kanji_a TEXT NOT NULL, kanji_b TEXT NOT NULL, source TEXT NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (kanji_a, kanji_b, source))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_a ON similar_kanji_pairs(kanji_a)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_b ON similar_kanji_pairs(kanji_b)");
    }

    private void createSimilarKanjiPracticeTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_CHOICE_STATE + " (target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, primary_meaning TEXT NOT NULL, choices TEXT NOT NULL, due_at INTEGER NOT NULL, passed_at INTEGER NOT NULL DEFAULT 0, last_reviewed_at INTEGER NOT NULL DEFAULT 0, correct_count INTEGER NOT NULL DEFAULT 0, wrong_count INTEGER NOT NULL DEFAULT 0, active_token TEXT NOT NULL DEFAULT '', first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (target_kanji, choice_signature))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_choice_due ON similar_kanji_choice_state(passed_at, due_at)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_REPAIR_QUEUE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, repair_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, wrong_selection TEXT NOT NULL, prompt_meaning TEXT NOT NULL, status TEXT NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', attempts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_repair_due ON similar_kanji_repair_queue(status, due_at, created_at)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_REVIEW_LOG + " (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, selected_kanji TEXT NOT NULL, correct INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_review_log_target ON similar_kanji_review_log(target_kanji, reviewed_at)");
    }

    private void createHistoricalSyncTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SYNC_CARD_SNAPSHOTS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, sync_id INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, deck_id TEXT NOT NULL DEFAULT '', deck_name TEXT NOT NULL, model_id INTEGER NOT NULL DEFAULT 0, model_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, suspended INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, mature INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_card_snapshots_sync_card ON " + TABLE_SYNC_CARD_SNAPSHOTS + "(sync_id, card_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_card_snapshots_note ON " + TABLE_SYNC_CARD_SNAPSHOTS + "(sync_id, note_id)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SYNC_NOTE_SNAPSHOTS + " (sync_id INTEGER NOT NULL, finished_at INTEGER NOT NULL, note_id INTEGER NOT NULL, model_id INTEGER NOT NULL DEFAULT 0, model_name TEXT NOT NULL, deck_ids TEXT NOT NULL DEFAULT '', deck_names TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, tags TEXT NOT NULL, fields_json TEXT NOT NULL, extracted_kanji TEXT NOT NULL, PRIMARY KEY (sync_id, note_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_note_snapshots_kanji ON " + TABLE_SYNC_NOTE_SNAPSHOTS + "(sync_id, extracted_kanji)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SYNC_KANJI_SNAPSHOTS + " (sync_id INTEGER NOT NULL, finished_at INTEGER NOT NULL, kanji TEXT NOT NULL, active_cards INTEGER NOT NULL, suspended_cards INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, average_interval_days REAL NOT NULL, total_lapses INTEGER NOT NULL, total_reps INTEGER NOT NULL, fsrs_stability_avg REAL, fsrs_difficulty_avg REAL, fsrs_retrievability_avg REAL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, PRIMARY KEY (sync_id, kanji))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_kanji_snapshots_kanji_sync ON " + TABLE_SYNC_KANJI_SNAPSHOTS + "(kanji, sync_id)");
    }

    private void addHistoricalIdentityColumns(SQLiteDatabase db) {
        addNullableColumn(db, TABLE_SYNC_CARD_SNAPSHOTS, COLUMN_DECK_ID, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_SYNC_CARD_SNAPSHOTS, COLUMN_MODEL_ID, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_SYNC_NOTE_SNAPSHOTS, COLUMN_MODEL_ID, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_SYNC_NOTE_SNAPSHOTS, COLUMN_DECK_IDS, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        db.execSQL("UPDATE " + TABLE_SYNC_CARD_SNAPSHOTS + " SET deck_id=deck_name WHERE deck_id=''");
        db.execSQL("UPDATE " + TABLE_SYNC_NOTE_SNAPSHOTS + " SET deck_ids=deck_names WHERE deck_ids=''");
    }

    private void addRichReviewColumns(SQLiteDatabase db) {
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_TASK_TYPE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_ANSWER_SIGNATURE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "prompt", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "hints_used", SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_REVIEW_LOG, "writing_clean", SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_REVIEW_LOG, "memory_before", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "memory_after", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "scheduler_state_after_json", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
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
            int deletedNotes = countDeletedExisting(db, TABLE_SOURCE_NOTES, COLUMN_NOTE_ID, activeIndex.noteIds);
            int deletedCards = countDeletedExisting(db, TABLE_SOURCE_CARDS, COLUMN_CARD_ID, activeIndex.cardIds);
            long syncId = insertSyncRun(db, new SyncRunInsert(
                    timing.startedAt,
                    timing.finishedAt,
                    STATUS_SUCCESS,
                    activeIndex,
                    imports.size(),
                    null,
                    null,
                    removal == null ? "" : removal.message,
                    deletedNotes,
                    deletedCards
            ));
            Map<Long, Records.Note> notesById = snapshot.notesById();
            appendHistoricalSyncSnapshots(db, snapshot, notesById, rows, settings, syncId, timing);
            clearSyncMirrorTables(db);
            saveSourceNotes(db, snapshot.notes, activeIndex, settings, syncId);
            saveSourceCardsAndArchive(db, snapshot.cards, notesById, settings, timing.finishedAt, syncId);
            saveSuspendedImports(db, imports, timing.finishedAt, syncId);

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

    private void clearSyncMirrorTables(SQLiteDatabase db) {
        db.delete(TABLE_SOURCE_CARDS, null, null);
        db.delete(TABLE_SOURCE_NOTES, null, null);
        db.delete(TABLE_DASHBOARD_ROWS, null, null);
        db.delete(TABLE_KANJI_EXAMPLES, null, null);
    }

    private void saveSourceNotes(
            SQLiteDatabase db,
            List<Records.Note> notes,
            ActiveCardIndex activeIndex,
            Records.Settings settings,
            long syncId
    ) {
        for (Records.Note note : notes) {
            if (!activeIndex.noteIds.contains(note.noteId)) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_NOTE_ID, note.noteId);
            values.put(COLUMN_MODEL_NAME, note.modelName);
            values.put(COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)));
            values.put(COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)));
            values.put(COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)));
            values.put(COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)));
            values.put(COLUMN_FIELDS_JSON, fieldsJson(note.fields));
            values.put(COLUMN_TAGS, String.join(" ", note.tags));
            values.put(COLUMN_LAST_SEEN_SYNC_ID, syncId);
            db.insertWithOnConflict(TABLE_SOURCE_NOTES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void saveSourceCardsAndArchive(
            SQLiteDatabase db,
            List<Records.Card> cards,
            Map<Long, Records.Note> notesById,
            Records.Settings settings,
            long finishedAt,
            long syncId
    ) {
        for (Records.Card card : cards) {
            Records.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            if (card.suspended) {
                saveSuspendedArchiveCard(db, card, note, settings, finishedAt, syncId);
            } else {
                saveSourceCard(db, card, syncId);
            }
        }
    }

    private void saveSuspendedArchiveCard(
            SQLiteDatabase db,
            Records.Card card,
            Records.Note note,
            Records.Settings settings,
            long finishedAt,
            long syncId
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CARD_ID, card.cardId);
        values.put(COLUMN_NOTE_ID, card.noteId);
        values.put(COLUMN_DECK_NAME, card.deckName);
        values.put(COLUMN_MODEL_NAME, note.modelName);
        values.put(COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)));
        values.put(COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)));
        values.put(COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)));
        values.put(COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)));
        values.put(COLUMN_FIELDS_JSON, fieldsJson(note.fields));
        values.put("archived_at", finishedAt);
        values.put("archived_sync_id", syncId);
        db.insertWithOnConflict(TABLE_SUSPENDED_ARCHIVE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void saveSourceCard(SQLiteDatabase db, Records.Card card, long syncId) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CARD_ID, card.cardId);
        values.put(COLUMN_NOTE_ID, card.noteId);
        values.put(COLUMN_DECK_NAME, card.deckName);
        values.put("ord", card.ord);
        values.put(COLUMN_QUEUE, card.queue);
        values.put("type", card.type);
        values.put("due", card.due);
        values.put(COLUMN_INTERVAL_DAYS, card.intervalDays);
        values.put(COLUMN_REPS, card.reps);
        values.put(COLUMN_LAPSES, card.lapses);
        putNullableDouble(values, COLUMN_FSRS_STABILITY, card.fsrsStability);
        putNullableDouble(values, COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty);
        putNullableDouble(values, COLUMN_FSRS_RETRIEVABILITY, card.fsrsRetrievability);
        values.put(COLUMN_LAST_SEEN_SYNC_ID, syncId);
        db.insertWithOnConflict(TABLE_SOURCE_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void saveSuspendedImports(
            SQLiteDatabase db,
            List<Records.SuspendedImport> imports,
            long finishedAt,
            long syncId
    ) {
        for (Records.SuspendedImport imported : imports) {
            saveSuspendedImport(db, imported, finishedAt, syncId);
        }
    }

    private void saveSuspendedImport(
            SQLiteDatabase db,
            Records.SuspendedImport imported,
            long finishedAt,
            long syncId
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, imported.kanji);
        if (imported.jitenRank != null) {
            values.put(COLUMN_JITEN_RANK, imported.jitenRank);
        }
        values.put(COLUMN_RANK_KNOWN, imported.rankKnown ? 1 : 0);
        values.put(COLUMN_CUTOFF_USED, imported.cutoffUsed);
        values.put(COLUMN_FIRST_IMPORTED_AT, firstImportedAt(db, imported.kanji, finishedAt));
        values.put(COLUMN_LAST_SEEN_SYNC_ID, syncId);
        db.insertWithOnConflict(TABLE_SUSPENDED_IMPORTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        for (Records.SuspendedSource source : imported.sources) {
            ContentValues sourceValues = new ContentValues();
            sourceValues.put(COLUMN_KANJI, imported.kanji);
            sourceValues.put(COLUMN_CARD_ID, source.cardId);
            sourceValues.put(COLUMN_NOTE_ID, source.noteId);
            sourceValues.put(COLUMN_EXPRESSION, source.expression);
            sourceValues.put(COLUMN_READING, source.reading);
            sourceValues.put(COLUMN_MEANING, source.meaning);
            sourceValues.put(COLUMN_SENTENCE, source.sentence);
            sourceValues.put(COLUMN_SYNC_ID, syncId);
            db.insertWithOnConflict(TABLE_SUSPENDED_SOURCES, null, sourceValues, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public void saveFailedSync(long startedAt, long finishedAt, String status, String errorCode, String errorMessage) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STARTED_AT, startedAt);
        values.put(COLUMN_FINISHED_AT, finishedAt);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_ACTIVE_NOTES_COUNT, 0);
        values.put(COLUMN_ACTIVE_CARDS_COUNT, 0);
        values.put(COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, 0);
        values.put(COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, 0);
        values.put("deleted_notes_count", 0);
        values.put("deleted_cards_count", 0);
        values.put("error_code", errorCode);
        values.put(COLUMN_ERROR_MESSAGE, errorMessage);
        values.put(COLUMN_REMOVAL_MESSAGE, "");
        db.insert(TABLE_SYNC_RUNS, null, values);
    }

    public void updateSyncRemovalMessage(long syncId, String message) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_REMOVAL_MESSAGE, message == null ? "" : message);
        getWritableDatabase().update(TABLE_SYNC_RUNS, values, "id=?", new String[]{Long.toString(syncId)});
    }

    public List<Records.DashboardRow> dashboardRows() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.DashboardRow> rows = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, "weakness_score DESC, suspended_example_count DESC, kanji ASC", "120")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                rows.add(new Records.DashboardRow(
                        kanji,
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        string(cursor, COLUMN_PRIMARY_MEANING),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_BROWSER_SEARCH),
                        integer(cursor, COLUMN_WEAKNESS_SCORE),
                        string(cursor, COLUMN_REASON_CODE),
                        string(cursor, COLUMN_REASON_TEXT),
                        integer(cursor, COLUMN_ACTIVE_EXAMPLE_COUNT),
                        integer(cursor, COLUMN_SUSPENDED_EXAMPLE_COUNT),
                        integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                        examplesForKanji(db, kanji)
                ));
            }
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
        try (Cursor cursor = db.query(
                TABLE_KANJI_INVENTORY,
                null,
                selection,
                args,
                null,
                null,
                ORDER_KANJI_ASC,
                "300"
        )) {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
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
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, null, null, null, null, null, ORDER_SIMILAR_PAIR)) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
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
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_PAIRS,
                null,
                "kanji_a=? OR kanji_b=?",
                new String[]{normalized, normalized},
                null,
                null,
                ORDER_SIMILAR_PAIR
        )) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
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
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_SIMILAR_KANJI_PAIRS,
                new String[]{COLUMN_KANJI_A},
                "kanji_a=? AND kanji_b=?",
                pair,
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    public List<Records.SimilarKanjiChoiceCard> allSimilarChoiceCards() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiChoiceCard> out = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                null,
                null,
                null,
                null,
                "target_kanji ASC, choice_signature ASC"
        )) {
            while (cursor.moveToNext()) {
                out.add(readSimilarChoiceCard(cursor));
            }
        }
        return out;
    }

    public Records.SimilarKanjiChoiceCard dueSimilarChoiceForActiveTarget(String kanji, long nowMillis) {
        String target = normalizeSingleKanji(kanji);
        if (target.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                "target_kanji=? AND passed_at=0 AND due_at<=?",
                new String[]{target, Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, first_seen_at ASC",
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Records.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
            return hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature) ? null : card;
        }
    }

    public Records.SimilarKanjiChoiceCard nextDueInventorySimilarChoice(Set<String> activeTargets, long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                "passed_at=0 AND due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, last_reviewed_at ASC, target_kanji ASC"
        )) {
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
        }
    }

    public int dueSimilarStudyTaskCount(long nowMillis) {
        return dueSimilarChoiceTaskCount(nowMillis) + dueSimilarWritingRepairTaskCount(nowMillis);
    }

    public int dueSimilarChoiceTaskCount(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        int count = 0;
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                new String[]{COLUMN_TARGET_KANJI, COLUMN_CHOICE_SIGNATURE},
                "passed_at=0 AND due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                String targetKanji = string(cursor, COLUMN_TARGET_KANJI);
                String choiceSignature = string(cursor, COLUMN_CHOICE_SIGNATURE);
                if (!hasPendingSimilarRepairs(db, targetKanji, choiceSignature)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int dueSimilarWritingRepairTaskCount(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_SIMILAR_KANJI_REPAIR_QUEUE + " WHERE status=? AND due_at<=?",
                new String[]{STATUS_PENDING, Long.toString(nowMillis)}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
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
            values.put(COLUMN_LAST_REVIEWED_AT, nowMillis);
            if (result.correct) {
                values.put(COLUMN_PASSED_AT, nowMillis);
                values.put(COLUMN_CORRECT_COUNT, card.correctCount + 1);
            } else {
                values.put(COLUMN_PASSED_AT, 0L);
                values.put(COLUMN_DUE_AT, nowMillis);
                values.put(COLUMN_WRONG_COUNT, card.wrongCount + 1);
            }
            db.update(
                    TABLE_SIMILAR_KANJI_CHOICE_STATE,
                    values,
                    WHERE_SIMILAR_CHOICE,
                    new String[]{card.targetKanji, card.choiceSignature}
            );

            ContentValues log = new ContentValues();
            log.put(COLUMN_TARGET_KANJI, card.targetKanji);
            log.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
            log.put("selected_kanji", result.selectedKanji);
            log.put("correct", result.correct ? 1 : 0);
            log.put(COLUMN_REVIEWED_AT, nowMillis);
            db.insert(TABLE_SIMILAR_KANJI_REVIEW_LOG, null, log);

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
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                null,
                "status=? AND due_at<=?",
                new String[]{STATUS_PENDING, Long.toString(nowMillis)},
                null,
                null,
                "created_at ASC, id ASC",
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readSimilarWritingRepair(cursor);
        }
    }

    public void saveSimilarWritingRepair(Records.SimilarKanjiWritingRepair repair) {
        if (repair == null || repair.id <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_ACTIVE_TOKEN, repair.activeToken);
        values.put(COLUMN_UPDATED_AT, repair.updatedAtMillis);
        getWritableDatabase().update(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                values,
                "id=? AND status=?",
                new String[]{Long.toString(repair.id), STATUS_PENDING}
        );
    }

    public boolean finishSimilarWritingRepair(long repairId, String token, boolean passed, long nowMillis) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Records.SimilarKanjiWritingRepair current = similarWritingRepair(db, repairId);
            if (current == null || !STATUS_PENDING.equals(current.status)) {
                return false;
            }
            if (!current.activeToken.isEmpty() && !current.activeToken.equals(token == null ? "" : token)) {
                return false;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_ACTIVE_TOKEN, "");
            values.put(COLUMN_UPDATED_AT, nowMillis);
            if (passed) {
                values.put(COLUMN_STATUS, STATUS_COMPLETE);
                values.put(COLUMN_COMPLETED_AT, nowMillis);
            } else {
                values.put(COLUMN_ATTEMPTS, current.attempts + 1);
                values.put(COLUMN_DUE_AT, nowMillis);
            }
            db.update(TABLE_SIMILAR_KANJI_REPAIR_QUEUE, values, "id=?", new String[]{Long.toString(repairId)});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public Set<String> locallySuspendedKanji() {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                out.add(string(cursor, COLUMN_KANJI));
            }
        }
        return out;
    }

    public boolean isKanjiLocallySuspended(String kanji) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1")) {
            return cursor.moveToFirst();
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
                values.put(COLUMN_KANJI, kanji);
                values.put("suspended_at", nowMillis);
                db.insertWithOnConflict(TABLE_LOCAL_KANJI_SUSPENSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                db.delete(TABLE_LEARNING_REPEATS, WHERE_KANJI, new String[]{kanji});
            } else {
                db.delete(TABLE_LOCAL_KANJI_SUSPENSIONS, WHERE_KANJI, new String[]{kanji});
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
                TABLE_KANJI_TIMELINE_EVENTS,
                null,
                WHERE_KANJI,
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
        try (Cursor cursor = db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "due_at ASC")) {
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor));
            }
        }
        return items;
    }

    public List<Records.SuspendedImport> suspendedImports() {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        try (Cursor cursor = db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "jiten_rank ASC, kanji ASC")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        integer(cursor, COLUMN_RANK_KNOWN) == 1,
                        integer(cursor, COLUMN_CUTOFF_USED)
                ));
            }
        }

        try (Cursor sources = db.query(TABLE_SUSPENDED_SOURCES, null, null, null, null, null, "kanji ASC, card_id ASC")) {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, COLUMN_KANJI));
                if (imported == null) {
                    continue;
                }
                imported.sources.add(new Records.SuspendedSource(
                        imported.kanji,
                        longValue(sources, COLUMN_CARD_ID),
                        longValue(sources, COLUMN_NOTE_ID),
                        string(sources, COLUMN_EXPRESSION),
                        string(sources, COLUMN_READING),
                        string(sources, COLUMN_MEANING),
                        string(sources, COLUMN_SENTENCE)
                ));
            }
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
            db.delete(TABLE_STUDY_ITEMS, null, null);
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
        values.put(COLUMN_KANJI, request.kanji);
        values.put(COLUMN_TOKEN, request.token);
        values.put(COLUMN_RATING, appliedRating);
        values.put(COLUMN_WRITING_REQUIRED, request.writingRequired ? 1 : 0);
        values.put(COLUMN_WRITING_PASSED, request.writingPassed ? 1 : 0);
        values.put(COLUMN_MANUAL_OVERRIDE, request.manualOverride ? 1 : 0);
        values.put(COLUMN_REVIEWED_AT, reviewedAt);
        values.put(COLUMN_TASK_TYPE, request.taskType);
        values.put(COLUMN_ANSWER_SIGNATURE, request.answerSignature);
        values.put("prompt", request.prompt);
        values.put("hints_used", request.hintsUsed);
        values.put("writing_clean", request.writingClean ? 1 : 0);
        values.put("memory_before", taskMemoryText(beforeReview, request.taskType));
        values.put("memory_after", taskMemoryText(afterReview, request.taskType));
        values.put("scheduler_state_after_json", studyItemSchedulerJson(afterReview));
        return db.insertWithOnConflict(TABLE_REVIEW_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE);
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
        try (Cursor cursor = getReadableDatabase().query(TABLE_REVIEW_LOG, new String[]{COLUMN_TOKEN}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                tokens.add(string(cursor, COLUMN_TOKEN));
            }
        }
        return tokens;
    }

    public SyncStatus latestSync() {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SYNC_RUNS, null, null, null, null, null, ORDER_ID_DESC, "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new SyncStatus(new SyncStatusValues(
                    string(cursor, COLUMN_STATUS),
                    integer(cursor, COLUMN_ACTIVE_NOTES_COUNT),
                    integer(cursor, COLUMN_ACTIVE_CARDS_COUNT),
                    integer(cursor, COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT),
                    integer(cursor, COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT),
                    longValue(cursor, COLUMN_FINISHED_AT),
                    string(cursor, COLUMN_ERROR_MESSAGE),
                    string(cursor, COLUMN_REMOVAL_MESSAGE)
            ));
        }
    }

    public boolean hasSuccessfulSyncSince(long finishedAtMillis) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_SYNC_RUNS,
                new String[]{"id"},
                "status=? AND finished_at>=?",
                new String[]{STATUS_SUCCESS, Long.toString(finishedAtMillis)},
                null,
                null,
                ORDER_ID_DESC,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    public int getIntSetting(String key, int fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Integer.parseInt(string(cursor, COLUMN_VALUE));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    public long getLongSetting(String key, long fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Long.parseLong(string(cursor, COLUMN_VALUE));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    public String getStringSetting(String key, String fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            String value = string(cursor, COLUMN_VALUE);
            return value == null ? fallback : value;
        }
    }

    public double getDoubleSetting(String key, double fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Double.parseDouble(string(cursor, COLUMN_VALUE));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
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

    public int adaptiveLoadMaxItems() {
        return AdaptiveLoadPlanner.normalizeMaxItems(getIntSetting(
                "adaptive_load_max_items",
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
        ));
    }

    public void saveAdaptiveLoadMaxItems(int maxItems) {
        putIntSetting("adaptive_load_max_items", AdaptiveLoadPlanner.normalizeMaxItems(maxItems));
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
                getLongSetting(KEY_AUTO_SYNC_LAST_ATTEMPT_AT, 0L),
                getLongSetting(KEY_AUTO_SYNC_LAST_SUCCESS_AT, 0L),
                getLongSetting(KEY_AUTO_SYNC_NEXT_RUN_AT, 0L)
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
        putLongSetting(KEY_AUTO_SYNC_NEXT_RUN_AT, nextRunAt);
    }

    public void recordAutoSyncAttempt(long attemptedAt, boolean success) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(KEY_AUTO_SYNC_LAST_ATTEMPT_AT, attemptedAt);
            if (success) {
                putLongSetting(KEY_AUTO_SYNC_LAST_SUCCESS_AT, attemptedAt);
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
            putLongSetting(KEY_AUTO_SYNC_LAST_ATTEMPT_AT, normalized.lastAttemptAt);
            putLongSetting(KEY_AUTO_SYNC_LAST_SUCCESS_AT, normalized.lastSuccessAt);
            putLongSetting(KEY_AUTO_SYNC_NEXT_RUN_AT, normalized.nextRunAt);
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
        values.put(COLUMN_KANJI, repeat.kanji);
        values.put(COLUMN_ANSWER_SIGNATURE, repeat.answerSignature);
        values.put(COLUMN_TASK_TYPE, repeat.taskType);
        values.put("repeat_type", repeat.repeatType);
        values.put("step_index", repeat.stepIndex);
        values.put(COLUMN_DUE_AT, repeat.dueAtMillis);
        values.put(COLUMN_ACTIVE_TOKEN, repeat.activeToken);
        values.put(COLUMN_CREATED_AT, repeat.createdAtMillis);
        values.put(COLUMN_UPDATED_AT, repeat.updatedAtMillis);
        getWritableDatabase().insertWithOnConflict(TABLE_LEARNING_REPEATS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
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
                TABLE_LEARNING_REPEATS,
                "kanji=? AND answer_signature=? AND task_type=?",
                new String[]{repeat.kanji, repeat.answerSignature, repeat.taskType}
        );
    }

    public List<Records.LearningRepeat> dueLearningRepeats(long nowMillis) {
        List<Records.LearningRepeat> repeats = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                TABLE_LEARNING_REPEATS,
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
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_RATING, COLUMN_WRITING_REQUIRED, COLUMN_WRITING_PASSED, COLUMN_MANUAL_OVERRIDE},
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
                String rating = string(cursor, COLUMN_RATING);
                if (RATING_AGAIN.equals(rating)) {
                    again++;
                } else if ("hard".equals(rating)) {
                    hard++;
                } else if ("easy".equals(rating)) {
                    easy++;
                } else {
                    good++;
                }
                boolean required = integer(cursor, COLUMN_WRITING_REQUIRED) == 1;
                boolean passed = integer(cursor, COLUMN_WRITING_PASSED) == 1;
                boolean override = integer(cursor, COLUMN_MANUAL_OVERRIDE) == 1;
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

    public boolean recordStudyTaskAnswered(String taskKey, String kanji, String taskType, long startedAt, long answeredAt, long activeElapsedMillis, String outcome) {
        String normalizedKey = taskKey == null ? "" : taskKey;
        if (normalizedKey.isEmpty()) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put("task_key", normalizedKey);
        values.put(COLUMN_KANJI, kanji == null ? "" : kanji);
        values.put(COLUMN_TASK_TYPE, taskType == null ? "" : taskType);
        values.put(COLUMN_STARTED_AT, Math.max(0L, startedAt));
        values.put("answered_at", Math.max(0L, answeredAt));
        values.put("active_elapsed_ms", Math.min(MAX_STUDY_TASK_ELAPSED_MS, Math.max(0L, activeElapsedMillis)));
        values.put("outcome", outcome == null ? "" : outcome);
        return getWritableDatabase().insertWithOnConflict(TABLE_STUDY_TASK_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L;
    }

    public StudyTaskTimeStats studyTaskTimeStats(long nowMillis) {
        long today = localDayStart(nowMillis);
        long tomorrow = moveLocalDays(today, 1);
        long sevenDayStart = moveLocalDays(today, -6);
        long todayMillis = sumStudyTaskElapsed(today, tomorrow);
        StudyTaskAggregate week = studyTaskAggregate(sevenDayStart, tomorrow);
        return new StudyTaskTimeStats(todayMillis, week.elapsedMillis, week.taskCount);
    }

    private long sumStudyTaskElapsed(long startMillis, long endMillis) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(active_elapsed_ms), 0) FROM study_task_log WHERE answered_at>=? AND answered_at<?",
                new String[]{Long.toString(startMillis), Long.toString(endMillis)}
        );
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private StudyTaskAggregate studyTaskAggregate(long startMillis, long endMillis) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(active_elapsed_ms), 0), COUNT(*) FROM study_task_log WHERE answered_at>=? AND answered_at<?",
                new String[]{Long.toString(startMillis), Long.toString(endMillis)}
        );
        try {
            if (!cursor.moveToFirst()) {
                return new StudyTaskAggregate(0L, 0);
            }
            return new StudyTaskAggregate(cursor.getLong(0), cursor.getInt(1));
        } finally {
            cursor.close();
        }
    }

    public List<RecentMistake> recentMistakes(int limit) {
        int boundedLimit = Math.max(1, limit);
        Cursor cursor = getReadableDatabase().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI, COLUMN_RATING, COLUMN_REVIEWED_AT},
                "rating IN (?, ?)",
                new String[]{RATING_AGAIN, "hard"},
                null,
                null,
                "reviewed_at DESC, id DESC",
                Integer.toString(boundedLimit)
        );
        List<RecentMistake> mistakes = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                mistakes.add(new RecentMistake(
                        string(cursor, COLUMN_KANJI),
                        string(cursor, COLUMN_RATING),
                        longValue(cursor, COLUMN_REVIEWED_AT)
                ));
            }
        } finally {
            cursor.close();
        }
        return mistakes;
    }

    public StudyStreak studyStreak(long nowMillis) {
        long today = localDayStart(nowMillis);
        StudyDays studyDays = studyDays(today);
        if (studyDays.days.isEmpty()) {
            return new StudyStreak(0, 0, false, 0, 0L);
        }
        boolean studiedToday = studyDays.days.get(0) == today;
        return new StudyStreak(
                currentStreak(studyDays.days, today),
                bestStreak(studyDays.days),
                studiedToday,
                studyDays.reviewsToday,
                studyDays.lastStudyAt
        );
    }

    private StudyDays studyDays(long today) {
        Cursor cursor = getReadableDatabase().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_REVIEWED_AT},
                null,
                null,
                null,
                null,
                "reviewed_at DESC"
        );
        List<Long> days = new ArrayList<>();
        int reviewsToday = 0;
        long tomorrow = moveLocalDays(today, 1);
        long lastStudyAt = 0L;
        try {
            long lastAddedDay = Long.MIN_VALUE;
            while (cursor.moveToNext()) {
                long reviewedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REVIEWED_AT));
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
        return new StudyDays(days, reviewsToday, lastStudyAt);
    }

    private int currentStreak(List<Long> days, long today) {
        long yesterday = moveLocalDays(today, -1);
        boolean studiedToday = days.get(0) == today;
        if (!studiedToday && days.get(0) != yesterday) {
            return 0;
        }
        long expected = studiedToday ? today : yesterday;
        int current = 0;
        for (long day : days) {
            if (day != expected) {
                break;
            }
            current++;
            expected = moveLocalDays(expected, -1);
        }
        return current;
    }

    private int bestStreak(List<Long> days) {
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
        return best;
    }

    public StudyImpactStats studyImpactStats() {
        Cursor cursor = getReadableDatabase().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI, COLUMN_WRITING_REQUIRED, COLUMN_WRITING_PASSED, COLUMN_MANUAL_OVERRIDE},
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
                reviewedKanji.add(string(cursor, COLUMN_KANJI));
                boolean required = integer(cursor, COLUMN_WRITING_REQUIRED) == 1;
                boolean passed = integer(cursor, COLUMN_WRITING_PASSED) == 1;
                boolean override = integer(cursor, COLUMN_MANUAL_OVERRIDE) == 1;
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

    public KaniOutcomeStats kaniOutcomeStats() {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, ReviewWindow> reviewWindows = reviewWindowsByKanji(db);
        if (reviewWindows.isEmpty()) {
            return KaniOutcomeStats.empty();
        }

        OutcomeAccumulator accumulator = new OutcomeAccumulator();
        for (ReviewWindow window : reviewWindows.values()) {
            OutcomeSnapshot before = latestOutcomeSnapshotBefore(db, window.kanji, window.firstReviewedAtMillis);
            OutcomeSnapshot after = latestOutcomeSnapshotAfter(db, window.kanji, window.lastReviewedAtMillis);
            accumulator.add(window.kanji, before, after);
        }

        accumulator.improvements.sort((left, right) -> {
            int dropCompare = Double.compare(right.beforeWeakness - right.afterWeakness, left.beforeWeakness - left.afterWeakness);
            return dropCompare == 0 ? left.kanji.compareTo(right.kanji) : dropCompare;
        });
        accumulator.supportGains.sort((left, right) -> {
            int gainCompare = Integer.compare(right.afterMatureSupport - right.beforeMatureSupport, left.afterMatureSupport - left.beforeMatureSupport);
            return gainCompare == 0 ? left.kanji.compareTo(right.kanji) : gainCompare;
        });

        int improvedCount = accumulator.improvements.size();
        WeakKanjiImprovedMetric weakMetric = new WeakKanjiImprovedMetric(
                improvedCount,
                improvedCount == 0 ? 0.0 : accumulator.beforeWeaknessSum / improvedCount,
                improvedCount == 0 ? 0.0 : accumulator.afterWeaknessSum / improvedCount,
                topThreeImprovements(accumulator.improvements)
        );
        MatureSupportGainedMetric supportMetric = new MatureSupportGainedMetric(
                accumulator.supportGains.size(),
                accumulator.firstSupportCount,
                topThreeSupportGains(accumulator.supportGains)
        );
        return new KaniOutcomeStats(weakMetric, supportMetric);
    }

    private Map<String, ReviewWindow> reviewWindowsByKanji(SQLiteDatabase db) {
        Map<String, ReviewWindow> out = new LinkedHashMap<>();
        Cursor cursor = db.rawQuery(
                "SELECT kanji, MIN(reviewed_at) AS first_reviewed_at, MAX(reviewed_at) AS last_reviewed_at FROM review_log GROUP BY kanji",
                null
        );
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                if (!kanji.isEmpty()) {
                    out.put(kanji, new ReviewWindow(
                            kanji,
                            longValue(cursor, "first_reviewed_at"),
                            longValue(cursor, COLUMN_LAST_REVIEWED_AT)
                    ));
                }
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private OutcomeSnapshot latestOutcomeSnapshotBefore(SQLiteDatabase db, String kanji, long reviewedAtMillis) {
        return outcomeSnapshot(db, kanji, "kanji=? AND finished_at<?", reviewedAtMillis);
    }

    private OutcomeSnapshot latestOutcomeSnapshotAfter(SQLiteDatabase db, String kanji, long reviewedAtMillis) {
        return outcomeSnapshot(db, kanji, "kanji=? AND finished_at>?", reviewedAtMillis);
    }

    private OutcomeSnapshot outcomeSnapshot(SQLiteDatabase db, String kanji, String selection, long reviewedAtMillis) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                new String[]{COLUMN_WEAKNESS_SCORE, COLUMN_MATURE_SUPPORT_COUNT},
                selection,
                new String[]{kanji, Long.toString(reviewedAtMillis)},
                null,
                null,
                "finished_at DESC, sync_id DESC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new OutcomeSnapshot(integer(cursor, COLUMN_WEAKNESS_SCORE), integer(cursor, COLUMN_MATURE_SUPPORT_COUNT));
        } finally {
            cursor.close();
        }
    }

    private static List<KanjiImprovement> topThreeImprovements(List<KanjiImprovement> improvements) {
        return new ArrayList<>(improvements.subList(0, Math.min(3, improvements.size())));
    }

    private static List<KanjiSupportGain> topThreeSupportGains(List<KanjiSupportGain> supportGains) {
        return new ArrayList<>(supportGains.subList(0, Math.min(3, supportGains.size())));
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
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI},
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
                kanji.add(string(cursor, COLUMN_KANJI));
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
        backfillSuspendedImportTimeline(db);
        backfillRowTimeline(db, rows);
        backfillStudyTimeline(db, rows);
        backfillReviewTimeline(db);
    }

    private void backfillSuspendedImportTimeline(SQLiteDatabase db) {
        Cursor imports = db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "first_imported_at ASC, kanji ASC");
        try {
            while (imports.moveToNext()) {
                String kanji = string(imports, COLUMN_KANJI);
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
                        longValue(imports, COLUMN_LAST_SEEN_SYNC_ID),
                        "suspended_imported:" + kanji
                );
            }
        } finally {
            imports.close();
        }
    }

    private void backfillRowTimeline(SQLiteDatabase db, Map<String, RowSnapshot> rows) {
        for (RowSnapshot row : rows.values()) {
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    TIMELINE_FIRST_SEEN,
                    TIMELINE_FIRST_SEEN_TITLE,
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
                    TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
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
    }

    private void backfillStudyTimeline(SQLiteDatabase db, Map<String, RowSnapshot> rows) {
        Cursor study = db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "created_at ASC, kanji ASC");
        try {
            while (study.moveToNext()) {
                backfillStudyTimelineRow(db, rows, study);
            }
        } finally {
            study.close();
        }
    }

    private void backfillStudyTimelineRow(SQLiteDatabase db, Map<String, RowSnapshot> rows, Cursor study) {
        String kanji = string(study, COLUMN_KANJI);
        long occurredAt = defaultTimelineTime(longValue(study, COLUMN_CREATED_AT));
        RowSnapshot row = rows.get(kanji);
        SourceSnapshot source = row == null ? firstExampleForKanji(db, kanji) : row.source;
        if (row == null) {
            insertTimelineEvent(
                    db,
                    kanji,
                    occurredAt,
                    TIMELINE_FIRST_SEEN,
                    TIMELINE_FIRST_SEEN_TITLE,
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
                    TIMELINE_FIRST_SEEN_KEY_PREFIX + kanji
            );
        }
        if (STATE_RETIRED.equals(string(study, COLUMN_STATE))) {
            Integer mature = row == null ? null : row.matureSupportCount;
            insertTimelineEvent(
                    db,
                    kanji,
                    occurredAt,
                    STATE_RETIRED,
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

    private void backfillReviewTimeline(SQLiteDatabase db) {
        Cursor reviews = db.query(TABLE_REVIEW_LOG, null, null, null, null, null, "reviewed_at ASC, id ASC");
        try {
            while (reviews.moveToNext()) {
                Records.ReviewRequest request = new Records.ReviewRequest(
                        string(reviews, COLUMN_KANJI),
                        string(reviews, COLUMN_TOKEN),
                        string(reviews, COLUMN_RATING),
                        integer(reviews, COLUMN_WRITING_REQUIRED) == 1,
                        integer(reviews, COLUMN_WRITING_PASSED) == 1,
                        integer(reviews, COLUMN_MANUAL_OVERRIDE) == 1,
                        0
                );
                appendReviewTimelineEvent(db, request, string(reviews, COLUMN_RATING), longValue(reviews, COLUMN_REVIEWED_AT), "review:" + request.token);
            }
        } finally {
            reviews.close();
        }
    }

    private long defaultTimelineTime(long occurredAt) {
        return occurredAt == 0L ? System.currentTimeMillis() : occurredAt;
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
                    TIMELINE_FIRST_SEEN,
                    TIMELINE_FIRST_SEEN_TITLE,
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
                    TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
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
            if (previous != null) {
                appendStudyStateTimelineEvent(db, item, previous, syncId, occurredAt, target);
            }
        }
    }

    private void appendStudyStateTimelineEvent(
            SQLiteDatabase db,
            Records.StudyItem item,
            StudySnapshot previous,
            long syncId,
            long occurredAt,
            int target
    ) {
        if (!stateRetirementChanged(item, previous)) {
            return;
        }
        RowSnapshot row = rowSnapshot(db, item.kanji);
        SourceSnapshot source = row == null ? firstExampleForKanji(db, item.kanji) : row.source;
        Integer mature = row == null ? null : row.matureSupportCount;
        boolean retired = STATE_RETIRED.equals(item.state);
        insertTimelineEvent(
                db,
                item.kanji,
                occurredAt,
                retired ? STATE_RETIRED : "reopened",
                retired ? "Retired by Anki support" : "Repair reopened",
                studyStateTimelineDetail(retired, mature, target),
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                row == null ? null : row.weaknessScore,
                mature,
                syncId,
                (retired ? "retired:" : "reopened:") + studyTimelineKey(item) + ":" + syncId
        );
    }

    private boolean stateRetirementChanged(Records.StudyItem item, StudySnapshot previous) {
        return STATE_RETIRED.equals(item.state) != STATE_RETIRED.equals(previous.state);
    }

    private String studyStateTimelineDetail(boolean retired, Integer mature, int target) {
        if (retired) {
            return mature == null
                    ? "No weak Anki evidence remained after sync, so Kani retired this repair."
                    : supportDetail("Mature Anki support met the target", mature, target);
        }
        return mature == null
                ? "Kani reopened this kanji after sync found weak evidence again."
                : supportDetail("Mature Anki support fell below target", mature, target);
    }

    private void appendReviewTimelineEvent(SQLiteDatabase db, Records.ReviewRequest request, String appliedRating, long reviewedAt, String dedupeKey) {
        String eventType;
        String title;
        if (request.manualOverride) {
            eventType = COLUMN_MANUAL_OVERRIDE;
            title = "Manual override";
        } else if (RATING_AGAIN.equals(appliedRating) || (request.writingRequired && !request.writingPassed)) {
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
        if (RATING_AGAIN.equals(appliedRating)) {
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
        try (Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, ORDER_KANJI_ASC)) {
            while (cursor.moveToNext()) {
                rows.add(readDashboardRow(db, cursor));
            }
        }
        return rows;
    }

    private List<Records.SuspendedImport> suspendedImportsFromDb(SQLiteDatabase db) {
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        try (Cursor cursor = db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "jiten_rank ASC, kanji ASC")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        integer(cursor, COLUMN_RANK_KNOWN) == 1,
                        integer(cursor, COLUMN_CUTOFF_USED)
                ));
            }
        }
        try (Cursor sources = db.query(TABLE_SUSPENDED_SOURCES, null, null, null, null, null, "kanji ASC, card_id ASC")) {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, COLUMN_KANJI));
                if (imported != null) {
                    imported.sources.add(new Records.SuspendedSource(
                            imported.kanji,
                            longValue(sources, COLUMN_CARD_ID),
                            longValue(sources, COLUMN_NOTE_ID),
                            string(sources, COLUMN_EXPRESSION),
                            string(sources, COLUMN_READING),
                            string(sources, COLUMN_MEANING),
                            string(sources, COLUMN_SENTENCE)
                    ));
                }
            }
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
        addSnapshotInventory(inventory, snapshot, settings);
        addImportedInventory(inventory, imports);
        addDashboardInventory(inventory, rows);
        addKnownKanji(inventory, db, TABLE_STUDY_ITEMS);
        addKnownKanji(inventory, db, TABLE_REVIEW_LOG);
        addKnownKanji(inventory, db, TABLE_KANJI_TIMELINE_EVENTS);
        writeKanjiInventory(db, inventory, nowMillis, settings);
    }

    private void addSnapshotInventory(
            Map<String, MutableKanjiInventoryItem> inventory,
            Records.CollectionSnapshot snapshot,
            Records.Settings settings
    ) {
        if (snapshot == null) {
            return;
        }
        ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
        for (Records.Note note : snapshot.notes) {
            if (activeIndex.noteIds.contains(note.noteId)) {
                addInventoryTextForNote(inventory, note, settings);
            }
        }
    }

    private void addInventoryTextForNote(
            Map<String, MutableKanjiInventoryItem> inventory,
            Records.Note note,
            Records.Settings settings
    ) {
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        String reading = TextUtil.normalizeJapanese(note.reading(settings));
        String meaning = TextUtil.firstMeaningLine(note.meaning(settings));
        String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
        addInventoryText(inventory, TextUtil.extractKanji(expression + " " + sentence), meaning, reading, expression, sentence);
    }

    private void addImportedInventory(Map<String, MutableKanjiInventoryItem> inventory, List<Records.SuspendedImport> imports) {
        for (Records.SuspendedImport imported : imports) {
            MutableKanjiInventoryItem item = inventoryItem(inventory, imported.kanji);
            for (Records.SuspendedSource source : imported.sources) {
                item.add(source.meaning, source.reading, source.expression, source.sentence);
            }
        }
    }

    private void addDashboardInventory(Map<String, MutableKanjiInventoryItem> inventory, List<Records.DashboardRow> rows) {
        for (Records.DashboardRow row : rows) {
            MutableKanjiInventoryItem item = inventoryItem(inventory, row.kanji);
            item.add(row.primaryMeaning, row.reading, row.reasonText, row.browserSearch);
            item.browserSearch = row.browserSearch;
            for (Records.Example example : row.examples) {
                item.exampleCount++;
                item.add(example.meaning, example.reading, example.expression, example.sentence);
            }
        }
    }

    private void addKnownKanji(Map<String, MutableKanjiInventoryItem> inventory, SQLiteDatabase db, String table) {
        try (Cursor cursor = db.query(true, table, new String[]{COLUMN_KANJI}, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                inventoryItem(inventory, string(cursor, COLUMN_KANJI));
            }
        }
    }

    private void writeKanjiInventory(
            SQLiteDatabase db,
            Map<String, MutableKanjiInventoryItem> inventory,
            long nowMillis,
            Records.Settings settings
    ) {
        for (MutableKanjiInventoryItem item : inventory.values()) {
            if (item.kanji.isEmpty()) {
                continue;
            }
            Records.KanjiInventoryItem previous = readInventoryItem(db, item.kanji);
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI, item.kanji);
            values.put(COLUMN_PRIMARY_MEANING, firstNonEmpty(item.primaryMeaning, previous == null ? "" : previous.primaryMeaning));
            values.put("readings", item.readingsText(previous == null ? "" : previous.readings));
            values.put(COLUMN_BROWSER_SEARCH, firstNonEmpty(item.browserSearch, previous == null ? TextUtil.browserSearchForKanji(item.kanji, settings) : previous.browserSearch));
            values.put("search_text", item.searchText(previous));
            values.put("source_count", Math.max(item.sourceCount, previous == null ? 0 : previous.sourceCount));
            values.put("example_count", Math.max(item.exampleCount, previous == null ? 0 : previous.exampleCount));
            values.put(COLUMN_FIRST_SEEN_AT, previous == null ? nowMillis : previous.lastSeenAtMillis);
            values.put(COLUMN_LAST_SEEN_AT, nowMillis);
            db.insertWithOnConflict(TABLE_KANJI_INVENTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void rebuildSimilarKanjiPairs(SQLiteDatabase db, SimilarKanjiIndex similarIndex, long nowMillis) {
        Map<String, Long> firstSeenByPair = similarPairFirstSeen(db);
        List<SimilarKanjiIndex.Pair> localPairs = similarIndex.pairsWithin(localInventoryKanji(db));
        db.delete(TABLE_SIMILAR_KANJI_PAIRS, null, null);
        for (SimilarKanjiIndex.Pair pair : localPairs) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI_A, pair.kanjiA);
            values.put(COLUMN_KANJI_B, pair.kanjiB);
            values.put(COLUMN_SOURCE, pair.source);
            values.put(COLUMN_FIRST_SEEN_AT, firstSeenByPair.getOrDefault(similarKey(pair.kanjiA, pair.kanjiB, pair.source), nowMillis));
            values.put(COLUMN_LAST_SEEN_AT, nowMillis);
            db.insertWithOnConflict(TABLE_SIMILAR_KANJI_PAIRS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
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
            upsertSimilarKanjiChoiceState(db, card, previous.get(key), nowMillis);
        }
        deleteStaleSimilarChoiceStates(db, previous.keySet(), currentKeys);
    }

    private void upsertSimilarKanjiChoiceState(
            SQLiteDatabase db,
            Records.SimilarKanjiChoiceCard card,
            SimilarChoiceSnapshot old,
            long nowMillis
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TARGET_KANJI, card.targetKanji);
        values.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
        values.put(COLUMN_PRIMARY_MEANING, card.primaryMeaning);
        values.put(COLUMN_CHOICES, serializeChoices(card.choices));
        values.put(COLUMN_DUE_AT, old == null ? 0L : old.dueAtMillis);
        values.put(COLUMN_PASSED_AT, old == null ? 0L : old.passedAtMillis);
        values.put(COLUMN_LAST_REVIEWED_AT, old == null ? 0L : old.lastReviewedAtMillis);
        values.put(COLUMN_CORRECT_COUNT, old == null ? 0 : old.correctCount);
        values.put(COLUMN_WRONG_COUNT, old == null ? 0 : old.wrongCount);
        values.put(COLUMN_ACTIVE_TOKEN, "");
        values.put(COLUMN_FIRST_SEEN_AT, old == null ? nowMillis : old.firstSeenAtMillis);
        values.put(COLUMN_LAST_SEEN_AT, nowMillis);
        db.insertWithOnConflict(TABLE_SIMILAR_KANJI_CHOICE_STATE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void deleteStaleSimilarChoiceStates(SQLiteDatabase db, Set<String> previousKeys, Set<String> currentKeys) {
        for (String key : previousKeys) {
            String[] parts = key.split(SIMILAR_CHOICE_KEY_DELIMITER, 2);
            if (!currentKeys.contains(key) && parts.length == 2) {
                db.delete(
                        TABLE_SIMILAR_KANJI_CHOICE_STATE,
                        WHERE_SIMILAR_CHOICE,
                        new String[]{parts[0], parts[1]}
                );
                db.delete(
                        TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                        "status=? AND target_kanji=? AND choice_signature=?",
                        new String[]{STATUS_PENDING, parts[0], parts[1]}
                );
            }
        }
    }

    private Map<String, Long> similarPairFirstSeen(SQLiteDatabase db) {
        Map<String, Long> out = new HashMap<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, new String[]{COLUMN_KANJI_A, COLUMN_KANJI_B, COLUMN_SOURCE, COLUMN_FIRST_SEEN_AT}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                out.put(
                        similarKey(string(cursor, COLUMN_KANJI_A), string(cursor, COLUMN_KANJI_B), string(cursor, COLUMN_SOURCE)),
                        longValue(cursor, COLUMN_FIRST_SEEN_AT)
                );
            }
        }
        return out;
    }

    private Set<String> localInventoryKanji(SQLiteDatabase db) {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = db.query(TABLE_KANJI_INVENTORY, new String[]{COLUMN_KANJI}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String kanji = normalizeSingleKanji(string(cursor, COLUMN_KANJI));
                if (!kanji.isEmpty()) {
                    out.add(kanji);
                }
            }
        }
        return out;
    }

    private Records.SimilarKanjiPair readSimilarPair(Cursor cursor) {
        return new Records.SimilarKanjiPair(
                string(cursor, COLUMN_KANJI_A),
                string(cursor, COLUMN_KANJI_B),
                string(cursor, COLUMN_SOURCE),
                longValue(cursor, COLUMN_FIRST_SEEN_AT),
                longValue(cursor, COLUMN_LAST_SEEN_AT)
        );
    }

    private List<Records.SimilarKanjiPair> allSimilarPairs(SQLiteDatabase db) {
        List<Records.SimilarKanjiPair> out = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, null, null, null, null, null, ORDER_SIMILAR_PAIR)) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        }
        return out;
    }

    private List<Records.KanjiInventoryItem> allInventoryItems(SQLiteDatabase db) {
        List<Records.KanjiInventoryItem> out = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_KANJI_INVENTORY, null, null, null, null, null, ORDER_KANJI_ASC)) {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
        }
        return out;
    }

    private Map<String, SimilarChoiceSnapshot> similarChoiceSnapshots(SQLiteDatabase db) {
        Map<String, SimilarChoiceSnapshot> out = new HashMap<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_CHOICE_STATE, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String target = string(cursor, COLUMN_TARGET_KANJI);
                String signature = string(cursor, COLUMN_CHOICE_SIGNATURE);
                out.put(
                        similarChoiceKey(target, signature),
                        new SimilarChoiceSnapshot(
                                longValue(cursor, COLUMN_DUE_AT),
                                longValue(cursor, COLUMN_PASSED_AT),
                                longValue(cursor, COLUMN_LAST_REVIEWED_AT),
                                integer(cursor, COLUMN_CORRECT_COUNT),
                                integer(cursor, COLUMN_WRONG_COUNT),
                                longValue(cursor, COLUMN_FIRST_SEEN_AT)
                        )
                );
            }
        }
        return out;
    }

    private Records.SimilarKanjiChoiceCard similarChoiceCard(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                WHERE_SIMILAR_CHOICE,
                new String[]{targetKanji, choiceSignature},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? readSimilarChoiceCard(cursor) : null;
        }
    }

    private Records.SimilarKanjiChoiceCard readSimilarChoiceCard(Cursor cursor) {
        return new Records.SimilarKanjiChoiceCard(
                string(cursor, COLUMN_TARGET_KANJI),
                string(cursor, COLUMN_PRIMARY_MEANING),
                deserializeChoices(string(cursor, COLUMN_CHOICES)),
                string(cursor, COLUMN_CHOICE_SIGNATURE),
                longValue(cursor, COLUMN_DUE_AT),
                longValue(cursor, COLUMN_PASSED_AT),
                longValue(cursor, COLUMN_LAST_REVIEWED_AT),
                integer(cursor, COLUMN_CORRECT_COUNT),
                integer(cursor, COLUMN_WRONG_COUNT)
        );
    }

    private boolean hasPendingSimilarRepairs(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=?",
                new String[]{STATUS_PENDING, targetKanji, choiceSignature},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
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
        try (Cursor pending = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=? AND repair_kanji=?",
                new String[]{STATUS_PENDING, card.targetKanji, card.choiceSignature, normalized},
                null,
                null,
                null,
                "1"
        )) {
            if (pending.moveToFirst()) {
                return;
            }
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_TARGET_KANJI, card.targetKanji);
        values.put("repair_kanji", normalized);
        values.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
        values.put("wrong_selection", wrongSelection == null ? "" : wrongSelection);
        values.put("prompt_meaning", card.primaryMeaning);
        values.put(COLUMN_STATUS, STATUS_PENDING);
        values.put(COLUMN_DUE_AT, nowMillis);
        values.put(COLUMN_ACTIVE_TOKEN, "");
        values.put(COLUMN_ATTEMPTS, 0);
        values.put(COLUMN_CREATED_AT, nowMillis);
        values.put(COLUMN_UPDATED_AT, nowMillis);
        values.put(COLUMN_COMPLETED_AT, 0L);
        db.insert(TABLE_SIMILAR_KANJI_REPAIR_QUEUE, null, values);
    }

    private Records.SimilarKanjiWritingRepair similarWritingRepair(SQLiteDatabase db, long repairId) {
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                null,
                "id=?",
                new String[]{Long.toString(repairId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? readSimilarWritingRepair(cursor) : null;
        }
    }

    private Records.SimilarKanjiWritingRepair readSimilarWritingRepair(Cursor cursor) {
        return new Records.SimilarKanjiWritingRepair(
                longValue(cursor, "id"),
                string(cursor, COLUMN_TARGET_KANJI),
                string(cursor, "repair_kanji"),
                string(cursor, COLUMN_CHOICE_SIGNATURE),
                string(cursor, "wrong_selection"),
                string(cursor, "prompt_meaning"),
                string(cursor, COLUMN_STATUS),
                longValue(cursor, COLUMN_DUE_AT),
                string(cursor, COLUMN_ACTIVE_TOKEN),
                integer(cursor, COLUMN_ATTEMPTS),
                longValue(cursor, COLUMN_CREATED_AT),
                longValue(cursor, COLUMN_UPDATED_AT),
                longValue(cursor, COLUMN_COMPLETED_AT)
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
        Cursor cursor = db.query(TABLE_KANJI_INVENTORY, null, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? readInventoryItem(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, COLUMN_KANJI);
        return new Records.KanjiInventoryItem(
                kanji,
                string(cursor, COLUMN_PRIMARY_MEANING),
                string(cursor, "readings"),
                string(cursor, COLUMN_BROWSER_SEARCH),
                integer(cursor, "source_count"),
                integer(cursor, "example_count"),
                isKanjiSuspended(db, kanji),
                longValue(cursor, COLUMN_LAST_SEEN_AT)
        );
    }

    private boolean isKanjiSuspended(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? "" : second;
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
        return first + SIMILAR_KEY_DELIMITER + second + SIMILAR_KEY_DELIMITER + source;
    }

    private static String similarChoiceKey(String targetKanji, String choiceSignature) {
        return targetKanji + SIMILAR_CHOICE_KEY_DELIMITER + (choiceSignature == null ? "" : choiceSignature);
    }

    private Records.DashboardRow readDashboardRow(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
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
        String kanji = string(cursor, COLUMN_KANJI);
        return new Records.DashboardRow(
                kanji,
                nullableInt(cursor, COLUMN_JITEN_RANK),
                string(cursor, COLUMN_PRIMARY_MEANING),
                string(cursor, COLUMN_READING),
                string(cursor, COLUMN_BROWSER_SEARCH),
                integer(cursor, COLUMN_WEAKNESS_SCORE),
                string(cursor, COLUMN_REASON_CODE),
                string(cursor, COLUMN_REASON_TEXT),
                integer(cursor, COLUMN_ACTIVE_EXAMPLE_COUNT),
                integer(cursor, COLUMN_SUSPENDED_EXAMPLE_COUNT),
                integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                examplesForKanji(db, kanji)
        );
    }

    private Records.StudyItem studyItemForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_STUDY_ITEMS, null, WHERE_KANJI, new String[]{kanji}, null, null, "state='retired' ASC, due_at ASC", "1");
        try {
            return cursor.moveToFirst() ? readStudyItem(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.KanjiTimelineEvent readTimelineEvent(Cursor cursor) {
        return new Records.KanjiTimelineEvent(
                longValue(cursor, "id"),
                string(cursor, COLUMN_KANJI),
                longValue(cursor, COLUMN_OCCURRED_AT),
                string(cursor, COLUMN_EVENT_TYPE),
                string(cursor, COLUMN_TITLE),
                string(cursor, COLUMN_DETAIL),
                string(cursor, "source_expression"),
                string(cursor, "source_reading"),
                string(cursor, COLUMN_RATING),
                integer(cursor, COLUMN_WRITING_REQUIRED) == 1,
                integer(cursor, COLUMN_WRITING_PASSED) == 1,
                integer(cursor, COLUMN_MANUAL_OVERRIDE) == 1,
                nullableInt(cursor, COLUMN_WEAKNESS_SCORE),
                nullableInt(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                nullableLong(cursor, COLUMN_SYNC_ID),
                string(cursor, COLUMN_DEDUPE_KEY)
        );
    }

    private void insertTimelineEvent(
            SQLiteDatabase db,
            String kanji,
            long occurredAt,
            String eventType,
            String title,
            String detail,
            Object... eventValues
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, kanji);
        values.put(COLUMN_OCCURRED_AT, occurredAt);
        values.put(COLUMN_EVENT_TYPE, eventType == null ? "" : eventType);
        values.put(COLUMN_TITLE, title == null ? "" : title);
        values.put(COLUMN_DETAIL, detail == null ? "" : detail);
        String sourceExpression = stringValueAt(eventValues, 0);
        String sourceReading = stringValueAt(eventValues, 1);
        String rating = stringValueAt(eventValues, 2);
        boolean writingRequired = booleanValueAt(eventValues, 3);
        boolean writingPassed = booleanValueAt(eventValues, 4);
        boolean manualOverride = booleanValueAt(eventValues, 5);
        Integer weaknessScore = integerValueAt(eventValues, 6);
        Integer matureSupportCount = integerValueAt(eventValues, 7);
        Long syncId = longValueAt(eventValues, 8);
        String dedupeKey = stringValueAt(eventValues, 9);
        values.put("source_expression", sourceExpression == null ? "" : sourceExpression);
        values.put("source_reading", sourceReading == null ? "" : sourceReading);
        values.put(COLUMN_RATING, rating == null ? "" : rating);
        values.put(COLUMN_WRITING_REQUIRED, writingRequired ? 1 : 0);
        values.put(COLUMN_WRITING_PASSED, writingPassed ? 1 : 0);
        values.put(COLUMN_MANUAL_OVERRIDE, manualOverride ? 1 : 0);
        if (weaknessScore == null) {
            values.putNull(COLUMN_WEAKNESS_SCORE);
        } else {
            values.put(COLUMN_WEAKNESS_SCORE, weaknessScore);
        }
        if (matureSupportCount == null) {
            values.putNull(COLUMN_MATURE_SUPPORT_COUNT);
        } else {
            values.put(COLUMN_MATURE_SUPPORT_COUNT, matureSupportCount);
        }
        if (syncId == null) {
            values.putNull(COLUMN_SYNC_ID);
        } else {
            values.put(COLUMN_SYNC_ID, syncId);
        }
        values.put(COLUMN_DEDUPE_KEY, dedupeKey);
        db.insertWithOnConflict(TABLE_KANJI_TIMELINE_EVENTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static String stringValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof String value ? value : "";
    }

    private static boolean booleanValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof Boolean value && value;
    }

    private static Integer integerValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof Integer value ? value : null;
    }

    private static Long longValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof Long value ? value : null;
    }

    private Map<String, RowSnapshot> rowSnapshots(SQLiteDatabase db) {
        Map<String, RowSnapshot> rows = new LinkedHashMap<>();
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, ORDER_KANJI_ASC);
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
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? rowSnapshotFromCursor(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private RowSnapshot rowSnapshotFromCursor(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, COLUMN_KANJI);
        return new RowSnapshot(
                kanji,
                integer(cursor, COLUMN_WEAKNESS_SCORE),
                integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                longValue(cursor, "rebuilt_at"),
                firstExampleForKanji(db, kanji)
        );
    }

    private Map<String, StudySnapshot> studySnapshots(SQLiteDatabase db) {
        Map<String, StudySnapshot> items = new HashMap<>();
        Cursor cursor = db.query(TABLE_STUDY_ITEMS, new String[]{COLUMN_KANJI, COLUMN_ANSWER_SIGNATURE, COLUMN_STATE}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                String answerSignature = string(cursor, COLUMN_ANSWER_SIGNATURE);
                items.put(studyFamilyKey(kanji, answerSignature), new StudySnapshot(string(cursor, COLUMN_STATE)));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private SourceSnapshot firstExampleForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_KANJI_EXAMPLES, new String[]{COLUMN_EXPRESSION, COLUMN_READING}, WHERE_KANJI, new String[]{kanji}, null, null, "source_type ASC, id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, COLUMN_EXPRESSION), string(cursor, COLUMN_READING));
        } finally {
            cursor.close();
        }
    }

    private SourceSnapshot firstSuspendedSourceForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_SUSPENDED_SOURCES, new String[]{COLUMN_EXPRESSION, COLUMN_READING}, WHERE_KANJI, new String[]{kanji}, null, null, "card_id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, COLUMN_EXPRESSION), string(cursor, COLUMN_READING));
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
        values.put(COLUMN_VALUE, value);
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private long insertSyncRun(SQLiteDatabase db, SyncRunInsert syncRun) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STARTED_AT, syncRun.startedAt());
        values.put(COLUMN_FINISHED_AT, syncRun.finishedAt());
        values.put(COLUMN_STATUS, syncRun.status());
        values.put(COLUMN_ACTIVE_NOTES_COUNT, syncRun.activeIndex().noteIds.size());
        values.put(COLUMN_ACTIVE_CARDS_COUNT, syncRun.activeIndex().activeCardCount);
        values.put(COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, syncRun.activeIndex().suspendedCardCount);
        values.put(COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, syncRun.importCount());
        values.put("deleted_notes_count", syncRun.deletedNotes());
        values.put("deleted_cards_count", syncRun.deletedCards());
        values.put("error_code", syncRun.errorCode());
        values.put(COLUMN_ERROR_MESSAGE, syncRun.errorMessage());
        values.put(COLUMN_REMOVAL_MESSAGE, syncRun.removalMessage());
        return db.insert(TABLE_SYNC_RUNS, null, values);
    }

    private void saveRows(SQLiteDatabase db, List<Records.DashboardRow> rows, long rebuiltAt) {
        for (Records.DashboardRow row : rows) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI, row.kanji);
            if (row.jitenRank != null) {
                values.put(COLUMN_JITEN_RANK, row.jitenRank);
            }
            values.put(COLUMN_PRIMARY_MEANING, row.primaryMeaning);
            values.put(COLUMN_READING, row.reading);
            values.put(COLUMN_BROWSER_SEARCH, row.browserSearch);
            values.put(COLUMN_WEAKNESS_SCORE, row.weaknessScore);
            values.put(COLUMN_REASON_CODE, row.reasonCode);
            values.put(COLUMN_REASON_TEXT, row.reasonText);
            values.put(COLUMN_ACTIVE_EXAMPLE_COUNT, row.activeExampleCount);
            values.put(COLUMN_SUSPENDED_EXAMPLE_COUNT, row.suspendedExampleCount);
            values.put(COLUMN_MATURE_SUPPORT_COUNT, row.matureSupportCount);
            values.put("rebuilt_at", rebuiltAt);
            db.insertWithOnConflict(TABLE_DASHBOARD_ROWS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            for (Records.Example example : row.examples) {
                ContentValues ex = new ContentValues();
                ex.put(COLUMN_KANJI, row.kanji);
                ex.put("source_type", example.sourceType);
                ex.put(COLUMN_CARD_ID, example.cardId);
                ex.put(COLUMN_NOTE_ID, example.noteId);
                ex.put(COLUMN_EXPRESSION, example.expression);
                ex.put(COLUMN_READING, example.reading);
                ex.put(COLUMN_MEANING, example.meaning);
                ex.put(COLUMN_SENTENCE, example.sentence);
                ex.put(COLUMN_MATURE, example.mature ? 1 : 0);
                ex.put(COLUMN_LAPSES, example.lapses);
                ex.put(COLUMN_INTERVAL_DAYS, example.intervalDays);
                ex.put(COLUMN_REPS, example.reps);
                putNullableDouble(ex, COLUMN_FSRS_STABILITY, example.fsrsStability);
                putNullableDouble(ex, COLUMN_FSRS_DIFFICULTY, example.fsrsDifficulty);
                putNullableDouble(ex, COLUMN_FSRS_RETRIEVABILITY, example.fsrsRetrievability);
                db.insert(TABLE_KANJI_EXAMPLES, null, ex);
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
            cardValues.put(COLUMN_SYNC_ID, syncId);
            cardValues.put(COLUMN_STARTED_AT, timing.startedAt);
            cardValues.put(COLUMN_FINISHED_AT, timing.finishedAt);
            cardValues.put(COLUMN_CARD_ID, card.cardId);
            cardValues.put(COLUMN_NOTE_ID, card.noteId);
            cardValues.put(COLUMN_DECK_ID, card.deckId);
            cardValues.put(COLUMN_DECK_NAME, card.deckName);
            cardValues.put(COLUMN_MODEL_ID, note.modelId);
            cardValues.put(COLUMN_MODEL_NAME, note.modelName);
            cardValues.put("ord", card.ord);
            cardValues.put(COLUMN_QUEUE, card.queue);
            cardValues.put("type", card.type);
            cardValues.put("due", card.due);
            cardValues.put(COLUMN_INTERVAL_DAYS, card.intervalDays);
            cardValues.put(COLUMN_REPS, card.reps);
            cardValues.put(COLUMN_LAPSES, card.lapses);
            cardValues.put("suspended", card.suspended ? 1 : 0);
            putNullableDouble(cardValues, COLUMN_FSRS_STABILITY, card.fsrsStability);
            putNullableDouble(cardValues, COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty);
            putNullableDouble(cardValues, COLUMN_FSRS_RETRIEVABILITY, card.fsrsRetrievability);
            cardValues.put(COLUMN_MATURE, card.mature(settings.matureDays) ? 1 : 0);
            db.insertWithOnConflict(TABLE_SYNC_CARD_SNAPSHOTS, null, cardValues, SQLiteDatabase.CONFLICT_REPLACE);

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
            noteValues.put(COLUMN_SYNC_ID, syncId);
            noteValues.put(COLUMN_FINISHED_AT, timing.finishedAt);
            noteValues.put(COLUMN_NOTE_ID, note.noteId);
            noteValues.put(COLUMN_MODEL_ID, note.modelId);
            noteValues.put(COLUMN_MODEL_NAME, note.modelName);
            noteValues.put(COLUMN_DECK_IDS, deckIds == null ? "" : String.join(" ", deckIds));
            noteValues.put(COLUMN_DECK_NAMES, String.join(" ", decks));
            noteValues.put(COLUMN_EXPRESSION, expression);
            noteValues.put(COLUMN_READING, reading);
            noteValues.put(COLUMN_MEANING, meaning);
            noteValues.put(COLUMN_SENTENCE, sentence);
            noteValues.put(COLUMN_TAGS, String.join(" ", note.tags));
            noteValues.put(COLUMN_FIELDS_JSON, fieldsJson(note.fields));
            noteValues.put("extracted_kanji", String.join("", TextUtil.extractKanji(expression + " " + sentence)));
            db.insertWithOnConflict(TABLE_SYNC_NOTE_SNAPSHOTS, null, noteValues, SQLiteDatabase.CONFLICT_REPLACE);
        }

        overlayDashboardRows(aggregates, rows);
        insertHistoricalKanjiAggregates(db, syncId, timing.finishedAt, aggregates);
    }

    private void backfillLatestHistoricalSync(SQLiteDatabase db) {
        if (tableHasRows(db, TABLE_SYNC_KANJI_SNAPSHOTS)) {
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
        backfillHistoricalCards(db, sync, settings, notes, deckIdsByNote, deckNamesByNote, aggregates);
        backfillHistoricalNotes(db, sync, notes, deckIdsByNote, deckNamesByNote);
        overlayDashboardRows(aggregates, currentDashboardRows(db));
        insertHistoricalKanjiAggregates(db, sync.id, sync.finishedAt, aggregates);
    }

    private void backfillHistoricalCards(
            SQLiteDatabase db,
            HistoricalSyncRun sync,
            Records.Settings settings,
            Map<Long, HistoricalNoteSnapshot> notes,
            Map<Long, LinkedHashSet<String>> deckIdsByNote,
            Map<Long, LinkedHashSet<String>> deckNamesByNote,
            Map<String, HistoricalKanjiAggregate> aggregates
    ) {
        Cursor cards = db.query(TABLE_SOURCE_CARDS, null, null, null, null, null, "card_id ASC");
        try {
            while (cards.moveToNext()) {
                HistoricalNoteSnapshot note = notes.get(longValue(cards, COLUMN_NOTE_ID));
                if (note == null) {
                    continue;
                }
                backfillHistoricalCard(db, cards, sync, settings, note, deckIdsByNote, deckNamesByNote, aggregates);
            }
        } finally {
            cards.close();
        }
    }

    private void backfillHistoricalCard(
            SQLiteDatabase db,
            Cursor cards,
            HistoricalSyncRun sync,
            Records.Settings settings,
            HistoricalNoteSnapshot note,
            Map<Long, LinkedHashSet<String>> deckIdsByNote,
            Map<Long, LinkedHashSet<String>> deckNamesByNote,
            Map<String, HistoricalKanjiAggregate> aggregates
    ) {
        String deck = string(cards, COLUMN_DECK_NAME);
        linkedSetFor(deckIdsByNote, note.noteId).add(deck);
        linkedSetFor(deckNamesByNote, note.noteId).add(deck);
        int intervalDays = integer(cards, COLUMN_INTERVAL_DAYS);
        int reps = integer(cards, COLUMN_REPS);
        int lapses = integer(cards, COLUMN_LAPSES);
        boolean mature = intervalDays >= settings.matureDays;
        db.insertWithOnConflict(
                TABLE_SYNC_CARD_SNAPSHOTS,
                null,
                historicalCardValues(cards, sync, note, deck, new HistoricalCardMetrics(intervalDays, reps, lapses, mature)),
                SQLiteDatabase.CONFLICT_REPLACE
        );
        for (String kanji : TextUtil.extractKanji(note.expression + " " + note.sentence)) {
            aggregateFor(aggregates, kanji).add(new CardMetrics(
                    intervalDays,
                    reps,
                    lapses,
                    false,
                    mature,
                    nullableDouble(cards, COLUMN_FSRS_STABILITY),
                    nullableDouble(cards, COLUMN_FSRS_DIFFICULTY),
                    nullableDouble(cards, COLUMN_FSRS_RETRIEVABILITY)
            ));
        }
    }

    private ContentValues historicalCardValues(
            Cursor cards,
            HistoricalSyncRun sync,
            HistoricalNoteSnapshot note,
            String deck,
            HistoricalCardMetrics metrics
    ) {
        ContentValues cardValues = new ContentValues();
        cardValues.put(COLUMN_SYNC_ID, sync.id);
        cardValues.put(COLUMN_STARTED_AT, sync.startedAt);
        cardValues.put(COLUMN_FINISHED_AT, sync.finishedAt);
        cardValues.put(COLUMN_CARD_ID, longValue(cards, COLUMN_CARD_ID));
        cardValues.put(COLUMN_NOTE_ID, note.noteId);
        cardValues.put(COLUMN_DECK_ID, deck);
        cardValues.put(COLUMN_DECK_NAME, deck);
        cardValues.put(COLUMN_MODEL_ID, note.modelId);
        cardValues.put(COLUMN_MODEL_NAME, note.modelName);
        cardValues.put("ord", integer(cards, "ord"));
        cardValues.put(COLUMN_QUEUE, integer(cards, COLUMN_QUEUE));
        cardValues.put("type", integer(cards, "type"));
        cardValues.put("due", integer(cards, "due"));
        cardValues.put(COLUMN_INTERVAL_DAYS, metrics.intervalDays);
        cardValues.put(COLUMN_REPS, metrics.reps);
        cardValues.put(COLUMN_LAPSES, metrics.lapses);
        cardValues.put("suspended", 0);
        putNullableDouble(cardValues, COLUMN_FSRS_STABILITY, nullableDouble(cards, COLUMN_FSRS_STABILITY));
        putNullableDouble(cardValues, COLUMN_FSRS_DIFFICULTY, nullableDouble(cards, COLUMN_FSRS_DIFFICULTY));
        putNullableDouble(cardValues, COLUMN_FSRS_RETRIEVABILITY, nullableDouble(cards, COLUMN_FSRS_RETRIEVABILITY));
        cardValues.put(COLUMN_MATURE, metrics.mature ? 1 : 0);
        return cardValues;
    }

    private void backfillHistoricalNotes(
            SQLiteDatabase db,
            HistoricalSyncRun sync,
            Map<Long, HistoricalNoteSnapshot> notes,
            Map<Long, LinkedHashSet<String>> deckIdsByNote,
            Map<Long, LinkedHashSet<String>> deckNamesByNote
    ) {
        for (HistoricalNoteSnapshot note : notes.values()) {
            LinkedHashSet<String> deckIds = deckIdsByNote.get(note.noteId);
            LinkedHashSet<String> decks = deckNamesByNote.get(note.noteId);
            if (decks == null || decks.isEmpty()) {
                continue;
            }
            ContentValues noteValues = new ContentValues();
            noteValues.put(COLUMN_SYNC_ID, sync.id);
            noteValues.put(COLUMN_FINISHED_AT, sync.finishedAt);
            noteValues.put(COLUMN_NOTE_ID, note.noteId);
            noteValues.put(COLUMN_MODEL_ID, note.modelId);
            noteValues.put(COLUMN_MODEL_NAME, note.modelName);
            noteValues.put(COLUMN_DECK_IDS, deckIds == null ? "" : String.join(" ", deckIds));
            noteValues.put(COLUMN_DECK_NAMES, String.join(" ", decks));
            noteValues.put(COLUMN_EXPRESSION, note.expression);
            noteValues.put(COLUMN_READING, note.reading);
            noteValues.put(COLUMN_MEANING, note.meaning);
            noteValues.put(COLUMN_SENTENCE, note.sentence);
            noteValues.put(COLUMN_TAGS, note.tags);
            noteValues.put(COLUMN_FIELDS_JSON, note.fieldsJson);
            noteValues.put("extracted_kanji", String.join("", TextUtil.extractKanji(note.expression + " " + note.sentence)));
            db.insertWithOnConflict(TABLE_SYNC_NOTE_SNAPSHOTS, null, noteValues, SQLiteDatabase.CONFLICT_REPLACE);
        }
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
            values.put(COLUMN_SYNC_ID, syncId);
            values.put(COLUMN_FINISHED_AT, finishedAt);
            values.put(COLUMN_KANJI, aggregate.kanji);
            values.put("active_cards", aggregate.activeCards);
            values.put("suspended_cards", aggregate.suspendedCards);
            values.put(COLUMN_MATURE_SUPPORT_COUNT, aggregate.matureSupportCount);
            values.put("average_interval_days", aggregate.averageIntervalDays());
            values.put("total_lapses", aggregate.totalLapses);
            values.put("total_reps", aggregate.totalReps);
            putNullableDouble(values, "fsrs_stability_avg", aggregate.averageStability());
            putNullableDouble(values, "fsrs_difficulty_avg", aggregate.averageDifficulty());
            putNullableDouble(values, "fsrs_retrievability_avg", aggregate.averageRetrievability());
            values.put(COLUMN_WEAKNESS_SCORE, aggregate.weaknessScore);
            values.put(COLUMN_REASON_CODE, aggregate.reasonCode);
            values.put(COLUMN_ACTIVE_EXAMPLE_COUNT, aggregate.activeExampleCount);
            values.put(COLUMN_SUSPENDED_EXAMPLE_COUNT, aggregate.suspendedExampleCount);
            db.insertWithOnConflict(TABLE_SYNC_KANJI_SNAPSHOTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
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
                TABLE_SYNC_RUNS,
                new String[]{"id", COLUMN_STARTED_AT, COLUMN_FINISHED_AT},
                "status=?",
                new String[]{STATUS_SUCCESS},
                null,
                null,
                ORDER_ID_DESC,
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalSyncRun(
                    longValue(cursor, "id"),
                    longValue(cursor, COLUMN_STARTED_AT),
                    longValue(cursor, COLUMN_FINISHED_AT)
            );
        } finally {
            cursor.close();
        }
    }

    private Map<Long, HistoricalNoteSnapshot> currentSourceNotes(SQLiteDatabase db) {
        Map<Long, HistoricalNoteSnapshot> notes = new LinkedHashMap<>();
        Cursor cursor = db.query(TABLE_SOURCE_NOTES, null, null, null, null, null, "note_id ASC");
        try {
            while (cursor.moveToNext()) {
                long noteId = longValue(cursor, COLUMN_NOTE_ID);
                notes.put(noteId, new HistoricalNoteSnapshot(new HistoricalNoteFields(
                        noteId,
                        0L,
                        string(cursor, COLUMN_MODEL_NAME),
                        string(cursor, COLUMN_EXPRESSION),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_MEANING),
                        string(cursor, COLUMN_SENTENCE),
                        string(cursor, COLUMN_TAGS),
                        string(cursor, COLUMN_FIELDS_JSON)
                )));
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    private List<Records.DashboardRow> currentDashboardRows(SQLiteDatabase db) {
        List<Records.DashboardRow> rows = new ArrayList<>();
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, new String[]{COLUMN_KANJI}, null, null, null, null, ORDER_KANJI_ASC);
        try {
            while (cursor.moveToNext()) {
                Records.DashboardRow row = readDashboardRow(db, string(cursor, COLUMN_KANJI));
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
                counts.put(string(cursor, COLUMN_KANJI), integer(cursor, "review_count"));
            }
        } finally {
            cursor.close();
        }
        return counts;
    }

    private Set<String> impactCandidateKanji(SQLiteDatabase db, long latestSyncId) {
        Set<String> candidates = new HashSet<>();
        Cursor current = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                new String[]{COLUMN_KANJI},
                "sync_id=? AND (weakness_score>0 OR reason_code<>'' OR active_example_count>0 OR suspended_example_count>0)",
                new String[]{Long.toString(latestSyncId)},
                null,
                null,
                null
        );
        try {
            while (current.moveToNext()) {
                candidates.add(string(current, COLUMN_KANJI));
            }
        } finally {
            current.close();
        }
        Cursor study = db.query(true, TABLE_STUDY_ITEMS, new String[]{COLUMN_KANJI}, null, null, null, null, null, null);
        try {
            while (study.moveToNext()) {
                candidates.add(string(study, COLUMN_KANJI));
            }
        } finally {
            study.close();
        }
        Cursor imports = db.query(true, TABLE_SUSPENDED_IMPORTS, new String[]{COLUMN_KANJI}, null, null, null, null, null, null);
        try {
            while (imports.moveToNext()) {
                candidates.add(string(imports, COLUMN_KANJI));
            }
        } finally {
            imports.close();
        }
        return candidates;
    }

    private Map<String, KanjiImpactAnalyzer.MetricSnapshot> kanjiMetricsForSync(SQLiteDatabase db, long syncId) {
        Map<String, KanjiImpactAnalyzer.MetricSnapshot> out = new LinkedHashMap<>();
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                "sync_id=?",
                new String[]{Long.toString(syncId)},
                null,
                null,
                ORDER_KANJI_ASC
        );
        try {
            while (cursor.moveToNext()) {
                out.put(string(cursor, COLUMN_KANJI), readKanjiImpactMetric(cursor));
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
                TABLE_SYNC_KANJI_SNAPSHOTS,
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
            return new HistoricalKanjiSnapshot(longValue(cursor, COLUMN_SYNC_ID), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private HistoricalKanjiSnapshot latestKanjiSnapshotAtOrBefore(SQLiteDatabase db, String kanji, long startedAt) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
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
            return new HistoricalKanjiSnapshot(longValue(cursor, COLUMN_SYNC_ID), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private HistoricalKanjiSnapshot firstKanjiSnapshot(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                WHERE_KANJI,
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
            return new HistoricalKanjiSnapshot(longValue(cursor, COLUMN_SYNC_ID), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private KanjiImpactAnalyzer.MetricSnapshot readKanjiImpactMetric(Cursor cursor) {
        return new KanjiImpactAnalyzer.MetricSnapshot(
                integer(cursor, "active_cards"),
                integer(cursor, "suspended_cards"),
                integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
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
                baseline.add(new CardMetrics(
                        integer(cursor, "b_interval_days"),
                        integer(cursor, "b_reps"),
                        integer(cursor, "b_lapses"),
                        integer(cursor, "b_suspended") == 1,
                        integer(cursor, "b_mature") == 1,
                        nullableDouble(cursor, "b_fsrs_stability"),
                        nullableDouble(cursor, "b_fsrs_difficulty"),
                        nullableDouble(cursor, "b_fsrs_retrievability")
                ));
                current.add(new CardMetrics(
                        integer(cursor, "c_interval_days"),
                        integer(cursor, "c_reps"),
                        integer(cursor, "c_lapses"),
                        integer(cursor, "c_suspended") == 1,
                        integer(cursor, "c_mature") == 1,
                        nullableDouble(cursor, "c_fsrs_stability"),
                        nullableDouble(cursor, "c_fsrs_difficulty"),
                        nullableDouble(cursor, "c_fsrs_retrievability")
                ));
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
        Cursor cursor = db.query(TABLE_KANJI_EXAMPLES, null, WHERE_KANJI, new String[]{kanji}, null, null, "source_type DESC, id ASC", "8");
        try {
            while (cursor.moveToNext()) {
                examples.add(new Records.Example(
                        string(cursor, "source_type"),
                        longValue(cursor, COLUMN_CARD_ID),
                        longValue(cursor, COLUMN_NOTE_ID),
                        string(cursor, COLUMN_EXPRESSION),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_MEANING),
                        string(cursor, COLUMN_SENTENCE),
                        integer(cursor, COLUMN_MATURE) == 1,
                        integer(cursor, COLUMN_LAPSES),
                        integer(cursor, COLUMN_INTERVAL_DAYS),
                        integer(cursor, COLUMN_REPS),
                        nullableDouble(cursor, COLUMN_FSRS_STABILITY),
                        nullableDouble(cursor, COLUMN_FSRS_DIFFICULTY),
                        nullableDouble(cursor, COLUMN_FSRS_RETRIEVABILITY)
                ));
            }
        } finally {
            cursor.close();
        }
        return examples;
    }

    private void upsertStudyItem(SQLiteDatabase db, Records.StudyItem item) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, item.kanji);
        values.put(COLUMN_STATE, item.state);
        values.put(COLUMN_DUE_AT, item.dueAtMillis);
        values.put("stability", item.stability);
        values.put("difficulty", item.difficulty);
        values.put("total_reviews", item.totalReviews);
        values.put(COLUMN_LAPSES, item.lapses);
        values.put("learning_step", item.learningStep);
        values.put("writing_level", item.writingLevel);
        values.put(COLUMN_RECOGNITION_STAGE, item.recognitionStage);
        values.put(COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS, item.consecutiveFailedRecognitionDays);
        values.put(COLUMN_LAST_FAILED_RECOGNITION_DAY, item.lastFailedRecognitionDayMillis);
        values.put(COLUMN_WRITING_REMEDIATION_PENDING, item.writingRemediationPending ? 1 : 0);
        values.put(COLUMN_SUPPRESSED_BY_TASK_TYPE, item.suppressedByTaskType);
        values.put(COLUMN_SUPPRESSED_AT, item.suppressedAtMillis);
        values.put(COLUMN_MATURE_INTERVAL_DAYS, item.matureIntervalDays);
        values.put(COLUMN_ANSWER_SIGNATURE, item.answerSignature);
        values.put(COLUMN_TYPING_MEANING_MEMORY, item.typingMeaningMemory.encode());
        values.put(COLUMN_KANJI_MEANING_MEMORY, item.kanjiMeaningMemory.encode());
        values.put(COLUMN_FONT_MEANING_MEMORY, item.fontMeaningMemory.encode());
        values.put(COLUMN_WORD_READING_MEMORY, item.wordReadingMemory.encode());
        values.put(COLUMN_WRITING_REMEDIATION_MEMORY, item.writingRemediationMemory.encode());
        values.put(COLUMN_ACTIVE_TOKEN, item.activeToken);
        values.put(COLUMN_CREATED_AT, item.createdAtMillis);
        db.insertWithOnConflict(TABLE_STUDY_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private Records.StudyItem readStudyItem(Cursor cursor) {
        String state = string(cursor, COLUMN_STATE);
        long dueAt = longValue(cursor, COLUMN_DUE_AT);
        double stability = cursor.getDouble(cursor.getColumnIndexOrThrow("stability"));
        double difficulty = cursor.getDouble(cursor.getColumnIndexOrThrow("difficulty"));
        int totalReviews = integer(cursor, "total_reviews");
        int lapses = integer(cursor, COLUMN_LAPSES);
        int learningStep = integer(cursor, "learning_step");
        int recognitionStage = integer(cursor, COLUMN_RECOGNITION_STAGE);
        boolean writingRemediationPending = integer(cursor, COLUMN_WRITING_REMEDIATION_PENDING) == 1;
        int matureIntervalDays = integer(cursor, COLUMN_MATURE_INTERVAL_DAYS);
        StudyMemoryFields memoryFields = new StudyMemoryFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        Records.TaskMemory typingFallback = taskMemoryFallback(-1, recognitionStage, memoryFields);
        Records.TaskMemory kanjiFallback = taskMemoryFallback(0, recognitionStage, memoryFields);
        Records.TaskMemory fontFallback = taskMemoryFallback(1, recognitionStage, memoryFields);
        Records.TaskMemory wordFallback = taskMemoryFallback(2, recognitionStage, memoryFields);
        Records.TaskMemory writingFallback = writingRemediationPending
                ? Records.TaskMemory.fromStudyFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays)
                : Records.TaskMemory.initial();
        return new Records.StudyItem(
                string(cursor, COLUMN_KANJI),
                state,
                dueAt,
                stability,
                difficulty,
                totalReviews,
                lapses,
                learningStep,
                integer(cursor, "writing_level"),
                recognitionStage,
                integer(cursor, COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS),
                longValue(cursor, COLUMN_LAST_FAILED_RECOGNITION_DAY),
                writingRemediationPending,
                string(cursor, COLUMN_SUPPRESSED_BY_TASK_TYPE),
                longValue(cursor, COLUMN_SUPPRESSED_AT),
                matureIntervalDays,
                string(cursor, COLUMN_ANSWER_SIGNATURE),
                string(cursor, COLUMN_ACTIVE_TOKEN),
                longValue(cursor, COLUMN_CREATED_AT),
                Records.TaskMemory.decode(string(cursor, COLUMN_TYPING_MEANING_MEMORY), typingFallback),
                Records.TaskMemory.decode(string(cursor, COLUMN_KANJI_MEANING_MEMORY), kanjiFallback),
                Records.TaskMemory.decode(string(cursor, COLUMN_FONT_MEANING_MEMORY), fontFallback),
                Records.TaskMemory.decode(string(cursor, COLUMN_WORD_READING_MEMORY), wordFallback),
                Records.TaskMemory.decode(string(cursor, COLUMN_WRITING_REMEDIATION_MEMORY), writingFallback)
        );
    }

    private Records.LearningRepeat readLearningRepeat(Cursor cursor) {
        return new Records.LearningRepeat(
                string(cursor, COLUMN_KANJI),
                string(cursor, COLUMN_ANSWER_SIGNATURE),
                string(cursor, COLUMN_TASK_TYPE),
                string(cursor, "repeat_type"),
                integer(cursor, "step_index"),
                longValue(cursor, COLUMN_DUE_AT),
                string(cursor, COLUMN_ACTIVE_TOKEN),
                longValue(cursor, COLUMN_CREATED_AT),
                longValue(cursor, COLUMN_UPDATED_AT)
        );
    }

    private Records.TaskMemory taskMemoryFallback(
            int memoryStage,
            int recognitionStage,
            StudyMemoryFields fields
    ) {
        if (Math.max(-1, Math.min(2, recognitionStage)) == memoryStage) {
            return Records.TaskMemory.fromStudyFields(
                    fields.state(),
                    fields.dueAtMillis(),
                    fields.stability(),
                    fields.difficulty(),
                    fields.totalReviews(),
                    fields.lapses(),
                    fields.learningStep(),
                    fields.matureIntervalDays()
            );
        }
        return Records.TaskMemory.initial();
    }

    private long firstImportedAt(SQLiteDatabase db, String kanji, long fallback) {
        Cursor cursor = db.query(TABLE_SUSPENDED_IMPORTS, new String[]{COLUMN_FIRST_IMPORTED_AT}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
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

    private record SyncRunInsert(
            long startedAt,
            long finishedAt,
            String status,
            ActiveCardIndex activeIndex,
            int importCount,
            String errorCode,
            String errorMessage,
            String removalMessage,
            int deletedNotes,
            int deletedCards
    ) {
    }

    private record StudyMemoryFields(
            String state,
            long dueAtMillis,
            double stability,
            double difficulty,
            int totalReviews,
            int lapses,
            int learningStep,
            int matureIntervalDays
    ) {
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

        private HistoricalNoteSnapshot(HistoricalNoteFields fields) {
            this.noteId = fields.noteId();
            this.modelId = fields.modelId();
            this.modelName = nullToEmpty(fields.modelName());
            this.expression = nullToEmpty(fields.expression());
            this.reading = nullToEmpty(fields.reading());
            this.meaning = nullToEmpty(fields.meaning());
            this.sentence = nullToEmpty(fields.sentence());
            this.tags = nullToEmpty(fields.tags());
            this.fieldsJson = nullToEmpty(fields.fieldsJson());
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private record HistoricalNoteFields(
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
    }

    private record CardMetrics(
            int intervalDays,
            int reps,
            int lapses,
            boolean suspended,
            boolean mature,
            Double fsrsStability,
            Double fsrsDifficulty,
            Double fsrsRetrievability
    ) {
    }

    private record HistoricalCardMetrics(int intervalDays, int reps, int lapses, boolean mature) {
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

        private void add(CardMetrics metrics) {
            aggregate.add(metrics);
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
            add(new CardMetrics(
                    card.intervalDays,
                    card.reps,
                    card.lapses,
                    card.suspended,
                    card.mature(matureDays),
                    card.fsrsStability,
                    card.fsrsDifficulty,
                    card.fsrsRetrievability
            ));
        }

        private void add(CardMetrics metrics) {
            if (metrics.suspended()) {
                suspendedCards++;
            } else {
                activeCards++;
            }
            if (metrics.mature()) {
                matureSupportCount++;
            }
            totalLapses += Math.max(0, metrics.lapses());
            totalReps += Math.max(0, metrics.reps());
            intervalSum += Math.max(0, metrics.intervalDays());
            intervalCount++;
            if (metrics.fsrsStability() != null) {
                stabilitySum += metrics.fsrsStability();
                stabilityCount++;
            }
            if (metrics.fsrsDifficulty() != null) {
                difficultySum += metrics.fsrsDifficulty();
                difficultyCount++;
            }
            if (metrics.fsrsRetrievability() != null) {
                retrievabilitySum += metrics.fsrsRetrievability();
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
        private final Set<String> readings = new LinkedHashSet<>();
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
            List<String> display = new ArrayList<>();
            int hidden = 0;
            for (String reading : readings) {
                if (display.size() < MAX_DISPLAYED_INVENTORY_READINGS) {
                    display.add(reading);
                } else {
                    hidden++;
                }
            }
            String text = String.join(" / ", display);
            return hidden == 0 ? text : text + " +" + hidden + " more";
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
        return kanji + SIMILAR_KEY_DELIMITER + (answerSignature == null ? "" : answerSignature);
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

        private SyncStatus(SyncStatusValues values) {
            this.status = values.status();
            this.activeNotes = values.activeNotes();
            this.activeCards = values.activeCards();
            this.suspendedCards = values.suspendedCards();
            this.importedKanji = values.importedKanji();
            this.finishedAt = values.finishedAt();
            this.errorMessage = values.errorMessage();
            this.removalMessage = values.removalMessage();
        }

        public String headline() {
            if (!STATUS_SUCCESS.equals(status)) {
                return "Sync blocked: " + errorMessage;
            }
            return String.format(Locale.ROOT, "%d active cards checked, %d suspended cards archived, %d rare kanji added", activeCards, suspendedCards, importedKanji);
        }
    }

    private record SyncStatusValues(
            String status,
            int activeNotes,
            int activeCards,
            int suspendedCards,
            int importedKanji,
            long finishedAt,
            String errorMessage,
            String removalMessage
    ) {
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

    private record StudyDays(List<Long> days, int reviewsToday, long lastStudyAt) {
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

    public static final class StudyTaskTimeStats {
        public final long todayMillis;
        public final long lastSevenDaysMillis;
        public final int answeredTasks;

        public StudyTaskTimeStats(long todayMillis, long lastSevenDaysMillis, int answeredTasks) {
            this.todayMillis = Math.max(0L, todayMillis);
            this.lastSevenDaysMillis = Math.max(0L, lastSevenDaysMillis);
            this.answeredTasks = Math.max(0, answeredTasks);
        }

        public long averageMillisPerTask() {
            if (answeredTasks == 0) {
                return 0L;
            }
            return lastSevenDaysMillis / answeredTasks;
        }
    }

    private static final class StudyTaskAggregate {
        private final long elapsedMillis;
        private final int taskCount;

        private StudyTaskAggregate(long elapsedMillis, int taskCount) {
            this.elapsedMillis = elapsedMillis;
            this.taskCount = taskCount;
        }
    }

    public static final class KaniOutcomeStats {
        public final WeakKanjiImprovedMetric weakKanjiImproved;
        public final MatureSupportGainedMetric matureSupportGained;

        public KaniOutcomeStats(WeakKanjiImprovedMetric weakKanjiImproved, MatureSupportGainedMetric matureSupportGained) {
            this.weakKanjiImproved = weakKanjiImproved == null ? WeakKanjiImprovedMetric.empty() : weakKanjiImproved;
            this.matureSupportGained = matureSupportGained == null ? MatureSupportGainedMetric.empty() : matureSupportGained;
        }

        public static KaniOutcomeStats empty() {
            return new KaniOutcomeStats(WeakKanjiImprovedMetric.empty(), MatureSupportGainedMetric.empty());
        }
    }

    public static final class WeakKanjiImprovedMetric {
        public final int improvedCount;
        public final double averageBeforeWeakness;
        public final double averageAfterWeakness;
        public final List<KanjiImprovement> examples;

        public WeakKanjiImprovedMetric(int improvedCount, double averageBeforeWeakness, double averageAfterWeakness, List<KanjiImprovement> examples) {
            this.improvedCount = Math.max(0, improvedCount);
            this.averageBeforeWeakness = Math.max(0.0, averageBeforeWeakness);
            this.averageAfterWeakness = Math.max(0.0, averageAfterWeakness);
            this.examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static WeakKanjiImprovedMetric empty() {
            return new WeakKanjiImprovedMetric(0, 0.0, 0.0, Collections.emptyList());
        }
    }

    public static final class KanjiImprovement {
        public final String kanji;
        public final double beforeWeakness;
        public final double afterWeakness;

        public KanjiImprovement(String kanji, double beforeWeakness, double afterWeakness) {
            this.kanji = kanji == null ? "" : kanji;
            this.beforeWeakness = Math.max(0.0, beforeWeakness);
            this.afterWeakness = Math.max(0.0, afterWeakness);
        }
    }

    public static final class MatureSupportGainedMetric {
        public final int gainedSupportCount;
        public final int firstSupportCount;
        public final List<KanjiSupportGain> examples;

        public MatureSupportGainedMetric(int gainedSupportCount, int firstSupportCount, List<KanjiSupportGain> examples) {
            this.gainedSupportCount = Math.max(0, gainedSupportCount);
            this.firstSupportCount = Math.max(0, firstSupportCount);
            this.examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static MatureSupportGainedMetric empty() {
            return new MatureSupportGainedMetric(0, 0, Collections.emptyList());
        }
    }

    public static final class KanjiSupportGain {
        public final String kanji;
        public final int beforeMatureSupport;
        public final int afterMatureSupport;

        public KanjiSupportGain(String kanji, int beforeMatureSupport, int afterMatureSupport) {
            this.kanji = kanji == null ? "" : kanji;
            this.beforeMatureSupport = Math.max(0, beforeMatureSupport);
            this.afterMatureSupport = Math.max(0, afterMatureSupport);
        }
    }

    private static final class ReviewWindow {
        private final String kanji;
        private final long firstReviewedAtMillis;
        private final long lastReviewedAtMillis;

        private ReviewWindow(String kanji, long firstReviewedAtMillis, long lastReviewedAtMillis) {
            this.kanji = kanji == null ? "" : kanji;
            this.firstReviewedAtMillis = Math.max(0L, firstReviewedAtMillis);
            this.lastReviewedAtMillis = Math.max(0L, lastReviewedAtMillis);
        }
    }

    private static final class OutcomeSnapshot {
        private final int weaknessScore;
        private final int matureSupportCount;

        private OutcomeSnapshot(int weaknessScore, int matureSupportCount) {
            this.weaknessScore = Math.max(0, weaknessScore);
            this.matureSupportCount = Math.max(0, matureSupportCount);
        }
    }

    private static final class OutcomeAccumulator {
        private final List<KanjiImprovement> improvements = new ArrayList<>();
        private final List<KanjiSupportGain> supportGains = new ArrayList<>();
        private double beforeWeaknessSum;
        private double afterWeaknessSum;
        private int firstSupportCount;

        private void add(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            if (before == null || after == null) {
                return;
            }
            addImprovement(kanji, before, after);
            addSupportGain(kanji, before, after);
        }

        private void addImprovement(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            int weaknessDrop = before.weaknessScore - after.weaknessScore;
            if (before.weaknessScore <= 0 || weaknessDrop < 5) {
                return;
            }
            double beforeWeakness = normalizedWeakness(before.weaknessScore);
            double afterWeakness = normalizedWeakness(after.weaknessScore);
            improvements.add(new KanjiImprovement(kanji, beforeWeakness, afterWeakness));
            beforeWeaknessSum += beforeWeakness;
            afterWeaknessSum += afterWeakness;
        }

        private static double normalizedWeakness(int weaknessScore) {
            return Math.max(0, weaknessScore) / 100.0;
        }

        private void addSupportGain(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            if (after.matureSupportCount <= before.matureSupportCount) {
                return;
            }
            supportGains.add(new KanjiSupportGain(kanji, before.matureSupportCount, after.matureSupportCount));
            if (before.matureSupportCount == 0) {
                firstSupportCount++;
            }
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
