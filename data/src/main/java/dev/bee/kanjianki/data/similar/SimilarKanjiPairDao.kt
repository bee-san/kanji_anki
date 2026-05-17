package dev.bee.kanjianki.data.similar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SimilarKanjiPairDao {
    @Query("SELECT * FROM similar_kanji_pairs WHERE kanji_a = :kanji OR kanji_b = :kanji ORDER BY kanji_a ASC, kanji_b ASC, source ASC")
    suspend fun listForKanji(kanji: String): List<SimilarKanjiPairEntity>

    @Query(
        """
        SELECT kanji_a FROM similar_kanji_pairs WHERE kanji_a != ''
        UNION
        SELECT kanji_b FROM similar_kanji_pairs WHERE kanji_b != ''
        """,
    )
    suspend fun kanjiWithSimilarNeighbors(): List<String>

    @Upsert
    suspend fun upsertAll(pairs: List<SimilarKanjiPairEntity>)

    @Query("DELETE FROM similar_kanji_pairs")
    suspend fun deleteAll()
}
