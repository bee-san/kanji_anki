package dev.bee.kanjianki.data

internal object StudySchedulerMigration {
    private const val DROP_STUDY_DUE_INDEX = "DROP INDEX IF EXISTS idx_study_due"
    private const val CREATE_STUDY_DUE_INDEX = "CREATE INDEX IF NOT EXISTS idx_study_due ON "
    private const val DELETE_FROM = "DELETE FROM "
    private const val OLD_STUDY_ITEMS_TABLE = "study_items_ladder_migration_old"

    @JvmStatic
    fun rebuildLadderStudyItemsSql(
        studyItemsTable: String,
        createStudyItemsSql: String,
        learningRepeatsTable: String,
        similarChoiceStateTable: String,
        similarRepairQueueTable: String,
    ): List<String> {
        return listOf(
            DROP_STUDY_DUE_INDEX,
            "ALTER TABLE $studyItemsTable RENAME TO $OLD_STUDY_ITEMS_TABLE",
            createStudyItemsSql,
            preservedStudyItemsInsertSql(studyItemsTable),
            "DROP TABLE $OLD_STUDY_ITEMS_TABLE",
            "$CREATE_STUDY_DUE_INDEX$studyItemsTable(state, due_at)",
            "$DELETE_FROM$learningRepeatsTable",
            "$DELETE_FROM$similarChoiceStateTable",
            "$DELETE_FROM$similarRepairQueueTable",
        )
    }

    private fun preservedStudyItemsInsertSql(studyItemsTable: String): String {
        val columns = listOf(
            "kanji",
            "state",
            "due_at",
            "stability",
            "difficulty",
            "total_reviews",
            "lapses",
            "learning_step",
            "writing_level",
            "recognition_stage",
            "consecutive_failed_recognition_days",
            "last_failed_recognition_day",
            "writing_remediation_pending",
            "suppressed_by_task_type",
            "suppressed_at",
            "mature_interval_days",
            "answer_signature",
            "typing_meaning_memory",
            "meaning_kanji_memory",
            "kanji_meaning_memory",
            "font_meaning_memory",
            "word_reading_memory",
            "writing_remediation_memory",
            "rung",
            "phase",
            "real_pass_streak",
            "real_again_streak",
            "last_real_review_due_at",
            "similar_kanji_memory",
            "active_token",
            "created_at",
        )
        val selected = listOf(
            "kanji",
            "state",
            "due_at",
            "stability",
            "difficulty",
            "total_reviews",
            "lapses",
            "learning_step",
            "writing_level",
            "recognition_stage",
            "consecutive_failed_recognition_days",
            "last_failed_recognition_day",
            "writing_remediation_pending",
            "suppressed_by_task_type",
            "suppressed_at",
            "mature_interval_days",
            "answer_signature",
            "typing_meaning_memory",
            "''",
            "kanji_meaning_memory",
            "font_meaning_memory",
            "word_reading_memory",
            "writing_remediation_memory",
            legacyRungSql(),
            legacyPhaseSql(),
            "0",
            "0",
            "0",
            "''",
            "active_token",
            "created_at",
        )
        return "INSERT OR REPLACE INTO $studyItemsTable (${columns.joinToString(", ")}) " +
            "SELECT ${selected.joinToString(", ")} FROM $OLD_STUDY_ITEMS_TABLE"
    }

    private fun legacyRungSql(): String = "CASE " +
        "WHEN writing_remediation_pending = 1 THEN 'write_kanji' " +
        "WHEN recognition_stage < 0 THEN 'type_meaning' " +
        "WHEN recognition_stage = 1 THEN 'font_meaning' " +
        "WHEN recognition_stage >= 2 THEN 'word_reading' " +
        "ELSE 'kanji_meaning' END"

    private fun legacyPhaseSql(): String = "CASE " +
        "WHEN state = 'review' THEN 'review' " +
        "ELSE 'new_learning' END"
}
