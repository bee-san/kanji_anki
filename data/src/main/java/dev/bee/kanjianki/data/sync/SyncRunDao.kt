package dev.bee.kanjianki.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRunDao {
    @Query("SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<SyncRunEntity?>

    @Query("SELECT * FROM sync_runs WHERE id = :id")
    suspend fun get(id: Long): SyncRunEntity?

    @Query("SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1")
    suspend fun latest(): SyncRunEntity?

    @Insert
    suspend fun insert(syncRun: SyncRunEntity): Long

    @Update
    suspend fun update(syncRun: SyncRunEntity)
}
