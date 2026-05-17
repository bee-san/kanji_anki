package dev.bee.kanjianki.data.inventory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalKanjiSuspensionDao {
    @Query("SELECT * FROM local_kanji_suspensions ORDER BY kanji ASC")
    fun observeAll(): Flow<List<LocalKanjiSuspensionEntity>>

    @Query("SELECT * FROM local_kanji_suspensions ORDER BY kanji ASC")
    suspend fun listAll(): List<LocalKanjiSuspensionEntity>

    @Query("SELECT * FROM local_kanji_suspensions WHERE kanji = :kanji")
    suspend fun get(kanji: String): LocalKanjiSuspensionEntity?

    @Upsert
    suspend fun upsert(suspension: LocalKanjiSuspensionEntity)

    @Query("DELETE FROM local_kanji_suspensions WHERE kanji = :kanji")
    suspend fun delete(kanji: String)
}
