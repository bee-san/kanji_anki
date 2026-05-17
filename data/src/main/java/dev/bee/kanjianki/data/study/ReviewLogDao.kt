package dev.bee.kanjianki.data.study

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReviewLogDao {
    @Query("SELECT * FROM review_log WHERE kanji = :kanji ORDER BY reviewed_at DESC, id DESC")
    suspend fun listForKanji(kanji: String): List<ReviewLogEntity>

    @Query("SELECT * FROM review_log WHERE reviewed_at >= :fromMillis ORDER BY reviewed_at ASC, id ASC")
    suspend fun listSince(fromMillis: Long): List<ReviewLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: ReviewLogEntity): Long
}
