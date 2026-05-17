package dev.bee.kanjianki.data.similar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SimilarKanjiPairDao {
    @Query("SELECT * FROM similar_kanji_pairs WHERE kanji_a = :kanji OR kanji_b = :kanji ORDER BY kanji_a ASC, kanji_b ASC, source ASC")
    suspend fun listForKanji(kanji: String): List<SimilarKanjiPairEntity>

    @Upsert
    suspend fun upsertAll(pairs: List<SimilarKanjiPairEntity>)

    @Query("DELETE FROM similar_kanji_pairs")
    suspend fun deleteAll()
}
