package dev.bee.kanjianki.data.similar

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SimilarRepairQueueDao {
    @Query("SELECT * FROM similar_kanji_repair_queue WHERE status = :status AND due_at <= :nowMillis ORDER BY due_at ASC, created_at ASC")
    suspend fun listDue(status: String, nowMillis: Long): List<SimilarRepairQueueEntity>

    @Insert
    suspend fun insert(repair: SimilarRepairQueueEntity): Long

    @Update
    suspend fun update(repair: SimilarRepairQueueEntity)
}
