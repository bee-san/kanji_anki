package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class LocalStoreBase extends SQLiteOpenHelper {
    static final String DB_NAME = LocalStoreSchema.DB_NAME;
    static final int DB_VERSION = LocalStoreSchema.DB_VERSION;
    static final String TABLE_SETTINGS = "settings";
    static final String TABLE_SYNC_RUNS = "sync_runs";
    static final String TABLE_SOURCE_NOTES = "source_notes";
    static final String TABLE_SOURCE_CARDS = "source_cards";
    static final String TABLE_SUSPENDED_ARCHIVE = "suspended_archive";
    static final String TABLE_SUSPENDED_IMPORTS = "suspended_imports";
    static final String TABLE_SUSPENDED_SOURCES = "suspended_sources";
    static final String TABLE_IMPORT_RULE_AUDITS = "import_rule_audits";
    static final String TABLE_IMPORT_DECISIONS = "import_decisions";
    static final String TABLE_DASHBOARD_ROWS = "dashboard_rows";
    static final String TABLE_KANJI_EXAMPLES = "kanji_examples";
    static final String TABLE_STUDY_ITEMS = "study_items";
    static final String TABLE_LEARNING_REPEATS = "learning_repeats";
    static final String TABLE_REVIEW_LOG = "review_log";
    static final String TABLE_KANJI_INVENTORY = "kanji_inventory";
    static final String TABLE_LOCAL_KANJI_SUSPENSIONS = "local_kanji_suspensions";
    static final String TABLE_SIMILAR_KANJI_PAIRS = "similar_kanji_pairs";
    static final String TABLE_SIMILAR_KANJI_CHOICE_STATE = "similar_kanji_choice_state";
    static final String TABLE_SIMILAR_KANJI_REPAIR_QUEUE = "similar_kanji_repair_queue";
    static final String TABLE_SIMILAR_KANJI_REVIEW_LOG = "similar_kanji_review_log";
    static final String TABLE_STUDY_TASK_LOG = "study_task_log";
    static final String TABLE_KANJI_TIMELINE_EVENTS = "kanji_timeline_events";
    static final String TABLE_SYNC_CARD_SNAPSHOTS = "sync_card_snapshots";
    static final String TABLE_SYNC_NOTE_SNAPSHOTS = "sync_note_snapshots";
    static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    static final String SQL_CREATE_TABLE = "CREATE TABLE ";
    static final String SQL_CREATE_TABLE_IF_NEEDED = "CREATE TABLE IF NOT EXISTS ";
    static final String SQL_TEXT_NOT_NULL_DEFAULT_EMPTY = "TEXT NOT NULL DEFAULT ''";
    static final String SQL_INTEGER_NOT_NULL_DEFAULT_ZERO = "INTEGER NOT NULL DEFAULT 0";
    static final String WHERE_KANJI = "kanji=?";
    static final String WHERE_SETTING_KEY = "key=?";
    static final String WHERE_SIMILAR_CHOICE = "target_kanji=? AND choice_signature=?";
    static final String ORDER_ID_DESC = "id DESC";
    static final String SQL_DELETE_FROM = "DELETE FROM ";
    static final String ORDER_KANJI_ASC = "kanji ASC";
    static final String ORDER_SIMILAR_PAIR = "kanji_a ASC, kanji_b ASC, source ASC";
    static final String COLUMN_ACTIVE_TOKEN = "active_token";
    static final String COLUMN_ANSWER_SIGNATURE = "answer_signature";
    static final String COLUMN_ATTEMPTS = "attempts";
    static final String COLUMN_ACTIVE_CARDS_COUNT = "active_cards_count";
    static final String COLUMN_ACTIVE_EXAMPLE_COUNT = "active_example_count";
    static final String COLUMN_ACTIVE_NOTES_COUNT = "active_notes_count";
    static final String COLUMN_BROWSER_SEARCH = "browser_search";
    static final String COLUMN_CARD_ID = "card_id";
    static final String COLUMN_CHOICE_SIGNATURE = "choice_signature";
    static final String COLUMN_CHOICES = "choices";
    static final String COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS = "consecutive_failed_recognition_days";
    static final String COLUMN_COMPLETED_AT = "completed_at";
    static final String COLUMN_CORRECT_COUNT = "correct_count";
    static final String COLUMN_CREATED_AT = "created_at";
    static final String COLUMN_CUTOFF_USED = "cutoff_used";
    static final String COLUMN_DECK_ID = "deck_id";
    static final String COLUMN_DECK_IDS = "deck_ids";
    static final String COLUMN_DECK_NAME = "deck_name";
    static final String COLUMN_DECK_NAMES = "deck_names";
    static final String COLUMN_DEDUPE_KEY = "dedupe_key";
    static final String COLUMN_DETAIL = "detail";
    static final String COLUMN_DECISION = "decision";
    static final String COLUMN_DUE_AT = "due_at";
    static final String COLUMN_ENABLED_SOURCES = "enabled_sources";
    static final String COLUMN_ERROR_MESSAGE = "error_message";
    static final String COLUMN_EVENT_TYPE = "event_type";
    static final String COLUMN_EXPRESSION = "expression";
    static final String COLUMN_FIELDS_JSON = "fields_json";
    static final String COLUMN_FINISHED_AT = "finished_at";
    static final String COLUMN_FIRST_SEEN_AT = "first_seen_at";
    static final String COLUMN_FONT_MEANING_MEMORY = "font_meaning_memory";
    static final String COLUMN_FSRS_DIFFICULTY = "fsrs_difficulty";
    static final String COLUMN_FSRS_RETRIEVABILITY = "fsrs_retrievability";
    static final String COLUMN_FSRS_STABILITY = "fsrs_stability";
    static final String COLUMN_INTERVAL_DAYS = "interval_days";
    static final String COLUMN_JITEN_RANK = "jiten_rank";
    static final String COLUMN_KANJI = "kanji";
    static final String COLUMN_KANJI_A = "kanji_a";
    static final String COLUMN_KANJI_B = "kanji_b";
    static final String COLUMN_KANJI_MEANING_MEMORY = "kanji_meaning_memory";
    static final String COLUMN_MEANING_KANJI_MEMORY = "meaning_kanji_memory";
    static final String COLUMN_LAST_FAILED_RECOGNITION_DAY = "last_failed_recognition_day";
    static final String COLUMN_LAST_REVIEWED_AT = "last_reviewed_at";
    static final String COLUMN_LAST_SEEN_AT = "last_seen_at";
    static final String COLUMN_LAST_SEEN_SYNC_ID = "last_seen_sync_id";
    static final String COLUMN_LAPSES = "lapses";
    static final String COLUMN_MANUAL_OVERRIDE = "manual_override";
    static final String COLUMN_MATURE_SUPPORT_COUNT = "mature_support_count";
    static final String COLUMN_MATURE_INTERVAL_DAYS = "mature_interval_days";
    static final String COLUMN_MEANING = "meaning";
    static final String COLUMN_MODEL_ID = "model_id";
    static final String COLUMN_MODEL_NAME = "model_name";
    static final String COLUMN_NOTE_ID = "note_id";
    static final String COLUMN_OCCURRED_AT = "occurred_at";
    static final String COLUMN_PASSED_AT = "passed_at";
    static final String COLUMN_PRIMARY_MEANING = "primary_meaning";
    static final String COLUMN_QUEUE = "queue";
    static final String COLUMN_RANK_KNOWN = "rank_known";
    static final String COLUMN_RATING = "rating";
    static final String COLUMN_READING = "reading";
    static final String COLUMN_RECOGNITION_STAGE = "recognition_stage";
    static final String COLUMN_REASON_CODE = "reason_code";
    static final String COLUMN_REASON_TEXT = "reason_text";
    static final String COLUMN_REMOVAL_MESSAGE = "removal_message";
    static final String COLUMN_REPS = "reps";
    static final String COLUMN_REVIEW_DAY_START = "review_day_start";
    static final String COLUMN_REVIEWED_AT = "reviewed_at";
    static final String COLUMN_SENTENCE = "sentence";
    static final String COLUMN_SOURCE = "source";
    static final String COLUMN_SOURCE_CARD_IDS = "source_card_ids";
    static final String COLUMN_SOURCE_COUNT = "source_count";
    static final String COLUMN_SOURCE_NOTE_IDS = "source_note_ids";
    static final String COLUMN_SOURCE_TYPES = "source_types";
    static final String COLUMN_STATE = "state";
    static final String COLUMN_STATUS = "status";
    static final String COLUMN_STARTED_AT = "started_at";
    static final String COLUMN_SYNC_ID = "sync_id";
    static final String COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT = "suspended_cards_archived_count";
    static final String COLUMN_SUSPENDED_EXAMPLE_COUNT = "suspended_example_count";
    static final String COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT = "suspended_kanji_imported_count";
    static final String COLUMN_SUPPRESSED_AT = "suppressed_at";
    static final String COLUMN_SUPPRESSED_BY_TASK_TYPE = "suppressed_by_task_type";
    static final String COLUMN_TARGET_KANJI = "target_kanji";
    static final String COLUMN_TAGS = "tags";
    static final String COLUMN_TASK_TYPE = "task_type";
    static final String COLUMN_TITLE = "title";
    static final String COLUMN_TYPING_MEANING_MEMORY = "typing_meaning_memory";
    static final String COLUMN_TOKEN = "token";
    static final String COLUMN_UPDATED_AT = "updated_at";
    static final String COLUMN_VALUE = "value";
    static final String COLUMN_WEAKNESS_SCORE = "weakness_score";
    static final String COLUMN_RULE_TYPES = "rule_types";
    static final String COLUMN_SETTINGS_JSON = "settings_json";
    static final String COLUMN_WORD_READING_MEMORY = "word_reading_memory";
    static final String COLUMN_WRONG_COUNT = "wrong_count";
    static final String COLUMN_WRITING_REMEDIATION_MEMORY = "writing_remediation_memory";
    static final String COLUMN_WRITING_REMEDIATION_PENDING = "writing_remediation_pending";
    static final String COLUMN_WRITING_PASSED = "writing_passed";
    static final String COLUMN_WRITING_REQUIRED = "writing_required";
    static final String COLUMN_RUNG = "rung";
    static final String COLUMN_PHASE = "phase";
    static final String COLUMN_REAL_PASS_STREAK = "real_pass_streak";
    static final String COLUMN_REAL_AGAIN_STREAK = "real_again_streak";
    static final String COLUMN_LAST_REAL_REVIEW_DUE_AT = "last_real_review_due_at";
    static final String COLUMN_SIMILAR_KANJI_MEMORY = "similar_kanji_memory";
    static final String STUDY_ITEMS_TABLE_SQL = SQL_CREATE_TABLE + TABLE_STUDY_ITEMS + " (kanji TEXT NOT NULL, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, recognition_stage INTEGER NOT NULL DEFAULT 0, consecutive_failed_recognition_days INTEGER NOT NULL DEFAULT 0, last_failed_recognition_day INTEGER NOT NULL DEFAULT 0, writing_remediation_pending INTEGER NOT NULL DEFAULT 0, suppressed_by_task_type TEXT NOT NULL DEFAULT '', suppressed_at INTEGER NOT NULL DEFAULT 0, mature_interval_days INTEGER NOT NULL DEFAULT 0, answer_signature TEXT NOT NULL DEFAULT '', typing_meaning_memory TEXT NOT NULL DEFAULT '', meaning_kanji_memory TEXT NOT NULL DEFAULT '', kanji_meaning_memory TEXT NOT NULL DEFAULT '', font_meaning_memory TEXT NOT NULL DEFAULT '', word_reading_memory TEXT NOT NULL DEFAULT '', writing_remediation_memory TEXT NOT NULL DEFAULT '', rung TEXT NOT NULL DEFAULT 'kanji_meaning', phase TEXT NOT NULL DEFAULT 'new_learning', real_pass_streak INTEGER NOT NULL DEFAULT 0, real_again_streak INTEGER NOT NULL DEFAULT 0, last_real_review_due_at INTEGER NOT NULL DEFAULT 0, similar_kanji_memory TEXT NOT NULL DEFAULT '', active_token TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature))";
    static final String LEARNING_REPEATS_TABLE_SQL = SQL_CREATE_TABLE + TABLE_LEARNING_REPEATS + " (kanji TEXT NOT NULL, answer_signature TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL, repeat_type TEXT NOT NULL, step_index INTEGER NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature, task_type))";
    static final String REVIEW_LOG_TABLE_SQL = SQL_CREATE_TABLE + TABLE_REVIEW_LOG + " (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL, review_day_start INTEGER NOT NULL DEFAULT 0, task_type TEXT NOT NULL DEFAULT '', answer_signature TEXT NOT NULL DEFAULT '', prompt TEXT NOT NULL DEFAULT '', hints_used INTEGER NOT NULL DEFAULT 0, writing_clean INTEGER NOT NULL DEFAULT 0, memory_before TEXT NOT NULL DEFAULT '', memory_after TEXT NOT NULL DEFAULT '', scheduler_state_after_json TEXT NOT NULL DEFAULT '')";
    static final String STUDY_TASK_LOG_TABLE_SQL = SQL_CREATE_TABLE_IF_NEEDED + TABLE_STUDY_TASK_LOG + " (id INTEGER PRIMARY KEY AUTOINCREMENT, task_key TEXT NOT NULL UNIQUE, kanji TEXT NOT NULL, task_type TEXT NOT NULL, started_at INTEGER NOT NULL, answered_at INTEGER NOT NULL, active_elapsed_ms INTEGER NOT NULL, outcome TEXT NOT NULL)";
    static final long MAX_STUDY_TASK_ELAPSED_MS = 30L * 60L * 1000L;
    static final String RATING_AGAIN = "again";
    static final String STATE_RETIRED = "retired";
    static final String STATUS_SUCCESS = "success";
    static final String STATUS_PENDING = "pending";
    static final String STATUS_COMPLETE = "complete";
    static final String TIMELINE_FIRST_SEEN = "first_seen";
    static final String TIMELINE_FIRST_SEEN_TITLE = "Kani started watching";
    static final String TIMELINE_FIRST_SEEN_KEY_PREFIX = "first_seen:";
    static final String COLUMN_MATURE = "mature";
    static final String COLUMN_FIRST_IMPORTED_AT = "first_imported_at";
    static final String KEY_AUTO_SYNC_LAST_ATTEMPT_AT = "auto_sync_last_attempt_at";
    static final String KEY_AUTO_SYNC_LAST_SUCCESS_AT = "auto_sync_last_success_at";
    static final String KEY_AUTO_SYNC_NEXT_RUN_AT = "auto_sync_next_run_at";
    static final String SIMILAR_KEY_DELIMITER = "\u0000";
    static final String SIMILAR_CHOICE_KEY_DELIMITER = "\u0001";
    static final int DEFAULT_REMINDER_HOUR = 19;
    static final int DEFAULT_REMINDER_MINUTE = 0;
    static final int DEFAULT_AUTO_SYNC_HOUR = DEFAULT_REMINDER_HOUR;
    static final int DEFAULT_AUTO_SYNC_MINUTE = DEFAULT_REMINDER_MINUTE;
    static final String SETTING_STUDY_AHEAD_MINUTES = "study_ahead_minutes";
    static final int DEFAULT_STUDY_AHEAD_MINUTES = 0;
    static final int MAX_STUDY_AHEAD_MINUTES = 1440;
    static final String KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled";
    static final String KEY_AUTO_UPDATE_LAST_CHECK_AT = "auto_update_last_check_at";
    static final String KEY_AUTO_UPDATE_LAST_RESULT = "auto_update_last_result";
    static final String KEY_AUTO_UPDATE_LAST_VERSION = "auto_update_last_version";
    static final String KEY_AUTO_UPDATE_PENDING_APK = "auto_update_pending_apk";
    static final int MAX_DISPLAYED_INVENTORY_READINGS = 3;
    static final String KEY_AUTO_UPDATE_PENDING_MESSAGE = "auto_update_pending_message";

    private final SettingsRepository settingsRepository = new SettingsRepository(this);

    LocalStoreBase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    SettingsRepository settingsRepository() {
        return settingsRepository;
    }

    LocalStoreMigrationHooks migrationHooks() {
        return new LocalStoreMigrationHooks(this);
    }

    abstract void createTimelineTables(SQLiteDatabase db);
    abstract void backfillTimelineEvents(SQLiteDatabase db);
    abstract void addNullableColumn(SQLiteDatabase db, String table, String column, String type);
    abstract void backfillKanjiInventory(SQLiteDatabase db, long nowMillis, RecordsSyncModels.Settings settings);
    abstract void rebuildSimilarKanjiChoiceStates(SQLiteDatabase db, long nowMillis);
    abstract void backfillLatestHistoricalSync(SQLiteDatabase db);

    @Override
    public void onCreate(SQLiteDatabase db) {
        LocalStoreSchema.createInitialTables(db, migrationHooks());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LocalStoreMigrations.upgrade(db, oldVersion, newVersion, migrationHooks());
    }

    void rebuildStudyItemsForLadderScheduler(SQLiteDatabase db) {
        for (String sql : StudySchedulerMigration.rebuildLadderStudyItemsSql(
                TABLE_STUDY_ITEMS,
                STUDY_ITEMS_TABLE_SQL.replace(SQL_CREATE_TABLE, SQL_CREATE_TABLE_IF_NEEDED),
                TABLE_LEARNING_REPEATS,
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE
        )) {
            db.execSQL(sql);
        }
    }

    void createStudyTaskLogTable(SQLiteDatabase db) {
        db.execSQL(STUDY_TASK_LOG_TABLE_SQL);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_task_log_answered ON " + TABLE_STUDY_TASK_LOG + "(answered_at)");
    }

    void createStatsIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_reviewed_at ON " + TABLE_REVIEW_LOG + "(reviewed_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_day_reviewed ON " + TABLE_REVIEW_LOG + "(review_day_start, reviewed_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_kanji_reviewed ON " + TABLE_REVIEW_LOG + "(kanji, reviewed_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_rating_reviewed ON " + TABLE_REVIEW_LOG + "(rating, reviewed_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_items_ladder_stats ON " + TABLE_STUDY_ITEMS + "(state, phase, rung)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_kanji_snapshots_kanji_finished ON " + TABLE_SYNC_KANJI_SNAPSHOTS + "(kanji, finished_at)");
    }

    void ensureStatsAggregateStorage(SQLiteDatabase db) {
        db.execSQL(REVIEW_LOG_TABLE_SQL.replace(SQL_CREATE_TABLE, SQL_CREATE_TABLE_IF_NEEDED));
        addRichReviewColumns(db);
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_REVIEW_DAY_START, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        createStudyTaskLogTable(db);
        createTimelineTables(db);
        createHistoricalSyncTables(db);
        createStatsIndexes(db);
    }

    void repairHistoricalSyncSnapshotsIfPossible(SQLiteDatabase db) {
        createHistoricalSyncTables(db);
        backfillLatestHistoricalSync(db);
    }

    void createKanjiInventoryTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_KANJI_INVENTORY + " (kanji TEXT PRIMARY KEY, primary_meaning TEXT NOT NULL, readings TEXT NOT NULL, browser_search TEXT NOT NULL, search_text TEXT NOT NULL, source_count INTEGER NOT NULL, example_count INTEGER NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_LOCAL_KANJI_SUSPENSIONS + " (kanji TEXT PRIMARY KEY, suspended_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kanji_inventory_search ON kanji_inventory(search_text)");
    }

    void createSimilarKanjiTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_PAIRS + " (kanji_a TEXT NOT NULL, kanji_b TEXT NOT NULL, source TEXT NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (kanji_a, kanji_b, source))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_a ON similar_kanji_pairs(kanji_a)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_b ON similar_kanji_pairs(kanji_b)");
    }

    void createSimilarKanjiPracticeTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_CHOICE_STATE + " (target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, primary_meaning TEXT NOT NULL, choices TEXT NOT NULL, due_at INTEGER NOT NULL, passed_at INTEGER NOT NULL DEFAULT 0, last_reviewed_at INTEGER NOT NULL DEFAULT 0, correct_count INTEGER NOT NULL DEFAULT 0, wrong_count INTEGER NOT NULL DEFAULT 0, active_token TEXT NOT NULL DEFAULT '', first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (target_kanji, choice_signature))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_choice_due ON similar_kanji_choice_state(passed_at, due_at)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_REPAIR_QUEUE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, repair_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, wrong_selection TEXT NOT NULL, prompt_meaning TEXT NOT NULL, status TEXT NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', attempts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_repair_due ON similar_kanji_repair_queue(status, due_at, created_at)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SIMILAR_KANJI_REVIEW_LOG + " (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, selected_kanji TEXT NOT NULL, correct INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_review_log_target ON similar_kanji_review_log(target_kanji, reviewed_at)");
    }

    void createHistoricalSyncTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SYNC_CARD_SNAPSHOTS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, sync_id INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, deck_id TEXT NOT NULL DEFAULT '', deck_name TEXT NOT NULL, model_id INTEGER NOT NULL DEFAULT 0, model_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, suspended INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, mature INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_card_snapshots_sync_card ON " + TABLE_SYNC_CARD_SNAPSHOTS + "(sync_id, card_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_card_snapshots_note ON " + TABLE_SYNC_CARD_SNAPSHOTS + "(sync_id, note_id)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SYNC_NOTE_SNAPSHOTS + " (sync_id INTEGER NOT NULL, finished_at INTEGER NOT NULL, note_id INTEGER NOT NULL, model_id INTEGER NOT NULL DEFAULT 0, model_name TEXT NOT NULL, deck_ids TEXT NOT NULL DEFAULT '', deck_names TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, tags TEXT NOT NULL, fields_json TEXT NOT NULL, extracted_kanji TEXT NOT NULL, PRIMARY KEY (sync_id, note_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_note_snapshots_kanji ON " + TABLE_SYNC_NOTE_SNAPSHOTS + "(sync_id, extracted_kanji)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_SYNC_KANJI_SNAPSHOTS + " (sync_id INTEGER NOT NULL, finished_at INTEGER NOT NULL, kanji TEXT NOT NULL, active_cards INTEGER NOT NULL, suspended_cards INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, average_interval_days REAL NOT NULL, total_lapses INTEGER NOT NULL, total_reps INTEGER NOT NULL, fsrs_stability_avg REAL, fsrs_difficulty_avg REAL, fsrs_retrievability_avg REAL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, PRIMARY KEY (sync_id, kanji))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_kanji_snapshots_kanji_sync ON " + TABLE_SYNC_KANJI_SNAPSHOTS + "(kanji, sync_id)");
    }

    void createImportAuditTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_IMPORT_RULE_AUDITS + " (sync_id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, model_name TEXT NOT NULL, enabled_sources TEXT NOT NULL, rank_min INTEGER NOT NULL, rank_max INTEGER NOT NULL, min_matching_cards INTEGER NOT NULL, import_tags TEXT NOT NULL, weak_fsrs_difficulty REAL NOT NULL, weak_lapses INTEGER NOT NULL, browser_query TEXT NOT NULL, settings_json TEXT NOT NULL)");
        db.execSQL(SQL_CREATE_TABLE_IF_NEEDED + TABLE_IMPORT_DECISIONS + " (sync_id INTEGER NOT NULL, kanji TEXT NOT NULL, decision TEXT NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, jiten_rank INTEGER, rank_known INTEGER NOT NULL, rank_min INTEGER NOT NULL, rank_max INTEGER NOT NULL, min_matching_cards INTEGER NOT NULL, source_count INTEGER NOT NULL, source_types TEXT NOT NULL, rule_types TEXT NOT NULL, source_card_ids TEXT NOT NULL, source_note_ids TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (sync_id, kanji))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_import_decisions_kanji_sync ON " + TABLE_IMPORT_DECISIONS + "(kanji, sync_id)");
    }

    void addHistoricalIdentityColumns(SQLiteDatabase db) {
        addNullableColumn(db, TABLE_SYNC_CARD_SNAPSHOTS, COLUMN_DECK_ID, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_SYNC_CARD_SNAPSHOTS, COLUMN_MODEL_ID, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_SYNC_NOTE_SNAPSHOTS, COLUMN_MODEL_ID, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_SYNC_NOTE_SNAPSHOTS, COLUMN_DECK_IDS, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        db.execSQL("UPDATE " + TABLE_SYNC_CARD_SNAPSHOTS + " SET deck_id=deck_name WHERE deck_id=''");
        db.execSQL("UPDATE " + TABLE_SYNC_NOTE_SNAPSHOTS + " SET deck_ids=deck_names WHERE deck_ids=''");
    }

    void addRichReviewColumns(SQLiteDatabase db) {
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_TASK_TYPE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_ANSWER_SIGNATURE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "prompt", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "hints_used", SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_REVIEW_LOG, "writing_clean", SQL_INTEGER_NOT_NULL_DEFAULT_ZERO);
        addNullableColumn(db, TABLE_REVIEW_LOG, "memory_before", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "memory_after", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
        addNullableColumn(db, TABLE_REVIEW_LOG, "scheduler_state_after_json", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY);
    }

    void rebuildStudyItemsWithAnswerSignatureKey(SQLiteDatabase db) {
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

    static final class MutableSuspendedImport {
        final String kanji;
        final Integer rank;
        final boolean rankKnown;
        final int cutoff;
        final List<RecordsImportModels.SuspendedSource> sources = new ArrayList<>();

        MutableSuspendedImport(String kanji, Integer rank, boolean rankKnown, int cutoff) {
            this.kanji = kanji;
            this.rank = rank;
            this.rankKnown = rankKnown;
            this.cutoff = cutoff;
        }

        RecordsImportModels.SuspendedImport build() {
            return new RecordsImportModels.SuspendedImport(kanji, rank, rankKnown, cutoff, sources);
        }
    }

    static final class ActiveCardIndex {
        final Set<Long> noteIds;
        final Set<Long> cardIds;
        final int activeCardCount;

        ActiveCardIndex(Set<Long> noteIds, Set<Long> cardIds, int activeCardCount) {
            this.noteIds = noteIds;
            this.cardIds = cardIds;
            this.activeCardCount = activeCardCount;
        }
    }

    record SyncRunInsert(
            long startedAt,
            long finishedAt,
            String status,
            ActiveCardIndex activeIndex,
            int archivedSuspendedCardCount,
            int importCount,
            String errorCode,
            String errorMessage,
            String removalMessage,
            int deletedNotes,
            int deletedCards
    ) {
    }

    record StudyMemoryFields(
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

    static final class HistoricalSyncRun {
        final long id;
        final long startedAt;
        final long finishedAt;

        HistoricalSyncRun(long id, long startedAt, long finishedAt) {
            this.id = id;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }
    }

    static final class HistoricalNoteSnapshot {
        final long noteId;
        final long modelId;
        final String modelName;
        final String expression;
        final String reading;
        final String meaning;
        final String sentence;
        final String tags;
        final String fieldsJson;

        HistoricalNoteSnapshot(HistoricalNoteFields fields) {
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

        static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    record HistoricalNoteFields(
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

    record CardMetrics(
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

    record HistoricalCardMetrics(int intervalDays, int reps, int lapses, boolean mature) {
    }

    record HistoricalBackfillContext(
            RecordsSyncModels.Settings settings,
            Map<Long, LinkedHashSet<String>> deckIdsByNote,
            Map<Long, LinkedHashSet<String>> deckNamesByNote,
            Map<String, HistoricalKanjiAggregate> aggregates
    ) {
    }

    static final class HistoricalKanjiAggregate {
        final String kanji;
        int activeCards;
        int suspendedCards;
        int matureSupportCount;
        int totalLapses;
        int totalReps;
        int intervalCount;
        double intervalSum;
        int stabilityCount;
        double stabilitySum;
        int difficultyCount;
        double difficultySum;
        int retrievabilityCount;
        double retrievabilitySum;
        int weaknessScore;
        String reasonCode = "";
        int activeExampleCount;
        int suspendedExampleCount;

        HistoricalKanjiAggregate(String kanji) {
            this.kanji = kanji == null ? "" : kanji;
        }

        void add(RecordsSyncModels.Card card, int matureDays) {
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

        void add(CardMetrics metrics) {
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

        double averageIntervalDays() {
            return intervalCount == 0 ? 0.0 : intervalSum / intervalCount;
        }

        Double averageStability() {
            return stabilityCount == 0 ? null : stabilitySum / stabilityCount;
        }

        Double averageDifficulty() {
            return difficultyCount == 0 ? null : difficultySum / difficultyCount;
        }

        Double averageRetrievability() {
            return retrievabilityCount == 0 ? null : retrievabilitySum / retrievabilityCount;
        }
    }

    static final class SimilarChoiceSnapshot {
        final long dueAtMillis;
        final long passedAtMillis;
        final long lastReviewedAtMillis;
        final int correctCount;
        final int wrongCount;
        final long firstSeenAtMillis;

        SimilarChoiceSnapshot(
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

    static final class MutableKanjiInventoryItem {
        final String kanji;
        String primaryMeaning = "";
        String browserSearch = "";
        int sourceCount = 0;
        int exampleCount = 0;
        final Set<String> readings = new LinkedHashSet<>();
        final Set<String> searchParts = new HashSet<>();

        MutableKanjiInventoryItem(String kanji) {
            this.kanji = kanji == null ? "" : kanji;
            searchParts.add(this.kanji.toLowerCase(Locale.ROOT));
        }

        void add(String meaning, String reading, String expression, String sentence) {
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

        void addSearch(String value) {
            String normalized = TextUtil.normalizeJapanese(value);
            if (!normalized.isEmpty()) {
                searchParts.add(normalized.toLowerCase(Locale.ROOT));
            }
        }

        String readingsText(String previous) {
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

        String searchText(RecordsImportModels.KanjiInventoryItem previous) {
            if (previous != null) {
                addSearch(previous.primaryMeaning);
                addSearch(previous.readings);
                addSearch(previous.browserSearch);
            }
            return String.join(" ", searchParts);
        }
    }

    static final class SourceSnapshot {
        static final SourceSnapshot EMPTY = new SourceSnapshot("", "");

        final String expression;
        final String reading;

        SourceSnapshot(String expression, String reading) {
            this.expression = expression == null ? "" : expression;
            this.reading = reading == null ? "" : reading;
        }
    }

    static final class RowSnapshot {
        final String kanji;
        final int weaknessScore;
        final int matureSupportCount;
        final long rebuiltAt;
        final SourceSnapshot source;

        RowSnapshot(String kanji, int weaknessScore, int matureSupportCount, long rebuiltAt, SourceSnapshot source) {
            this.kanji = kanji;
            this.weaknessScore = weaknessScore;
            this.matureSupportCount = matureSupportCount;
            this.rebuiltAt = rebuiltAt;
            this.source = source == null ? SourceSnapshot.EMPTY : source;
        }
    }

    static final class StudySnapshot {
        final String state;

        StudySnapshot(String state) {
            this.state = state == null ? "" : state;
        }
    }

    static String studyFamilyKey(String kanji, String answerSignature) {
        return kanji + SIMILAR_KEY_DELIMITER + (answerSignature == null ? "" : answerSignature);
    }

    static String studyTimelineKey(RecordsStudyModels.StudyItem item) {
        return item.kanji + ":" + Integer.toHexString((item.answerSignature == null ? "" : item.answerSignature).hashCode());
    }

    static String fieldsJson(Map<String, String> fields) {
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

    static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    static int integer(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    static Integer nullableInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getInt(index);
    }

    static Long nullableLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getLong(index);
    }

    static Double nullableDouble(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    static long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    static void putNullableDouble(ContentValues values, String key, Double value) {
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

        SyncStatus(SyncStatusValues values) {
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
            return String.format(Locale.ROOT, "%d suspended cards archived, %d rare kanji added; active cards optional", suspendedCards, importedKanji);
        }
    }

    record SyncStatusValues(
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

        ReminderSettings normalized() {
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

        AutoSyncSettings normalized() {
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

        AutoUpdateStatus(boolean enabled, long lastCheckAtMillis, String lastResult, String lastVersion, String pendingApkName, String pendingMessage) {
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

}
