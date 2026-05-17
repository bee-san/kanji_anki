package dev.bee.kanjianki.data.study

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyItemDao {
    @Query("SELECT * FROM study_items WHERE kanji = :kanji AND answer_signature = :answerSignature")
    fun observe(kanji: String, answerSignature: String): Flow<StudyItemEntity?>

    @Query("SELECT * FROM study_items WHERE state = :state ORDER BY due_at ASC, kanji ASC")
    suspend fun listByState(state: String): List<StudyItemEntity>

    @Query("SELECT * FROM study_items WHERE state IN (:states) ORDER BY due_at ASC, kanji ASC")
    suspend fun listByStates(states: List<String>): List<StudyItemEntity>

    @Query("SELECT COUNT(*) FROM study_items WHERE state = :state AND due_at <= :nowMillis")
    suspend fun dueCount(state: String, nowMillis: Long): Int

    @Upsert
    suspend fun upsert(item: StudyItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<StudyItemEntity>)
}
