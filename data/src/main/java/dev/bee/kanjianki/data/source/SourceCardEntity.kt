package dev.bee.kanjianki.data.source

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_cards")
data class SourceCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: Long,
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "deck_name")
    val deckName: String,
    @ColumnInfo(name = "ord")
    val ord: Int,
    @ColumnInfo(name = "queue")
    val queue: Int,
    @ColumnInfo(name = "type")
    val type: Int,
    @ColumnInfo(name = "due")
    val due: Int,
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int,
    @ColumnInfo(name = "reps")
    val reps: Int,
    @ColumnInfo(name = "lapses")
    val lapses: Int,
    @ColumnInfo(name = "fsrs_stability")
    val fsrsStability: Double?,
    @ColumnInfo(name = "fsrs_difficulty")
    val fsrsDifficulty: Double?,
    @ColumnInfo(name = "fsrs_retrievability")
    val fsrsRetrievability: Double?,
    @ColumnInfo(name = "last_seen_sync_id")
    val lastSeenSyncId: Long,
)
