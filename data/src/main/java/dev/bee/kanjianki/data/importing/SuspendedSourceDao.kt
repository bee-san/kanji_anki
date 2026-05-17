package dev.bee.kanjianki.data.importing

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SuspendedSourceDao {
    @Query("SELECT * FROM suspended_sources WHERE kanji = :kanji ORDER BY card_id ASC")
    suspend fun listForKanji(kanji: String): List<SuspendedSourceEntity>

    @Query("SELECT * FROM suspended_sources WHERE sync_id = :syncId ORDER BY kanji ASC, card_id ASC")
    suspend fun listForSync(syncId: Long): List<SuspendedSourceEntity>

    @Upsert
    suspend fun upsertAll(sources: List<SuspendedSourceEntity>)

    @Query("DELETE FROM suspended_sources WHERE kanji = :kanji")
    suspend fun deleteForKanji(kanji: String)
}
