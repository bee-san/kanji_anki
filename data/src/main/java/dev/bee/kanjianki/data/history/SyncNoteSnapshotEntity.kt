package dev.bee.kanjianki.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_note_snapshots",
    primaryKeys = ["sync_id", "note_id"],
    indices = [
        Index(value = ["sync_id", "extracted_kanji"], name = "idx_sync_note_snapshots_kanji"),
    ],
)
data class SyncNoteSnapshotEntity(
    @ColumnInfo(name = "sync_id")
    val syncId: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long,
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "model_id", defaultValue = "0")
    val modelId: Long,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "deck_ids", defaultValue = "''")
    val deckIds: String,
    @ColumnInfo(name = "deck_names")
    val deckNames: String,
    @ColumnInfo(name = "expression")
    val expression: String,
    @ColumnInfo(name = "reading")
    val reading: String,
    @ColumnInfo(name = "meaning")
    val meaning: String,
    @ColumnInfo(name = "sentence")
    val sentence: String,
    @ColumnInfo(name = "tags")
    val tags: String,
    @ColumnInfo(name = "fields_json")
    val fieldsJson: String,
    @ColumnInfo(name = "extracted_kanji")
    val extractedKanji: String,
)
