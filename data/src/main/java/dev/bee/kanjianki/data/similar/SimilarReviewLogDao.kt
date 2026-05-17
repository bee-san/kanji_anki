package dev.bee.kanjianki.data.similar

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SimilarReviewLogDao {
    @Query("SELECT * FROM similar_kanji_review_log WHERE target_kanji = :kanji ORDER BY reviewed_at DESC, id DESC")
    suspend fun listForKanji(kanji: String): List<SimilarReviewLogEntity>

    @Insert
    suspend fun insert(log: SimilarReviewLogEntity): Long
}
