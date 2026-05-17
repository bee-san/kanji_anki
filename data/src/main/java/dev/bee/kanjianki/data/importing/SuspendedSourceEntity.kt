package dev.bee.kanjianki.data.importing

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "suspended_sources",
    primaryKeys = ["kanji", "card_id"],
)
data class SuspendedSourceEntity(
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "card_id")
    val cardId: Long,
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "expression")
    val expression: String,
    @ColumnInfo(name = "reading")
    val reading: String,
    @ColumnInfo(name = "meaning")
    val meaning: String,
    @ColumnInfo(name = "sentence")
    val sentence: String,
    @ColumnInfo(name = "sync_id")
    val syncId: Long,
)
