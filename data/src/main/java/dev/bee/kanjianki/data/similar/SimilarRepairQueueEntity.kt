package dev.bee.kanjianki.data.similar

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "similar_kanji_repair_queue",
    indices = [
        Index(value = ["status", "due_at", "created_at"], name = "idx_similar_repair_due"),
    ],
)
data class SimilarRepairQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "target_kanji")
    val targetKanji: String,
    @ColumnInfo(name = "repair_kanji")
    val repairKanji: String,
    @ColumnInfo(name = "choice_signature")
    val choiceSignature: String,
    @ColumnInfo(name = "wrong_selection")
    val wrongSelection: String,
    @ColumnInfo(name = "prompt_meaning")
    val promptMeaning: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "active_token", defaultValue = "''")
    val activeToken: String,
    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "completed_at", defaultValue = "0")
    val completedAt: Long,
)
