package dev.bee.kanjianki.data;

import android.database.sqlite.SQLiteDatabase;

final class StudySchedulerMigration {
    private static final String DROP_STUDY_DUE_INDEX = "DROP INDEX IF EXISTS idx_study_due";
    private static final String CREATE_STUDY_DUE_INDEX = "CREATE INDEX IF NOT EXISTS idx_study_due ON ";

    private StudySchedulerMigration() {
    }

    static void rebuildLadderStudyItems(
            SQLiteDatabase db,
            String studyItemsTable,
            String createStudyItemsSql,
            String learningRepeatsTable,
            String similarChoiceStateTable,
            String similarRepairQueueTable
    ) {
        db.execSQL(DROP_STUDY_DUE_INDEX);
        db.execSQL("DROP TABLE IF EXISTS " + studyItemsTable);
        db.execSQL(createStudyItemsSql);
        db.execSQL(CREATE_STUDY_DUE_INDEX + studyItemsTable + "(state, due_at)");
        clearLegacySchedulerQueues(db, learningRepeatsTable, similarChoiceStateTable, similarRepairQueueTable);
    }

    private static void clearLegacySchedulerQueues(
            SQLiteDatabase db,
            String learningRepeatsTable,
            String similarChoiceStateTable,
            String similarRepairQueueTable
    ) {
        db.execSQL("DELETE FROM " + learningRepeatsTable);
        db.execSQL("DELETE FROM " + similarChoiceStateTable);
        db.execSQL("DELETE FROM " + similarRepairQueueTable);
    }
}
