package dev.bee.kanjianki.data.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncKanjiSnapshotDao {
    @Query("SELECT * FROM sync_kanji_snapshots WHERE kanji = :kanji ORDER BY finished_at DESC, sync_id DESC")
    suspend fun listForKanji(kanji: String): List<SyncKanjiSnapshotEntity>

    @Query("SELECT * FROM sync_kanji_snapshots WHERE sync_id = :syncId ORDER BY kanji ASC")
    suspend fun listForSync(syncId: Long): List<SyncKanjiSnapshotEntity>

    @Upsert
    suspend fun upsertAll(rows: List<SyncKanjiSnapshotEntity>)
}
