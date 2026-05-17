package dev.bee.kanjianki.data.study

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "study_items",
    primaryKeys = ["kanji", "answer_signature"],
    indices = [
        Index(value = ["state", "due_at"], name = "idx_study_due"),
        Index(value = ["state", "phase", "rung"], name = "idx_study_items_ladder_stats"),
    ],
)
data class StudyItemEntity(
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "stability")
    val stability: Double,
    @ColumnInfo(name = "difficulty")
    val difficulty: Double,
    @ColumnInfo(name = "total_reviews")
    val totalReviews: Int,
    @ColumnInfo(name = "lapses")
    val lapses: Int,
    @ColumnInfo(name = "learning_step")
    val learningStep: Int,
    @ColumnInfo(name = "writing_level")
    val writingLevel: Int,
    @ColumnInfo(name = "recognition_stage", defaultValue = "0")
    val recognitionStage: Int,
    @ColumnInfo(name = "consecutive_failed_recognition_days", defaultValue = "0")
    val consecutiveFailedRecognitionDays: Int,
    @ColumnInfo(name = "last_failed_recognition_day", defaultValue = "0")
    val lastFailedRecognitionDay: Long,
    @ColumnInfo(name = "writing_remediation_pending", defaultValue = "0")
    val writingRemediationPending: Int,
    @ColumnInfo(name = "suppressed_by_task_type", defaultValue = "''")
    val suppressedByTaskType: String,
    @ColumnInfo(name = "suppressed_at", defaultValue = "0")
    val suppressedAt: Long,
    @ColumnInfo(name = "mature_interval_days", defaultValue = "0")
    val matureIntervalDays: Int,
    @ColumnInfo(name = "answer_signature", defaultValue = "''")
    val answerSignature: String,
    @ColumnInfo(name = "typing_meaning_memory", defaultValue = "''")
    val typingMeaningMemory: String,
    @ColumnInfo(name = "meaning_kanji_memory", defaultValue = "''")
    val meaningKanjiMemory: String,
    @ColumnInfo(name = "kanji_meaning_memory", defaultValue = "''")
    val kanjiMeaningMemory: String,
    @ColumnInfo(name = "font_meaning_memory", defaultValue = "''")
    val fontMeaningMemory: String,
    @ColumnInfo(name = "word_reading_memory", defaultValue = "''")
    val wordReadingMemory: String,
    @ColumnInfo(name = "writing_remediation_memory", defaultValue = "''")
    val writingRemediationMemory: String,
    @ColumnInfo(name = "rung", defaultValue = "'kanji_meaning'")
    val rung: String,
    @ColumnInfo(name = "phase", defaultValue = "'new_learning'")
    val phase: String,
    @ColumnInfo(name = "real_pass_streak", defaultValue = "0")
    val realPassStreak: Int,
    @ColumnInfo(name = "real_again_streak", defaultValue = "0")
    val realAgainStreak: Int,
    @ColumnInfo(name = "last_real_review_due_at", defaultValue = "0")
    val lastRealReviewDueAt: Long,
    @ColumnInfo(name = "similar_kanji_memory", defaultValue = "''")
    val similarKanjiMemory: String,
    @ColumnInfo(name = "active_token")
    val activeToken: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
