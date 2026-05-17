package dev.bee.kanjianki.data.importing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suspended_archive")
data class SuspendedArchiveEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: Long,
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "deck_name")
    val deckName: String,
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
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long,
    @ColumnInfo(name = "archived_sync_id")
    val archivedSyncId: Long,
    @ColumnInfo(name = "restored_at")
    val restoredAt: Long?,
)
