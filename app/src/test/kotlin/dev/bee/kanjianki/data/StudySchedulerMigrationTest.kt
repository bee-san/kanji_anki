package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StudySchedulerMigrationTest {
    @Test
    fun rebuildLadderStudyItemsSqlPreservesRowsAndClearsLegacyQueuesInOrder() {
        val sql = StudySchedulerMigration.rebuildLadderStudyItemsSql(
            "study_items",
            "CREATE TABLE IF NOT EXISTS study_items (kanji TEXT)",
            "learning_repeats",
            "similar_kanji_choice_state",
            "similar_kanji_repair_queue",
        )

        assertEquals(9, sql.size)
        assertEquals("DROP INDEX IF EXISTS idx_study_due", sql[0])
        assertEquals("ALTER TABLE study_items RENAME TO study_items_ladder_migration_old", sql[1])
        assertEquals("CREATE TABLE IF NOT EXISTS study_items (kanji TEXT)", sql[2])
        assertEquals("INSERT OR REPLACE INTO study_items (kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, recognition_stage, consecutive_failed_recognition_days, last_failed_recognition_day, writing_remediation_pending, suppressed_by_task_type, suppressed_at, mature_interval_days, answer_signature, typing_meaning_memory, meaning_kanji_memory, kanji_meaning_memory, font_meaning_memory, word_reading_memory, writing_remediation_memory, rung, phase, real_pass_streak, real_again_streak, last_real_review_due_at, similar_kanji_memory, active_token, created_at) SELECT kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, recognition_stage, consecutive_failed_recognition_days, last_failed_recognition_day, writing_remediation_pending, suppressed_by_task_type, suppressed_at, mature_interval_days, answer_signature, typing_meaning_memory, '', kanji_meaning_memory, font_meaning_memory, word_reading_memory, writing_remediation_memory, CASE WHEN writing_remediation_pending = 1 THEN 'write_kanji' WHEN recognition_stage < 0 THEN 'type_meaning' WHEN recognition_stage = 1 THEN 'font_meaning' WHEN recognition_stage >= 2 THEN 'word_reading' ELSE 'kanji_meaning' END, CASE WHEN state = 'review' THEN 'review' ELSE 'new_learning' END, 0, 0, 0, '', active_token, created_at FROM study_items_ladder_migration_old", sql[3])
        assertEquals("DROP TABLE study_items_ladder_migration_old", sql[4])
        assertEquals("CREATE INDEX IF NOT EXISTS idx_study_due ON study_items(state, due_at)", sql[5])
        assertEquals("DELETE FROM learning_repeats", sql[6])
        assertEquals("DELETE FROM similar_kanji_choice_state", sql[7])
        assertEquals("DELETE FROM similar_kanji_repair_queue", sql[8])
    }
}
