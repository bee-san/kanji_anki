package dev.bee.kanjianki.data

internal object StudySchedulerMigration {
    private const val DROP_STUDY_DUE_INDEX = "DROP INDEX IF EXISTS idx_study_due"
    private const val CREATE_STUDY_DUE_INDEX = "CREATE INDEX IF NOT EXISTS idx_study_due ON "
    private const val DELETE_FROM = "DELETE FROM "

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
            "DROP TABLE IF EXISTS $studyItemsTable",
            createStudyItemsSql,
            "$CREATE_STUDY_DUE_INDEX$studyItemsTable(state, due_at)",
            "$DELETE_FROM$learningRepeatsTable",
            "$DELETE_FROM$similarChoiceStateTable",
            "$DELETE_FROM$similarRepairQueueTable",
        )
    }
}
