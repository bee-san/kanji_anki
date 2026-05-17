package dev.bee.kanjianki.data.inventory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardRowDao {
    @Query("SELECT * FROM dashboard_rows ORDER BY weakness_score DESC, suspended_example_count DESC, kanji ASC LIMIT :limit")
    fun observeTop(limit: Int): Flow<List<DashboardRowEntity>>

    @Query("SELECT * FROM dashboard_rows WHERE kanji = :kanji")
    suspend fun get(kanji: String): DashboardRowEntity?

    @Upsert
    suspend fun upsertAll(rows: List<DashboardRowEntity>)

    @Query("DELETE FROM dashboard_rows")
    suspend fun deleteAll()
}
