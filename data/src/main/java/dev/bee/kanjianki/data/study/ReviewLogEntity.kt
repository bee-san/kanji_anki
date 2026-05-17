package dev.bee.kanjianki.data.study

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_log",
    indices = [
        Index(value = ["token"], unique = true, name = "index_review_log_token"),
        Index(value = ["reviewed_at"], name = "idx_review_log_reviewed_at"),
        Index(value = ["review_day_start", "reviewed_at"], name = "idx_review_log_day_reviewed"),
        Index(value = ["kanji", "reviewed_at"], name = "idx_review_log_kanji_reviewed"),
        Index(value = ["rating", "reviewed_at"], name = "idx_review_log_rating_reviewed"),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "token")
    val token: String,
    @ColumnInfo(name = "rating")
    val rating: String,
    @ColumnInfo(name = "writing_required")
    val writingRequired: Int,
    @ColumnInfo(name = "writing_passed")
    val writingPassed: Int,
    @ColumnInfo(name = "manual_override")
    val manualOverride: Int,
    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long,
    @ColumnInfo(name = "review_day_start", defaultValue = "0")
    val reviewDayStart: Long,
    @ColumnInfo(name = "task_type", defaultValue = "''")
    val taskType: String,
    @ColumnInfo(name = "answer_signature", defaultValue = "''")
    val answerSignature: String,
    @ColumnInfo(name = "prompt", defaultValue = "''")
    val prompt: String,
    @ColumnInfo(name = "hints_used", defaultValue = "0")
    val hintsUsed: Int,
    @ColumnInfo(name = "writing_clean", defaultValue = "0")
    val writingClean: Int,
    @ColumnInfo(name = "memory_before", defaultValue = "''")
    val memoryBefore: String,
    @ColumnInfo(name = "memory_after", defaultValue = "''")
    val memoryAfter: String,
    @ColumnInfo(name = "scheduler_state_after_json", defaultValue = "''")
    val schedulerStateAfterJson: String,
)
