package dev.bee.kanjianki.data.importing

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ImportDecisionDao {
    @Query("SELECT * FROM import_decisions WHERE sync_id = :syncId ORDER BY kanji ASC")
    suspend fun listForSync(syncId: Long): List<ImportDecisionEntity>

    @Query("SELECT * FROM import_decisions WHERE kanji = :kanji ORDER BY sync_id DESC")
    suspend fun listForKanji(kanji: String): List<ImportDecisionEntity>

    @Upsert
    suspend fun upsertAll(decisions: List<ImportDecisionEntity>)
}
