package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase

internal object LocalStoreSchema {
    const val DB_NAME: String = "kanji_anki_simple.db"
    const val DB_VERSION: Int = 34

    @JvmStatic
    fun createInitialTables(db: SQLiteDatabase, hooks: LocalStoreMigrationHooks) {
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SETTINGS + " (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SYNC_RUNS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, active_notes_count INTEGER NOT NULL, active_cards_count INTEGER NOT NULL, suspended_cards_archived_count INTEGER NOT NULL, suspended_kanji_imported_count INTEGER NOT NULL, deleted_notes_count INTEGER NOT NULL, deleted_cards_count INTEGER NOT NULL, error_code TEXT, error_message TEXT, removal_message TEXT)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SOURCE_NOTES + " (note_id INTEGER PRIMARY KEY, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, tags TEXT NOT NULL, last_seen_sync_id INTEGER NOT NULL)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SOURCE_CARDS + " (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, last_seen_sync_id INTEGER NOT NULL)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SUSPENDED_ARCHIVE + " (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, archived_at INTEGER NOT NULL, archived_sync_id INTEGER NOT NULL, restored_at INTEGER)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SUSPENDED_IMPORTS + " (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, rank_known INTEGER NOT NULL, cutoff_used INTEGER NOT NULL, first_imported_at INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_SUSPENDED_SOURCES + " (kanji TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, sync_id INTEGER NOT NULL, PRIMARY KEY (kanji, card_id))")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_DASHBOARD_ROWS + " (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, primary_meaning TEXT NOT NULL, reading TEXT NOT NULL, browser_search TEXT NOT NULL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)")
        db.execSQL(LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED + LocalStoreBase.TABLE_KANJI_EXAMPLES + " (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, mature INTEGER NOT NULL, lapses INTEGER NOT NULL, interval_days INTEGER NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL)")
        hooks.createImportAuditTables(db)
        hooks.createKanjiInventoryTables(db)
        hooks.createKanjiMnemonicNotesTable(db)
        hooks.createMissingKanjiTables(db)
        hooks.createSimilarKanjiTables(db)
        hooks.createSimilarKanjiPracticeTables(db)
        hooks.createKanjiReadingTables(db)
        db.execSQL(LocalStoreBase.STUDY_ITEMS_TABLE_SQL.replace(LocalStoreBase.SQL_CREATE_TABLE, LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED))
        db.execSQL(LocalStoreBase.LEARNING_REPEATS_TABLE_SQL.replace(LocalStoreBase.SQL_CREATE_TABLE, LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED))
        db.execSQL(LocalStoreBase.REVIEW_LOG_TABLE_SQL.replace(LocalStoreBase.SQL_CREATE_TABLE, LocalStoreBase.SQL_CREATE_TABLE_IF_NEEDED))
        hooks.createStudyTaskLogTable(db)
        hooks.createDashboardIndexes(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_due ON " + LocalStoreBase.TABLE_STUDY_ITEMS + "(state, due_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_learning_repeats_due ON " + LocalStoreBase.TABLE_LEARNING_REPEATS + "(due_at)")
        hooks.createTimelineTables(db)
        hooks.createHistoricalSyncTables(db)
        hooks.createStatsIndexes(db)
        hooks.createStatsCacheTables(db)
    }
}
