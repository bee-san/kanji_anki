package dev.bee.kanjianki.data.importing

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SuspendedImportDao {
    @Query("SELECT * FROM suspended_imports WHERE kanji = :kanji")
    fun observe(kanji: String): Flow<SuspendedImportEntity?>

    @Query("SELECT * FROM suspended_imports WHERE kanji = :kanji")
    suspend fun get(kanji: String): SuspendedImportEntity?

    @Query("SELECT * FROM suspended_imports ORDER BY jiten_rank ASC, kanji ASC")
    suspend fun listRanked(): List<SuspendedImportEntity>

    @Upsert
    suspend fun upsert(entry: SuspendedImportEntity)

    @Upsert
    suspend fun upsertAll(entries: List<SuspendedImportEntity>)
}
