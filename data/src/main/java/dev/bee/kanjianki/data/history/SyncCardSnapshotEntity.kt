package dev.bee.kanjianki.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_card_snapshots",
    indices = [
        Index(value = ["sync_id", "card_id"], unique = true, name = "idx_sync_card_snapshots_sync_card"),
        Index(value = ["sync_id", "note_id"], name = "idx_sync_card_snapshots_note"),
    ],
)
data class SyncCardSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "sync_id")
    val syncId: Long,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long,
    @ColumnInfo(name = "card_id")
    val cardId: Long,
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "deck_id", defaultValue = "''")
    val deckId: String,
    @ColumnInfo(name = "deck_name")
    val deckName: String,
    @ColumnInfo(name = "model_id", defaultValue = "0")
    val modelId: Long,
    @ColumnInfo(name = "model_name")
    val modelName: String,
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
    @ColumnInfo(name = "suspended")
    val suspended: Int,
    @ColumnInfo(name = "fsrs_stability")
    val fsrsStability: Double?,
    @ColumnInfo(name = "fsrs_difficulty")
    val fsrsDifficulty: Double?,
    @ColumnInfo(name = "fsrs_retrievability")
    val fsrsRetrievability: Double?,
    @ColumnInfo(name = "mature")
    val mature: Int,
)
