package dev.bee.kanjianki.data.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface KanjiTimelineEventDao {
    @Query("SELECT * FROM kanji_timeline_events WHERE kanji = :kanji ORDER BY occurred_at DESC, id DESC")
    suspend fun listForKanji(kanji: String): List<KanjiTimelineEventEntity>

    @Query("SELECT * FROM kanji_timeline_events WHERE kanji = :kanji ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    suspend fun listLatestForKanji(kanji: String, limit: Int): List<KanjiTimelineEventEntity>

    @Upsert
    suspend fun upsert(event: KanjiTimelineEventEntity)
}
