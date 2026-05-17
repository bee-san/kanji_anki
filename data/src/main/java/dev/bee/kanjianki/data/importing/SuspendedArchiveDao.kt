package dev.bee.kanjianki.data.importing

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SuspendedArchiveDao {
    @Query("SELECT * FROM suspended_archive WHERE card_id = :cardId")
    fun observe(cardId: Long): Flow<SuspendedArchiveEntity?>

    @Query("SELECT * FROM suspended_archive WHERE card_id = :cardId")
    suspend fun get(cardId: Long): SuspendedArchiveEntity?

    @Query("SELECT * FROM suspended_archive WHERE restored_at IS NULL ORDER BY archived_at DESC, card_id")
    suspend fun listActive(): List<SuspendedArchiveEntity>

    @Upsert
    suspend fun upsert(entry: SuspendedArchiveEntity)

    @Upsert
    suspend fun upsertAll(entries: List<SuspendedArchiveEntity>)
}
