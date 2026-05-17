package dev.bee.kanjianki.data.study

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_task_log",
    indices = [
        Index(value = ["task_key"], unique = true, name = "index_study_task_log_task_key"),
        Index(value = ["answered_at"], name = "idx_study_task_log_answered"),
    ],
)
data class StudyTaskLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "task_key")
    val taskKey: String,
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "task_type")
    val taskType: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "answered_at")
    val answeredAt: Long,
    @ColumnInfo(name = "active_elapsed_ms")
    val activeElapsedMs: Long,
    @ColumnInfo(name = "outcome")
    val outcome: String,
)
