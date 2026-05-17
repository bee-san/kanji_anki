package dev.bee.kanjianki.data.study

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "learning_repeats",
    primaryKeys = ["kanji", "answer_signature", "task_type"],
    indices = [
        Index(value = ["due_at"], name = "idx_learning_repeats_due"),
    ],
)
data class LearningRepeatEntity(
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "answer_signature", defaultValue = "''")
    val answerSignature: String,
    @ColumnInfo(name = "task_type")
    val taskType: String,
    @ColumnInfo(name = "repeat_type")
    val repeatType: String,
    @ColumnInfo(name = "step_index")
    val stepIndex: Int,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "active_token", defaultValue = "''")
    val activeToken: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
