package dev.bee.kanjianki.data.study

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LearningRepeatDao {
    @Query("SELECT * FROM learning_repeats WHERE due_at <= :nowMillis ORDER BY due_at ASC, kanji ASC")
    suspend fun listDue(nowMillis: Long): List<LearningRepeatEntity>

    @Query("SELECT * FROM learning_repeats WHERE kanji = :kanji AND answer_signature = :answerSignature")
    suspend fun listForStudyItem(kanji: String, answerSignature: String): List<LearningRepeatEntity>

    @Upsert
    suspend fun upsert(repeat: LearningRepeatEntity)

    @Query("DELETE FROM learning_repeats WHERE kanji = :kanji AND answer_signature = :answerSignature AND task_type = :taskType")
    suspend fun delete(kanji: String, answerSignature: String, taskType: String)

    @Query("DELETE FROM learning_repeats WHERE kanji = :kanji")
    suspend fun deleteForKanji(kanji: String)
}
