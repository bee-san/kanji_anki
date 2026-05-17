package dev.bee.kanjianki.data.inventory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KanjiInventoryDao {
    @Query("SELECT * FROM kanji_inventory ORDER BY kanji ASC")
    fun observeAll(): Flow<List<KanjiInventoryEntity>>

    @Query("SELECT * FROM kanji_inventory WHERE kanji = :kanji")
    suspend fun get(kanji: String): KanjiInventoryEntity?

    @Query("SELECT * FROM kanji_inventory ORDER BY kanji ASC")
    suspend fun listAll(): List<KanjiInventoryEntity>

    @Query("SELECT * FROM kanji_inventory ORDER BY kanji ASC LIMIT :limit")
    suspend fun listLimited(limit: Int): List<KanjiInventoryEntity>

    @Query("SELECT * FROM kanji_inventory WHERE search_text LIKE '%' || :query || '%' ORDER BY kanji ASC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<KanjiInventoryEntity>

    @Upsert
    suspend fun upsertAll(items: List<KanjiInventoryEntity>)
}
