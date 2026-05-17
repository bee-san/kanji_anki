package dev.bee.kanjianki.data.source

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceNoteDao {
    @Query("SELECT * FROM source_notes WHERE note_id = :noteId")
    fun observe(noteId: Long): Flow<SourceNoteEntity?>

    @Query("SELECT * FROM source_notes WHERE note_id = :noteId")
    suspend fun get(noteId: Long): SourceNoteEntity?

    @Query("SELECT * FROM source_notes WHERE last_seen_sync_id = :syncId ORDER BY note_id")
    suspend fun listForSync(syncId: Long): List<SourceNoteEntity>

    @Query("SELECT note_id FROM source_notes")
    suspend fun listIds(): List<Long>

    @Upsert
    suspend fun upsert(note: SourceNoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<SourceNoteEntity>)

    @Query("DELETE FROM source_notes")
    suspend fun deleteAll()
}
