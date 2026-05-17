package dev.bee.kanjianki.data.source

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_notes")
data class SourceNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "expression")
    val expression: String,
    @ColumnInfo(name = "reading")
    val reading: String,
    @ColumnInfo(name = "meaning")
    val meaning: String,
    @ColumnInfo(name = "sentence")
    val sentence: String,
    @ColumnInfo(name = "fields_json")
    val fieldsJson: String,
    @ColumnInfo(name = "tags")
    val tags: String,
    @ColumnInfo(name = "last_seen_sync_id")
    val lastSeenSyncId: Long,
)
