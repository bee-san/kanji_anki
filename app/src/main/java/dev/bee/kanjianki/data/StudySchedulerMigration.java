package dev.bee.kanjianki.data;

import java.util.Arrays;
import java.util.List;

final class StudySchedulerMigration {
    private static final String DROP_STUDY_DUE_INDEX = "DROP INDEX IF EXISTS idx_study_due";
    private static final String CREATE_STUDY_DUE_INDEX = "CREATE INDEX IF NOT EXISTS idx_study_due ON ";

    private StudySchedulerMigration() {
    }

    static List<String> rebuildLadderStudyItemsSql(
            String studyItemsTable,
            String createStudyItemsSql,
            String learningRepeatsTable,
            String similarChoiceStateTable,
            String similarRepairQueueTable
    ) {
        return Arrays.asList(
                DROP_STUDY_DUE_INDEX,
                "DROP TABLE IF EXISTS " + studyItemsTable,
                createStudyItemsSql,
                CREATE_STUDY_DUE_INDEX + studyItemsTable + "(state, due_at)",
                "DELETE FROM " + learningRepeatsTable,
                "DELETE FROM " + similarChoiceStateTable,
                "DELETE FROM " + similarRepairQueueTable
        );
    }
}
