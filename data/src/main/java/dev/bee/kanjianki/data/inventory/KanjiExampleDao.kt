package dev.bee.kanjianki.data.inventory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface KanjiExampleDao {
    @Query("SELECT * FROM kanji_examples WHERE kanji = :kanji ORDER BY source_type DESC, id ASC LIMIT :limit")
    suspend fun listForKanji(kanji: String, limit: Int): List<KanjiExampleEntity>

    @Query("SELECT * FROM kanji_examples WHERE kanji = :kanji ORDER BY source_type ASC, id ASC LIMIT :limit")
    suspend fun listForTimeline(kanji: String, limit: Int): List<KanjiExampleEntity>

    @Upsert
    suspend fun upsertAll(examples: List<KanjiExampleEntity>)

    @Query("DELETE FROM kanji_examples")
    suspend fun deleteAll()
}
