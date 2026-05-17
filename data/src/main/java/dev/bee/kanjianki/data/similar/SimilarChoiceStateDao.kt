package dev.bee.kanjianki.data.similar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SimilarChoiceStateDao {
    @Query("SELECT * FROM similar_kanji_choice_state WHERE target_kanji = :kanji ORDER BY due_at ASC, choice_signature ASC")
    suspend fun listForKanji(kanji: String): List<SimilarChoiceStateEntity>

    @Query("SELECT * FROM similar_kanji_choice_state WHERE passed_at = 0 AND due_at <= :nowMillis ORDER BY due_at ASC, target_kanji ASC")
    suspend fun listDue(nowMillis: Long): List<SimilarChoiceStateEntity>

    @Upsert
    suspend fun upsert(state: SimilarChoiceStateEntity)
}
