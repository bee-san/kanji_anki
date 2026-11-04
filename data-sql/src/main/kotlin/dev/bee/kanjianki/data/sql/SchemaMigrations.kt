package dev.bee.kanjianki.data.sql

internal object SchemaMigrations {
    fun upgrade(
        session: SqlSession,
        oldVersion: Int,
        targetVersion: Int,
        context: MigrationContext,
    ) {
        require(oldVersion in 1 until CanonicalSchema.VERSION) {
            "Unsupported source schema version $oldVersion"
        }
        require(targetVersion in oldVersion..CanonicalSchema.VERSION) {
            "Unsupported target schema version $targetVersion"
        }
        for (version in (oldVersion + 1)..targetVersion) {
            when (version) {
                2 -> migrateV2(session, context)
                3 -> migrateV3(session)
                4 -> migrateV4(session)
                5 -> migrateV5(session)
                6 -> migrateV6(session)
                7 -> migrateV7(session)
                8 -> migrateV8(session)
                9 -> migrateV9(session, context)
                10 -> migrateV10(session)
                11 -> migrateV11(session, context)
                12 -> migrateV12(session, context)
                13 -> migrateV13(session)
                14 -> addColumn(session, STUDY_ITEMS, "typing_meaning_memory", TEXT_EMPTY)
                15 -> createStudyTaskLog(session)
                16 -> migrateV16(session)
                17 -> migrateV17(session)
                18 -> MigrationBackfills.latestHistoricalSync(session, context)
                19 -> addColumn(session, STUDY_ITEMS, "meaning_kanji_memory", TEXT_EMPTY)
                20 -> createImportAuditTables(session)
                21 -> addRichReviewColumns(session)
                22 -> createStatsCacheTables(session, context)
                23 -> addColumn(session, STATS_SCREEN_CACHE, "cache_format_version", INTEGER_ONE)
                24 -> addColumn(
                    session,
                    SIMILAR_REVIEW_LOG,
                    "rung",
                    "TEXT NOT NULL DEFAULT 'similar_kanji'",
                )
                25 -> session.execute(
                    "UPDATE $STUDY_ITEMS SET suppressed_by_task_type = '', suppressed_at = 0 " +
                        "WHERE suppressed_by_task_type <> ''",
                )
                26 -> createKanjiReadingTables(session)
                27 -> addColumn(session, STUDY_ITEMS, "kanji_reading_memory", TEXT_EMPTY)
                28 -> addColumn(session, STUDY_ITEMS, "reading_kanji_memory", TEXT_EMPTY)
                29 -> addColumn(session, STUDY_ITEMS, "sentence_reading_memory", TEXT_EMPTY)
                30 -> createDashboardIndexes(session)
                31 -> migrateV31(session)
                32 -> createTables(session, "kanji_mnemonic_notes")
                33 -> createMissingKanjiTables(session)
                34 -> migrateV34(session, context)
            }
        }
    }

    fun recordDowngrade(
        session: SqlSession,
        oldVersion: Int,
        context: MigrationContext,
    ) {
        session.executePrepared(
            "INSERT OR REPLACE INTO settings(key, value, updated_at) VALUES (?, ?, ?)",
        ) {
            bindText(1, context.defaults.downgradeSettingKey)
            bindText(2, oldVersion.toString())
            bindLong(3, context.clock.nowMillis())
        }
    }

    private fun migrateV2(session: SqlSession, context: MigrationContext) {
        createTimelineTables(session)
        MigrationBackfills.timeline(session, context)
    }

    private fun migrateV3(session: SqlSession) {
        addColumn(session, SOURCE_CARDS, "fsrs_stability", "REAL")
        addColumn(session, SOURCE_CARDS, "fsrs_difficulty", "REAL")
        addColumn(session, SOURCE_CARDS, "fsrs_retrievability", "REAL")
        addColumn(session, KANJI_EXAMPLES, "interval_days", INTEGER_ZERO)
        addColumn(session, KANJI_EXAMPLES, "reps", INTEGER_ZERO)
        addColumn(session, KANJI_EXAMPLES, "fsrs_stability", "REAL")
        addColumn(session, KANJI_EXAMPLES, "fsrs_difficulty", "REAL")
        addColumn(session, KANJI_EXAMPLES, "fsrs_retrievability", "REAL")
    }

    private fun migrateV4(session: SqlSession) {
        addColumn(session, STUDY_ITEMS, "recognition_stage", INTEGER_ZERO)
        addColumn(session, STUDY_ITEMS, "consecutive_failed_recognition_days", INTEGER_ZERO)
        addColumn(session, STUDY_ITEMS, "last_failed_recognition_day", INTEGER_ZERO)
        addColumn(session, STUDY_ITEMS, "writing_remediation_pending", INTEGER_ZERO)
    }

    private fun migrateV5(session: SqlSession) {
        addColumn(session, STUDY_ITEMS, "suppressed_by_task_type", TEXT_EMPTY)
        addColumn(session, STUDY_ITEMS, "suppressed_at", INTEGER_ZERO)
        addColumn(session, STUDY_ITEMS, "mature_interval_days", INTEGER_ZERO)
        addColumn(session, STUDY_ITEMS, "answer_signature", TEXT_EMPTY)
    }

    private fun migrateV6(session: SqlSession) {
        addColumn(session, STUDY_ITEMS, "kanji_meaning_memory", TEXT_EMPTY)
        addColumn(session, STUDY_ITEMS, "font_meaning_memory", TEXT_EMPTY)
        addColumn(session, STUDY_ITEMS, "word_reading_memory", TEXT_EMPTY)
        addColumn(session, STUDY_ITEMS, "writing_remediation_memory", TEXT_EMPTY)
    }

    private fun migrateV7(session: SqlSession) {
        session.execute("DROP INDEX IF EXISTS idx_study_due")
        session.execute("ALTER TABLE $STUDY_ITEMS RENAME TO study_items_old")
        session.execute(CanonicalSchema.createTable(STUDY_ITEMS))
        session.execute(
            """
            INSERT OR REPLACE INTO study_items(
                kanji, state, due_at, stability, difficulty, total_reviews,
                lapses, learning_step, writing_level, recognition_stage,
                consecutive_failed_recognition_days, last_failed_recognition_day,
                writing_remediation_pending, suppressed_by_task_type, suppressed_at,
                mature_interval_days, answer_signature, kanji_meaning_memory,
                font_meaning_memory, word_reading_memory, writing_remediation_memory,
                active_token, created_at
            )
            SELECT
                kanji, state, due_at, stability, difficulty, total_reviews,
                lapses, learning_step, writing_level, recognition_stage,
                consecutive_failed_recognition_days, last_failed_recognition_day,
                writing_remediation_pending, suppressed_by_task_type, suppressed_at,
                mature_interval_days, COALESCE(answer_signature, ''), kanji_meaning_memory,
                font_meaning_memory, word_reading_memory, writing_remediation_memory,
                active_token, created_at
            FROM study_items_old
            """.trimIndent(),
        )
        session.execute("DROP TABLE study_items_old")
        createIndexes(session, "idx_study_due")
    }

    private fun migrateV8(session: SqlSession) {
        createTables(session, LEARNING_REPEATS)
        createIndexes(session, "idx_learning_repeats_due")
    }

    private fun migrateV9(session: SqlSession, context: MigrationContext) {
        createKanjiInventoryTables(session)
        MigrationBackfills.inventory(session, context)
    }

    private fun migrateV10(session: SqlSession) {
        createTables(session, "similar_kanji_pairs")
        createIndexes(
            session,
            "idx_similar_kanji_pairs_a",
            "idx_similar_kanji_pairs_b",
        )
    }

    private fun migrateV11(session: SqlSession, context: MigrationContext) {
        createSimilarKanjiPracticeTables(session)
        MigrationBackfills.similarChoiceStates(session, context)
    }

    private fun migrateV12(session: SqlSession, context: MigrationContext) {
        createHistoricalSyncTables(session)
        addRichReviewColumns(session)
        addHistoricalIdentityColumns(session)
        MigrationBackfills.latestHistoricalSync(session, context)
    }

    private fun migrateV13(session: SqlSession) {
        createHistoricalSyncTables(session)
        addHistoricalIdentityColumns(session)
    }

    private fun migrateV16(session: SqlSession) {
        session.execute("DROP INDEX IF EXISTS idx_study_due")
        session.execute("ALTER TABLE $STUDY_ITEMS RENAME TO study_items_ladder_migration_old")
        session.execute(CanonicalSchema.createTable(STUDY_ITEMS))
        session.execute(
            """
            INSERT OR REPLACE INTO study_items(
                kanji, state, due_at, stability, difficulty, total_reviews, lapses,
                learning_step, writing_level, recognition_stage,
                consecutive_failed_recognition_days, last_failed_recognition_day,
                writing_remediation_pending, suppressed_by_task_type, suppressed_at,
                mature_interval_days, answer_signature, typing_meaning_memory,
                meaning_kanji_memory, kanji_meaning_memory, font_meaning_memory,
                word_reading_memory, writing_remediation_memory, rung, phase,
                real_pass_streak, real_again_streak, last_real_review_due_at,
                similar_kanji_memory, active_token, created_at
            )
            SELECT
                kanji, state, due_at, stability, difficulty, total_reviews, lapses,
                learning_step, writing_level, recognition_stage,
                consecutive_failed_recognition_days, last_failed_recognition_day,
                writing_remediation_pending, suppressed_by_task_type, suppressed_at,
                mature_interval_days, answer_signature, typing_meaning_memory, '',
                kanji_meaning_memory, font_meaning_memory, word_reading_memory,
                writing_remediation_memory,
                CASE
                    WHEN writing_remediation_pending = 1 THEN 'write_kanji'
                    WHEN recognition_stage < 0 THEN 'type_meaning'
                    WHEN recognition_stage = 1 THEN 'font_meaning'
                    WHEN recognition_stage >= 2 THEN 'word_reading'
                    ELSE 'kanji_meaning'
                END,
                CASE WHEN state = 'review' THEN 'review' ELSE 'new_learning' END,
                0, 0, 0, '', active_token, created_at
            FROM study_items_ladder_migration_old
            """.trimIndent(),
        )
        session.execute("DROP TABLE study_items_ladder_migration_old")
        createIndexes(session, "idx_study_due")
        session.execute("DELETE FROM $LEARNING_REPEATS")
        session.execute("DELETE FROM $SIMILAR_CHOICE_STATE")
        session.execute("DELETE FROM $SIMILAR_REPAIR_QUEUE")
    }

    private fun migrateV17(session: SqlSession) {
        createTables(session, REVIEW_LOG)
        addRichReviewColumns(session)
        addColumn(session, REVIEW_LOG, "review_day_start", INTEGER_ZERO)
        createStudyTaskLog(session)
        createTimelineTables(session)
        createHistoricalSyncTables(session)
        createStatsIndexes(session)
    }

    private fun migrateV31(session: SqlSession) {
        addColumn(session, STUDY_ITEMS, "scheduler_revision", INTEGER_ZERO)
        addColumn(session, STUDY_ITEMS, "routing_version", INTEGER_ONE)
        addColumn(session, STUDY_ITEMS, "adaptive_route_state_json", TEXT_EMPTY)
        for (column in listOf(
            "core_skill",
            "failure_cause",
            "evidence_source",
            "selected_answer",
            "correct_answer",
            "answer_evidence_json",
        )) {
            addColumn(session, REVIEW_LOG, column, TEXT_EMPTY)
        }
    }

    private fun migrateV34(session: SqlSession, context: MigrationContext) {
        session.executePrepared(
            """
            INSERT OR REPLACE INTO settings(key, value, updated_at)
            SELECT ?, ?, 0
            WHERE
                EXISTS (
                    SELECT 1 FROM sync_runs WHERE status = ? LIMIT 1
                )
                AND (
                    EXISTS (SELECT 1 FROM source_notes LIMIT 1)
                    OR EXISTS (SELECT 1 FROM source_cards LIMIT 1)
                )
            """.trimIndent(),
        ) {
            bindText(1, context.defaults.androidLegacyMigrationKey)
            bindText(2, context.defaults.androidLegacyMigrationEligibleValue)
            bindText(3, context.defaults.successfulSyncStatus)
        }
    }

    private fun createTimelineTables(session: SqlSession) {
        createTables(session, "kanji_timeline_events")
        createIndexes(session, "idx_timeline_dedupe", "idx_timeline_kanji_time")
    }

    private fun createKanjiInventoryTables(session: SqlSession) {
        createTables(session, "kanji_inventory", "local_kanji_suspensions")
        createIndexes(session, "idx_kanji_inventory_search")
    }

    private fun createSimilarKanjiPracticeTables(session: SqlSession) {
        createTables(
            session,
            SIMILAR_CHOICE_STATE,
            SIMILAR_REPAIR_QUEUE,
            SIMILAR_REVIEW_LOG,
        )
        createIndexes(
            session,
            "idx_similar_choice_due",
            "idx_similar_repair_due",
            "idx_similar_review_log_target",
        )
    }

    private fun createHistoricalSyncTables(session: SqlSession) {
        createTables(
            session,
            "sync_card_snapshots",
            "sync_note_snapshots",
            "sync_kanji_snapshots",
        )
        createIndexes(
            session,
            "idx_sync_card_snapshots_sync_card",
            "idx_sync_card_snapshots_note",
            "idx_sync_note_snapshots_kanji",
            "idx_sync_kanji_snapshots_kanji_sync",
        )
    }

    private fun createImportAuditTables(session: SqlSession) {
        createTables(session, "import_rule_audits", "import_decisions")
        createIndexes(session, "idx_import_decisions_kanji_sync")
    }

    private fun createStatsCacheTables(
        session: SqlSession,
        context: MigrationContext,
    ) {
        createTables(session, "stats_cache_state", STATS_SCREEN_CACHE)
        session.executePrepared(
            "INSERT OR IGNORE INTO stats_cache_state(key, value) VALUES (?, ?)",
        ) {
            bindText(1, context.defaults.statsSourceVersionKey)
            bindLong(2, context.defaults.statsSourceVersion)
        }
    }

    private fun createKanjiReadingTables(session: SqlSession) {
        createTables(session, "kanji_reading_usage", "kanji_reading_pool")
        createIndexes(
            session,
            "idx_kanji_reading_usage_kanji",
            "idx_kanji_reading_usage_reading",
            "idx_kanji_reading_pool_reading",
        )
    }

    private fun createMissingKanjiTables(session: SqlSession) {
        createTables(
            session,
            "anki_kanji_inventory_scans",
            "anki_kanji_inventory",
            "manual_kanji_sources",
            "missing_kanji_exports",
        )
        createIndexes(
            session,
            "idx_anki_kanji_inventory_scans_completed",
            "idx_anki_kanji_inventory_scan",
            "idx_manual_kanji_sources_active_rank",
            "idx_missing_kanji_exports_destination",
        )
    }

    private fun createStudyTaskLog(session: SqlSession) {
        createTables(session, "study_task_log")
        createIndexes(session, "idx_study_task_log_answered")
    }

    private fun createStatsIndexes(session: SqlSession) {
        createIndexes(
            session,
            "idx_review_log_reviewed_at",
            "idx_review_log_day_reviewed",
            "idx_review_log_kanji_reviewed",
            "idx_review_log_rating_reviewed",
            "idx_study_items_ladder_stats",
            "idx_sync_kanji_snapshots_kanji_finished",
        )
    }

    private fun createDashboardIndexes(session: SqlSession) {
        createIndexes(
            session,
            "idx_dashboard_rows_priority",
            "idx_kanji_examples_ordered",
        )
        session.execute("DROP INDEX IF EXISTS idx_examples_kanji")
    }

    private fun addRichReviewColumns(session: SqlSession) {
        for (column in listOf(
            "task_type",
            "answer_signature",
            "prompt",
            "memory_before",
            "memory_after",
            "scheduler_state_before_json",
            "scheduler_state_after_json",
        )) {
            addColumn(session, REVIEW_LOG, column, TEXT_EMPTY)
        }
        addColumn(session, REVIEW_LOG, "hints_used", INTEGER_ZERO)
        addColumn(session, REVIEW_LOG, "writing_clean", INTEGER_ZERO)
    }

    private fun addHistoricalIdentityColumns(session: SqlSession) {
        addColumn(session, "sync_card_snapshots", "deck_id", TEXT_EMPTY)
        addColumn(session, "sync_card_snapshots", "model_id", INTEGER_ZERO)
        addColumn(session, "sync_note_snapshots", "model_id", INTEGER_ZERO)
        addColumn(session, "sync_note_snapshots", "deck_ids", TEXT_EMPTY)
        session.execute("UPDATE sync_card_snapshots SET deck_id = deck_name WHERE deck_id = ''")
        session.execute("UPDATE sync_note_snapshots SET deck_ids = deck_names WHERE deck_ids = ''")
    }

    private fun addColumn(
        session: SqlSession,
        table: String,
        column: String,
        declaration: String,
    ) {
        if (!columnExists(session, table, column)) {
            session.execute("ALTER TABLE $table ADD COLUMN $column $declaration")
        }
    }

    private fun columnExists(
        session: SqlSession,
        table: String,
        column: String,
    ): Boolean {
        val statement = session.prepare("PRAGMA table_info($table)")
        try {
            val rows = statement.query()
            try {
                while (rows.next()) {
                    if (rows.row.text(1).equals(column, ignoreCase = true)) {
                        return true
                    }
                }
            } finally {
                rows.close()
            }
        } finally {
            statement.close()
        }
        return false
    }

    private fun createTables(session: SqlSession, vararg tables: String) {
        tables.forEach { session.execute(CanonicalSchema.createTable(it)) }
    }

    private fun createIndexes(session: SqlSession, vararg indexes: String) {
        indexes.forEach { session.execute(CanonicalSchema.createIndex(it)) }
    }

    private inline fun SqlSession.executePrepared(
        sql: String,
        bind: SqlStatement.() -> Unit,
    ) {
        val statement = prepare(sql)
        try {
            statement.bind()
            statement.execute()
        } finally {
            statement.close()
        }
    }

    private const val SOURCE_CARDS = "source_cards"
    private const val KANJI_EXAMPLES = "kanji_examples"
    private const val STUDY_ITEMS = "study_items"
    private const val REVIEW_LOG = "review_log"
    private const val LEARNING_REPEATS = "learning_repeats"
    private const val SIMILAR_CHOICE_STATE = "similar_kanji_choice_state"
    private const val SIMILAR_REPAIR_QUEUE = "similar_kanji_repair_queue"
    private const val SIMILAR_REVIEW_LOG = "similar_kanji_review_log"
    private const val STATS_SCREEN_CACHE = "stats_screen_cache"
    private const val TEXT_EMPTY = "TEXT NOT NULL DEFAULT ''"
    private const val INTEGER_ZERO = "INTEGER NOT NULL DEFAULT 0"
    private const val INTEGER_ONE = "INTEGER NOT NULL DEFAULT 1"
}
