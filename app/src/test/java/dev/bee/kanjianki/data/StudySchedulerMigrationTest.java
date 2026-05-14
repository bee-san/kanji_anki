package dev.bee.kanjianki.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class StudySchedulerMigrationTest {
    @Test
    public void rebuildLadderStudyItemsSqlDropsRecreatesAndClearsLegacyQueuesInOrder() {
        List<String> sql = StudySchedulerMigration.rebuildLadderStudyItemsSql(
                "study_items",
                "CREATE TABLE IF NOT EXISTS study_items (kanji TEXT)",
                "learning_repeats",
                "similar_kanji_choice_state",
                "similar_kanji_repair_queue"
        );

        assertEquals(7, sql.size());
        assertEquals("DROP INDEX IF EXISTS idx_study_due", sql.get(0));
        assertEquals("DROP TABLE IF EXISTS study_items", sql.get(1));
        assertEquals("CREATE TABLE IF NOT EXISTS study_items (kanji TEXT)", sql.get(2));
        assertEquals("CREATE INDEX IF NOT EXISTS idx_study_due ON study_items(state, due_at)", sql.get(3));
        assertEquals("DELETE FROM learning_repeats", sql.get(4));
        assertEquals("DELETE FROM similar_kanji_choice_state", sql.get(5));
        assertEquals("DELETE FROM similar_kanji_repair_queue", sql.get(6));
    }
}
