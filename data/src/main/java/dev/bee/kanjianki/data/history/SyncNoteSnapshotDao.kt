package dev.bee.kanjianki.data.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncNoteSnapshotDao {
    @Query("SELECT * FROM sync_note_snapshots WHERE sync_id = :syncId ORDER BY note_id ASC")
    suspend fun listForSync(syncId: Long): List<SyncNoteSnapshotEntity>

    @Upsert
    suspend fun upsertAll(notes: List<SyncNoteSnapshotEntity>)
}
