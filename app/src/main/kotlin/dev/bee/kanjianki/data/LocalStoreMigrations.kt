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
        upgradeThroughTwentySix(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentySeven(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyEight(db, oldVersion, targetVersion, hooks)
        upgradeThroughTwentyNine(db, oldVersion, targetVersion, hooks)
        upgradeThroughThirty(db, oldVersion, targetVersion, hooks)
        upgradeThroughThirtyOne(db, oldVersion, targetVersion, hooks)
        upgradeThroughThirtyTwo(db, oldVersion, targetVersion, hooks)
        upgradeThroughThirtyThree(db, oldVersion, targetVersion, hooks)
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

    private fun upgradeThroughTwentySix(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 26)) {
            // Goal 77: reading-usage content tables. Created empty; the next
            // sync rebuilds them from the analyzed examples via the aligner.
            hooks.createKanjiReadingTables(db)
        }
    }

    private fun upgradeThroughTwentySeven(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 27)) {
            // Goal 78: per-rung memory column for the kanji_reading rung.
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_STUDY_ITEMS,
                LocalStoreBase.COLUMN_KANJI_READING_MEMORY,
                LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY,
            )
        }
    }

    private fun upgradeThroughTwentyEight(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 28)) {
            // Goal 79: per-rung memory column for the reading_kanji rung.
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_STUDY_ITEMS,
                LocalStoreBase.COLUMN_READING_KANJI_MEMORY,
                LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY,
            )
        }
    }

    private fun upgradeThroughTwentyNine(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 29)) {
            // Goal 80: per-rung memory column for the sentence_reading rung.
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_STUDY_ITEMS,
                LocalStoreBase.COLUMN_SENTENCE_READING_MEMORY,
                LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY,
            )
        }
    }

    private fun upgradeThroughThirty(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 30)) {
            // Keep cold dashboard reads bounded: one ordering index for the 120 headers,
            // then one ordered index lookup capped at eight examples for each selected kanji.
            hooks.createDashboardIndexes(db)
        }
    }

    private fun upgradeThroughThirtyOne(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 31)) {
            // Additive integrity/adaptive-routing metadata. Existing scheduler
            // columns and wire values stay intact so v30 backups and downgrade
            // tooling can continue to read the legacy state.
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_STUDY_ITEMS,
                LocalStoreBase.COLUMN_SCHEDULER_REVISION,
                LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ZERO,
            )
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_STUDY_ITEMS,
                LocalStoreBase.COLUMN_ROUTING_VERSION,
                LocalStoreBase.SQL_INTEGER_NOT_NULL_DEFAULT_ONE,
            )
            hooks.addNullableColumn(
                db,
                LocalStoreBase.TABLE_STUDY_ITEMS,
                LocalStoreBase.COLUMN_ADAPTIVE_ROUTE_STATE_JSON,
                LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY,
            )
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_REVIEW_LOG, LocalStoreBase.COLUMN_CORE_SKILL, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_REVIEW_LOG, LocalStoreBase.COLUMN_FAILURE_CAUSE, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_REVIEW_LOG, LocalStoreBase.COLUMN_EVIDENCE_SOURCE, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_REVIEW_LOG, LocalStoreBase.COLUMN_SELECTED_ANSWER, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_REVIEW_LOG, LocalStoreBase.COLUMN_CORRECT_ANSWER, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
            hooks.addNullableColumn(db, LocalStoreBase.TABLE_REVIEW_LOG, LocalStoreBase.COLUMN_ANSWER_EVIDENCE_JSON, LocalStoreBase.SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        }
    }

    private fun upgradeThroughThirtyTwo(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 32)) {
            // User-authored mnemonic notes live outside the sync mirror and
            // inventory tables so a routine inventory rebuild cannot erase them.
            hooks.createKanjiMnemonicNotesTable(db)
        }
    }

    private fun upgradeThroughThirtyThree(
        db: SQLiteDatabase,
        oldVersion: Int,
        targetVersion: Int,
        hooks: LocalStoreMigrationHooks,
    ) {
        if (shouldRun(oldVersion, targetVersion, 33)) {
            // Collection-wide Missing Kanji state is independent of the
            // configured-model sync mirror and stores no raw note fields.
            hooks.createMissingKanjiTables(db)
        }
    }

    private fun shouldRun(oldVersion: Int, targetVersion: Int, migrationVersion: Int): Boolean {
        return oldVersion < migrationVersion && targetVersion >= migrationVersion
    }
}
