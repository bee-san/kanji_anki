package dev.bee.kanjianki.data.study

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StudyTaskLogDao {
    @Query("SELECT * FROM study_task_log WHERE answered_at >= :fromMillis ORDER BY answered_at ASC, id ASC")
    suspend fun listAnsweredSince(fromMillis: Long): List<StudyTaskLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: StudyTaskLogEntity): Long
}
