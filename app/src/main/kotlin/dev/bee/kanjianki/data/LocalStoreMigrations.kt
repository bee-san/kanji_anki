package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsSyncModels

internal object LocalStoreMigrations {
    @JvmStatic
    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int, hooks: LocalStoreMigrationHooks) {
        val targetVersion = minOf(newVersion, LocalStoreSchema.DB_VERSION)
        upgradeThroughEight(db, oldVersion, targetVersion, hooks)
        upgradeThroughFifteen(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyOne(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyTwo(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyThree(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyFour(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyFive(db, oldVersion, targetVersion, hooks)
    }

    private fun upgradeThroughEight(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 2)) {
            hooks.createTimelineTables(db)
            hooks.backfillTimelineEvents(db)
        }
        if (shouldRun(oldVersion, targetVersion, 3)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_SOURCE_CARDS, LocalStoreBase.COLUMN_FSRS_STABILITY, "REAL")
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_SOURCE_CARDS, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, "REAL")
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_SOURCE_CARDS, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY, "REAL")
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_KANJI_EXAMPLES, LocalStoreBase.COLUMN_INTERVAL_DAYS, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_KANJI_EXAMPLES, LocalStoreBase.COLUMN_REPS, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_KANJI_EXAMPLES, LocalStoreBase.COLUMN_FSRS_STABILITY, "REAL")
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_KANJI_EXAMPLES, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, "REAL")
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_KANJI_EXAMPLES, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY, "REAL")
        }
        if (shouldRun(oldVersion, targetVersion, 4)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_RECOGNITION_STAGE, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_LAST_FAILED_RECOGNITION_DAY, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_WRITING_REMEDIATION_PENDING, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
        }
        if (shouldRun(oldVersion, targetVersion, 5)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_SUPPRESSED_BY_TASK_TYPE, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_SUPPRESSED_AT, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS, LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_ANSWER_SIGNATURE, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        }
        if (shouldRun(oldVersion, targetVersion, 6)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_KANJI_MEANING_MEMORY, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_FONT_MEANING_MEMORY, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_WORD_READING_MEMORY, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_WRITING_REMEDIATION_MEMORY, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        }
        if (shouldRun(oldVersion, targetVersion, 7)) {
            hooks.rebuildStudyItemsWithAnswerSignatureKey(db)
        }
        if (shouldRun(oldVersion, targetVersion, 8)) {
            db.execSQL(LocalStoreBase.LEARNING_REPEATS_TABLE_SQL)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learning_repeats_due ON " + LocalStoreBase.TABLE_LEARNING_REPEATS + "(due_at)")
        }
    }

    private fun upgradeThroughFifteen(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 9)) {
            hooks.createKanjiInventoryTables(db)
            hooks.backfillKanjiInventory(db, System.currentTimeMillis(), RecordsSyncModels.Settings.kikuDefaults())
        }
        if (shouldRun(oldVersion, targetVersion, 10)) {
            hooks.createSimilarKanjiTables(db)
        }
        if (shouldRun(oldVersion, targetVersion, 11)) {
            hooks.createSimilarKanjiPracticeTables(db)
            hooks.rebuildSimilarKanjiChoiceStates(db, System.currentTimeMillis())
        }
        if (shouldRun(oldVersion, targetVersion, 12)) {
            hooks.createHistoricalSyncTables(db)
            hooks.addRichReviewColumns(db)
            hooks.addHistoricalIdentityColumns(db)
            hooks.backfillLatestHistoricalSync(db)
        }
        if (shouldRun(oldVersion, targetVersion, 13)) {
            hooks.createHistoricalSyncTables(db)
            hooks.addHistoricalIdentityColumns(db)
        }
        if (shouldRun(oldVersion, targetVersion, 14)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_TYPING_MEANING_MEMORY, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        }
        if (shouldRun(oldVersion, targetVersion, 15)) {
            hooks.createStudyTaskLogTable(db)
        }
    }

    private fun upgradeThroughTwentyOne(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 16)) {
            hooks.rebuildStudyItemsForLadderScheduler(db)
        }
        if (shouldRun(oldVersion, targetVersion, 17)) {
            hooks.ensureStatsAggregateStorage(db)
        }
        if (shouldRun(oldVersion, targetVersion, 18)) {
            hooks.repairHistoricalSyncSnapshotsIfPossible(db)
        }
        if (shouldRun(oldVersion, targetVersion, 19)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STUDY_ITEMS, LocalStoreBase.COLUMN_MEANING_KANJI_MEMORY, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        }
        if (shouldRun(oldVersion, targetVersion, 20)) {
            hooks.createImportAuditTables(db)
        }
        if (shouldRun(oldVersion, targetVersion, 21)) {
            hooks.addRichReviewColumns(db)
        }
    }

    private fun upgradeThroughTwentyTwo(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 22)) {
            hooks.createStatsCacheTables(db)
        }
    }

    private fun upgradeThroughTwentyThree(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 23)) {
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_STATS_SCREEN_CACHE, "cache_format_version", LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ONE)
        }
    }

    private fun upgradeThroughTwentyFour(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 24)) {
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_SIMILAR_KANJI_REVIEW_LOG,
                LocalStoreBase.COLUMN_RUNG,
                LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_SIMILAR_KANJI_RUNG,
            )
        }
    }

    private fun upgradeThroughTwentyFive(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 25)) {
            hooks.clearStaleSuppressionFlags(db)
        }
    }

    private fun shouldRun(oldVersion: Int, targetVersion: Int, migrationVersion: Int): Boolean {
        return oldVersion < migrationVersion && targetVersion >= migrationVersion
    }
}
