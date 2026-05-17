package dev.bee.kanjianki.data.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncCardSnapshotDao {
    @Query("SELECT * FROM sync_card_snapshots WHERE sync_id = :syncId ORDER BY card_id ASC")
    suspend fun listForSync(syncId: Long): List<SyncCardSnapshotEntity>

    @Upsert
    suspend fun upsertAll(cards: List<SyncCardSnapshotEntity>)
}
