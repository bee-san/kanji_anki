package dev.bee.kanjianki.data.source

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceCardDao {
    @Query("SELECT * FROM source_cards WHERE card_id = :cardId")
    fun observe(cardId: Long): Flow<SourceCardEntity?>

    @Query("SELECT * FROM source_cards WHERE card_id = :cardId")
    suspend fun get(cardId: Long): SourceCardEntity?

    @Query("SELECT * FROM source_cards WHERE note_id = :noteId ORDER BY ord, card_id")
    suspend fun listForNote(noteId: Long): List<SourceCardEntity>

    @Query("SELECT * FROM source_cards WHERE last_seen_sync_id = :syncId ORDER BY card_id")
    suspend fun listForSync(syncId: Long): List<SourceCardEntity>

    @Upsert
    suspend fun upsert(card: SourceCardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<SourceCardEntity>)

    @Query("DELETE FROM source_cards")
    suspend fun deleteAll()
}
