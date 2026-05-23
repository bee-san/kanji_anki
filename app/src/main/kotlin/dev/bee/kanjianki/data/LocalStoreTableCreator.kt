package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase

internal object LocalStoreTableCreator {
    private const val COLUMN_FIRST_SEEN_AT = "first_seen_at INTEGER NOT NULL"
    private const val COLUMN_LAST_SEEN_AT = "last_seen_at INTEGER NOT NULL"
    private const val COLUMN_TARGET_KANJI = "target_kanji TEXT NOT NULL"
    private const val COLUMN_CHOICE_SIGNATURE = "choice_signature TEXT NOT NULL"
    private const val COLUMN_ID_AUTOINCREMENT = "id INTEGER PRIMARY KEY AUTOINCREMENT"
    private const val COLUMN_CREATED_AT = "created_at INTEGER NOT NULL"
    private const val COLUMN_SYNC_ID = "sync_id INTEGER NOT NULL"
    private const val COLUMN_FINISHED_AT = "finished_at INTEGER NOT NULL"
    private const val COLUMN_MODEL_NAME = "model_name TEXT NOT NULL"

    fun createStudyTaskLogTable(db: SQLiteDatabase) {
        db.execSQL(LocalStoreBase.STUDY_TASK_LOG_TABLE_SQL)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_task_log_answered ON ${LocalStoreBase.TABLE_STUDY_TASK_LOG}(answered_at)")
    }

    fun createStatsIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_reviewed_at ON ${LocalStoreBase.TABLE_REVIEW_LOG}(reviewed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_day_reviewed ON ${LocalStoreBase.TABLE_REVIEW_LOG}(review_day_start, reviewed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_kanji_reviewed ON ${LocalStoreBase.TABLE_REVIEW_LOG}(kanji, reviewed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_rating_reviewed ON ${LocalStoreBase.TABLE_REVIEW_LOG}(rating, reviewed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_items_ladder_stats ON ${LocalStoreBase.TABLE_STUDY_ITEMS}(state, phase, rung)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_kanji_snapshots_kanji_finished ON ${LocalStoreBase.TABLE_SYNC_KANJI_SNAPSHOTS}(kanji, finished_at)")
    }

    fun createKanjiInventoryTables(db: SQLiteDatabase) {
        db.createTableIfMissing(
            LocalStoreBase.TABLE_KANJI_INVENTORY,
            "kanji TEXT PRIMARY KEY",
            "primary_meaning TEXT NOT NULL",
            "readings TEXT NOT NULL",
            "browser_search TEXT NOT NULL",
            "search_text TEXT NOT NULL",
            "source_count INTEGER NOT NULL",
            "example_count INTEGER NOT NULL",
            COLUMN_FIRST_SEEN_AT,
            COLUMN_LAST_SEEN_AT,
        )
        db.createTableIfMissing(
            LocalStoreBase.TABLE_LOCAL_KANJI_SUSPENSIONS,
            "kanji TEXT PRIMARY KEY",
            "suspended_at INTEGER NOT NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kanji_inventory_search ON kanji_inventory(search_text)")
    }

    fun createSimilarKanjiTables(db: SQLiteDatabase) {
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS,
            "kanji_a TEXT NOT NULL",
            "kanji_b TEXT NOT NULL",
            "source TEXT NOT NULL",
            COLUMN_FIRST_SEEN_AT,
            COLUMN_LAST_SEEN_AT,
            "PRIMARY KEY (kanji_a, kanji_b, source)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_a ON similar_kanji_pairs(kanji_a)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_kanji_pairs_b ON similar_kanji_pairs(kanji_b)")
    }

    fun createSimilarKanjiPracticeTables(db: SQLiteDatabase) {
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
            COLUMN_TARGET_KANJI,
            COLUMN_CHOICE_SIGNATURE,
            "primary_meaning TEXT NOT NULL",
            "choices TEXT NOT NULL",
            "due_at INTEGER NOT NULL",
            "passed_at INTEGER NOT NULL DEFAULT 0",
            "last_reviewed_at INTEGER NOT NULL DEFAULT 0",
            "correct_count INTEGER NOT NULL DEFAULT 0",
            "wrong_count INTEGER NOT NULL DEFAULT 0",
            "active_token TEXT NOT NULL DEFAULT ''",
            COLUMN_FIRST_SEEN_AT,
            COLUMN_LAST_SEEN_AT,
            "PRIMARY KEY (target_kanji, choice_signature)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_choice_due ON similar_kanji_choice_state(passed_at, due_at)")
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            COLUMN_ID_AUTOINCREMENT,
            COLUMN_TARGET_KANJI,
            "repair_kanji TEXT NOT NULL",
            COLUMN_CHOICE_SIGNATURE,
            "wrong_selection TEXT NOT NULL",
            "prompt_meaning TEXT NOT NULL",
            "status TEXT NOT NULL",
            "due_at INTEGER NOT NULL",
            "active_token TEXT NOT NULL DEFAULT ''",
            "attempts INTEGER NOT NULL DEFAULT 0",
            COLUMN_CREATED_AT,
            "updated_at INTEGER NOT NULL",
            "completed_at INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_repair_due ON similar_kanji_repair_queue(status, due_at, created_at)")
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REVIEW_LOG,
            COLUMN_ID_AUTOINCREMENT,
            COLUMN_TARGET_KANJI,
            COLUMN_CHOICE_SIGNATURE,
            "selected_kanji TEXT NOT NULL",
            "correct INTEGER NOT NULL",
            "reviewed_at INTEGER NOT NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_similar_review_log_target ON similar_kanji_review_log(target_kanji, reviewed_at)")
    }

    fun createHistoricalSyncTables(db: SQLiteDatabase) {
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SYNC_CARD_SNAPSHOTS,
            COLUMN_ID_AUTOINCREMENT,
            COLUMN_SYNC_ID,
            "started_at INTEGER NOT NULL",
            COLUMN_FINISHED_AT,
            "card_id INTEGER NOT NULL",
            "note_id INTEGER NOT NULL",
            "deck_id TEXT NOT NULL DEFAULT ''",
            "deck_name TEXT NOT NULL",
            "model_id INTEGER NOT NULL DEFAULT 0",
            COLUMN_MODEL_NAME,
            "ord INTEGER NOT NULL",
            "queue INTEGER NOT NULL",
            "type INTEGER NOT NULL",
            "due INTEGER NOT NULL",
            "interval_days INTEGER NOT NULL",
            "reps INTEGER NOT NULL",
            "lapses INTEGER NOT NULL",
            "suspended INTEGER NOT NULL",
            "fsrs_stability REAL",
            "fsrs_difficulty REAL",
            "fsrs_retrievability REAL",
            "mature INTEGER NOT NULL",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_card_snapshots_sync_card ON ${LocalStoreBase.TABLE_SYNC_CARD_SNAPSHOTS}(sync_id, card_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_card_snapshots_note ON ${LocalStoreBase.TABLE_SYNC_CARD_SNAPSHOTS}(sync_id, note_id)")
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SYNC_NOTE_SNAPSHOTS,
            COLUMN_SYNC_ID,
            COLUMN_FINISHED_AT,
            "note_id INTEGER NOT NULL",
            "model_id INTEGER NOT NULL DEFAULT 0",
            COLUMN_MODEL_NAME,
            "deck_ids TEXT NOT NULL DEFAULT ''",
            "deck_names TEXT NOT NULL",
            "expression TEXT NOT NULL",
            "reading TEXT NOT NULL",
            "meaning TEXT NOT NULL",
            "sentence TEXT NOT NULL",
            "tags TEXT NOT NULL",
            "fields_json TEXT NOT NULL",
            "extracted_kanji TEXT NOT NULL",
            "PRIMARY KEY (sync_id, note_id)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_note_snapshots_kanji ON ${LocalStoreBase.TABLE_SYNC_NOTE_SNAPSHOTS}(sync_id, extracted_kanji)")
        db.createTableIfMissing(
            LocalStoreBase.TABLE_SYNC_KANJI_SNAPSHOTS,
            COLUMN_SYNC_ID,
            COLUMN_FINISHED_AT,
            "kanji TEXT NOT NULL",
            "active_cards INTEGER NOT NULL",
            "suspended_cards INTEGER NOT NULL",
            "mature_support_count INTEGER NOT NULL",
            "average_interval_days REAL NOT NULL",
            "total_lapses INTEGER NOT NULL",
            "total_reps INTEGER NOT NULL",
            "fsrs_stability_avg REAL",
            "fsrs_difficulty_avg REAL",
            "fsrs_retrievability_avg REAL",
            "weakness_score INTEGER NOT NULL",
            "reason_code TEXT NOT NULL",
            "active_example_count INTEGER NOT NULL",
            "suspended_example_count INTEGER NOT NULL",
            "PRIMARY KEY (sync_id, kanji)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_kanji_snapshots_kanji_sync ON ${LocalStoreBase.TABLE_SYNC_KANJI_SNAPSHOTS}(kanji, sync_id)")
    }

    fun createImportAuditTables(db: SQLiteDatabase) {
        db.createTableIfMissing(
            LocalStoreBase.TABLE_IMPORT_RULE_AUDITS,
            "sync_id INTEGER PRIMARY KEY",
            COLUMN_CREATED_AT,
            COLUMN_MODEL_NAME,
            "enabled_sources TEXT NOT NULL",
            "rank_min INTEGER NOT NULL",
            "rank_max INTEGER NOT NULL",
            "min_matching_cards INTEGER NOT NULL",
            "import_tags TEXT NOT NULL",
            "weak_fsrs_difficulty REAL NOT NULL",
            "weak_lapses INTEGER NOT NULL",
            "browser_query TEXT NOT NULL",
            "settings_json TEXT NOT NULL",
        )
        db.createTableIfMissing(
            LocalStoreBase.TABLE_IMPORT_DECISIONS,
            COLUMN_SYNC_ID,
            "kanji TEXT NOT NULL",
            "decision TEXT NOT NULL",
            "reason_code TEXT NOT NULL",
            "reason_text TEXT NOT NULL",
            "jiten_rank INTEGER",
            "rank_known INTEGER NOT NULL",
            "rank_min INTEGER NOT NULL",
            "rank_max INTEGER NOT NULL",
            "min_matching_cards INTEGER NOT NULL",
            "source_count INTEGER NOT NULL",
            "source_types TEXT NOT NULL",
            "rule_types TEXT NOT NULL",
            "source_card_ids TEXT NOT NULL",
            "source_note_ids TEXT NOT NULL",
            COLUMN_CREATED_AT,
            "PRIMARY KEY (sync_id, kanji)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_import_decisions_kanji_sync ON ${LocalStoreBase.TABLE_IMPORT_DECISIONS}(kanji, sync_id)")
    }

    private fun SQLiteDatabase.createTableIfMissing(table: String, vararg definitions: String) {
        execSQL("${LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED}$table (${definitions.joinToString(", ")})")
    }
}
